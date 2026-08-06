package cn.iocoder.yudao.module.drill.service.enhance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.drill.config.ImageEnhanceProperties;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.ImageVersionVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.*;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.*;
import cn.iocoder.yudao.module.drill.service.image.ImageAssetService;
import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.*;
import cn.iocoder.yudao.module.drill.service.enhance.pipeline.ManualRequiredException;
import cn.iocoder.yudao.module.drill.service.enhance.pipeline.OpenCvEnhancePipeline;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.*;

@Component
@Slf4j
public class EnhanceTaskWorker {
    @Resource private EnhanceItemMapper itemMapper;
    @Resource private EnhanceJobMapper enhanceJobMapper;
    @Resource private EnhanceProfileMapper profileMapper;
    @Resource private EnhanceStepLogMapper stepLogMapper;
    @Resource private ImageVersionMapper versionMapper;
    @Resource private ImageAssetService imageAssetService;
    @Resource private FileService fileService;
    @Resource private OpenCvEnhancePipeline pipeline;
    @Resource private EnhanceJobService jobService;
    @Resource private ImageVersionService versionService;
    @Resource private ImageEnhanceProperties properties;
    @Resource private TransactionTemplate transactionTemplate;
    @Resource @Qualifier("imageEnhanceExecutor") private Executor executor;
    private final String workerId = UUID.randomUUID().toString();

    @PostConstruct
    public void recoverExpiredAtStartup() {
        TenantUtils.executeIgnore(this::recoverExpired);
    }

    @Scheduled(fixedDelayString = "${drill.image-enhance.poll-interval:2s}")
    public void dispatch() {
        TenantUtils.executeIgnore(() -> {
            recoverExpired();
            int limit = Math.max(1, properties.getExecutor().getCoreSize());
            for (EnhanceItemDO candidate : itemMapper.selectPendingCandidates(limit)) {
                if (!itemMapper.claim(candidate.getId(), workerId,
                        LocalDateTime.now().plus(properties.getItemTimeout()))) continue;
                Long tenantId = candidate.getTenantId();
                try {
                    executor.execute(() -> TenantUtils.execute(tenantId, () -> process(candidate.getId())));
                } catch (RuntimeException ex) {
                    resetPending(candidate.getId());
                    log.warn("[dispatch][itemId({}) 线程池繁忙]", candidate.getId(), ex);
                }
            }
        });
    }

    protected void recoverExpired() {
        for (EnhanceItemDO item : itemMapper.selectExpired(LocalDateTime.now())) {
            EnhanceItemDO update = new EnhanceItemDO();
            update.setId(item.getId()); update.setWorkerId(null); update.setLeaseUntil(null);
            if (item.getRetryCount() < properties.getMaxRetries()) {
                update.setStatus(ITEM_PENDING); update.setRetryCount(item.getRetryCount() + 1);
                update.setErrorCode("TASK_LEASE_EXPIRED"); update.setErrorMessage("处理租约过期，已重新排队");
            } else {
                update.setStatus(ITEM_FAILED); update.setFinishedAt(LocalDateTime.now());
                update.setErrorCode("TASK_LEASE_EXPIRED"); update.setErrorMessage("处理租约多次过期");
            }
            itemMapper.updateById(update);
        }
    }

    private void process(Long itemId) {
        EnhanceItemDO item = itemMapper.selectById(itemId);
        if (item == null || !ITEM_PROCESSING.equals(item.getStatus())) return;
        long started = System.currentTimeMillis();
        try {
            EnhanceJobDO job = enhanceJobMapper.selectById(item.getJobId());
            EnhanceProfileDO profile = profileMapper.selectById(job.getProfileId());
            ImageAssetDO asset = imageAssetService.getAsset(item.getAssetId());
            byte[] input = imageAssetService.getAssetContent(asset.getId());
            List<StepConfig> steps = JsonUtils.parseArray(profile.getPipelineJson(), StepConfig.class);
            OutputSpec output = JsonUtils.parseObject(profile.getOutputSpecJson(), OutputSpec.class);
            ManualOverrides manual = JsonUtils.parseObject(item.getManualOverridesJson(), ManualOverrides.class);
            PipelineResult result = pipeline.execute(input, steps, output, manual,
                    new StepLogger(itemId, steps.size()));
            Long versionId = persistResult(item, job, profile, asset, steps, result);
            if (Boolean.TRUE.equals(job.getAutoPublish())) {
                ImageVersionVO.PublishReq publish = new ImageVersionVO.PublishReq();
                publish.setPurposes(List.of("INFERENCE")); publish.setSetAsDefault(true);
                versionService.publish(versionId, publish);
            }
        } catch (ManualRequiredException ex) {
            finishFailure(item, ITEM_MANUAL_REQUIRED, ex.getErrorCode(), ex.getMessage(), started);
        } catch (Exception ex) {
            log.error("[process][itemId({}) 图像增强失败]", itemId, ex);
            finishFailure(item, ITEM_FAILED, classify(ex), safeMessage(ex), started);
        } finally {
            jobService.refreshJob(item.getJobId());
        }
    }

    private Long persistResult(EnhanceItemDO item, EnhanceJobDO job, EnhanceProfileDO profile, ImageAssetDO original,
                               List<StepConfig> steps, PipelineResult result) {
        return transactionTemplate.execute(status -> {
            ImageAssetDO asset = imageAssetService.getAssetForUpdate(original.getId());
            int versionNo = versionMapper.selectNextVersionNo(asset.getId());
            String directory = "drill-images/tenant-" + asset.getTenantId() + "/asset-" + asset.getId()
                    + "/versions/v" + versionNo;
            Long maskFileId = null;
            Long fileId = null;
            try {
                String name = "enhanced." + result.getExtension();
                String url = fileService.createFile(result.getContent(), name, directory, result.getMimeType());
                FileDO file = fileService.getFileByUrl(url);
                if (file == null) throw new IllegalStateException("STORAGE_WRITE_FAILED: file record missing");
                fileId = file.getId();
                if (result.getMaskContent() != null) {
                    String maskUrl = fileService.createFile(result.getMaskContent(), "mask.png", directory, "image/png");
                    FileDO maskFile = fileService.getFileByUrl(maskUrl);
                    if (maskFile != null) maskFileId = maskFile.getId();
                }
                ImageVersionDO version = new ImageVersionDO();
                version.setAssetId(asset.getId()); version.setVersionNo(versionNo); version.setVersionType("ENHANCED");
                version.setEnhanceItemId(item.getId()); version.setFileId(file.getId());
                version.setFileConfigId(file.getConfigId()); version.setObjectKey(file.getPath());
                version.setSha256(result.getSha256()); version.setMimeType(result.getMimeType());
                version.setFileSize((long) result.getContent().length); version.setWidth(result.getWidth());
                version.setHeight(result.getHeight()); version.setProfileCode(profile.getProfileCode());
                version.setProfileVersion(profile.getVersion());
                version.setAlgorithmVersionsJson(JsonUtils.toJsonString(steps.stream().filter(StepConfig::getEnabled)
                        .collect(java.util.stream.Collectors.toMap(StepConfig::getCode, StepConfig::getAlgorithmVersion,
                                (a, b) -> a, LinkedHashMap::new))));
                version.setParametersJson(profile.getPipelineJson());
                version.setMetricsJson(JsonUtils.toJsonString(result.getMetrics()));
                version.setTransformMatrixJson(JsonUtils.toJsonString(result.getTransformMatrix()));
                version.setMaskFileId(maskFileId);
                boolean autoPass = "AUTO".equals(profile.getReviewPolicy())
                        || ("RISK".equals(profile.getReviewPolicy()) && result.getWarnings().isEmpty());
                version.setReviewStatus(autoPass ? REVIEW_AUTO_PASSED : REVIEW_PENDING);
                version.setPublishStatus(PUBLISH_UNPUBLISHED); version.setDefaultVersion(false);
                versionMapper.insert(version);
                EnhanceItemDO done = new EnhanceItemDO();
                done.setId(item.getId()); done.setStatus(autoPass ? ITEM_REVIEW_PASSED : ITEM_PENDING_REVIEW);
                done.setProgress(100); done.setFinishedAt(LocalDateTime.now());
                done.setDurationMs(java.time.Duration.between(item.getStartedAt(), LocalDateTime.now()).toMillis());
                done.setWorkerId(null); done.setLeaseUntil(null); itemMapper.updateById(done);
                return version.getId();
            } catch (Exception ex) {
                if (fileId != null) try { fileService.deleteFile(fileId); } catch (Exception cleanup) {
                    log.warn("[persistResult][fileId({}) 补偿删除失败]", fileId, cleanup);
                }
                throw new IllegalStateException("STORAGE_WRITE_FAILED", ex);
            }
        });
    }

    private void finishFailure(EnhanceItemDO item, String status, String code, String message, long started) {
        EnhanceItemDO update = new EnhanceItemDO();
        update.setId(item.getId()); update.setStatus(status); update.setErrorCode(code);
        update.setErrorMessage(message); update.setFinishedAt(LocalDateTime.now());
        update.setDurationMs(System.currentTimeMillis() - started); update.setWorkerId(null); update.setLeaseUntil(null);
        itemMapper.updateById(update);
    }

    private void resetPending(Long itemId) {
        EnhanceItemDO update = new EnhanceItemDO();
        update.setId(itemId); update.setStatus(ITEM_PENDING); update.setWorkerId(null); update.setLeaseUntil(null);
        itemMapper.updateById(update);
    }

    private String classify(Exception ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("STORAGE_WRITE_FAILED")) return "STORAGE_WRITE_FAILED";
        if (message != null && message.contains("INVALID_MANUAL_OVERRIDE")) return "INVALID_MANUAL_OVERRIDE";
        return "OUTPUT_QUALITY_REJECTED";
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message.substring(0, Math.min(500, message.length()));
    }

    private class StepLogger implements OpenCvEnhancePipeline.StepListener {
        private final Long itemId;
        private final int total;
        private int completed;
        StepLogger(Long itemId, int total) { this.itemId = itemId; this.total = Math.max(1, total); }

        @Override
        public void completed(StepConfig step, long durationMs, Map<String, Object> metrics) {
            save(step, durationMs, metrics, "SUCCESS", null, null);
            updateProgress(step);
        }
        @Override
        public void failed(StepConfig step, long durationMs, RuntimeException exception) {
            String code = exception instanceof ManualRequiredException manual ? manual.getErrorCode() : classify(exception);
            save(step, durationMs, Map.of(), "FAILED", code, safeMessage(exception));
        }
        private void save(StepConfig step, long duration, Map<String, Object> metrics,
                          String status, String code, String message) {
            EnhanceStepLogDO logDO = new EnhanceStepLogDO();
            logDO.setItemId(itemId); logDO.setStepCode(step.getCode()); logDO.setStepOrder(step.getStepOrder());
            logDO.setAlgorithmName(step.getAlgorithm()); logDO.setAlgorithmVersion(step.getAlgorithmVersion());
            logDO.setParametersJson(JsonUtils.toJsonString(step.getParameters()));
            logDO.setMetricsJson(JsonUtils.toJsonString(metrics)); logDO.setStatus(status);
            logDO.setErrorCode(code); logDO.setErrorMessage(message); logDO.setDurationMs(duration);
            stepLogMapper.insert(logDO);
        }
        private void updateProgress(StepConfig step) {
            completed++;
            EnhanceItemDO update = new EnhanceItemDO();
            update.setId(itemId); update.setCurrentStep(step.getCode());
            update.setProgress(Math.min(95, completed * 90 / total));
            update.setHeartbeatTime(LocalDateTime.now());
            update.setLeaseUntil(LocalDateTime.now().plus(properties.getItemTimeout()));
            itemMapper.updateById(update);
        }
    }
}
