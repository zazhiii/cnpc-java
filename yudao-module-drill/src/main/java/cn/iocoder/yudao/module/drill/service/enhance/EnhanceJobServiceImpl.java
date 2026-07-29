package cn.iocoder.yudao.module.drill.service.enhance;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceJobPageReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceJobVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.*;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.*;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageAssetMapper;
import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.StepConfig;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.*;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;

@Service
public class EnhanceJobServiceImpl implements EnhanceJobService {
    @Resource private EnhanceJobMapper enhanceJobMapper;
    @Resource private EnhanceItemMapper itemMapper;
    @Resource private EnhanceStepLogMapper stepLogMapper;
    @Resource private EnhanceProfileService profileService;
    @Resource private ImageAssetMapper assetMapper;

    @Override
    public Long create(EnhanceJobVO.CreateReq req, String idempotencyKey) {
        EnhanceProfileDO profile = profileService.get(req.getProfileId());
        if (!PROFILE_ENABLED.equals(profile.getStatus())) throw exception(ENHANCE_PROFILE_STATE_INVALID);
        if (Boolean.TRUE.equals(req.getAutoPublish()) && !"AUTO".equals(profile.getReviewPolicy())) {
            throw exception(ENHANCE_PROFILE_INVALID, "自动发布仅适用于AUTO审核方案");
        }
        List<Long> assetIds = resolveAssetIds(req);
        return createInternal(profile, assetIds, req.getBatchId(), req.getPriority(), req.getAutoReview(),
                req.getAutoPublish(), false, req.getRemark(), idempotencyKey);
    }

    @Override
    public Long createValidationJob(EnhanceProfileDO profile, List<Long> assetIds, String idempotencyKey) {
        validateAssets(assetIds);
        return createInternal(profile, assetIds, null, 8, false, false, true,
                "增强方案验证试运行", idempotencyKey);
    }

    @Transactional(rollbackFor = Exception.class)
    protected Long createInternal(EnhanceProfileDO profile, List<Long> assetIds, Long batchId, Integer priority,
                                  Boolean autoReview, Boolean autoPublish, boolean validation, String remark,
                                  String idempotencyKey) {
        String requestHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(Map.of(
                "profileId", profile.getId(), "assetIds", assetIds, "validation", validation)));
        if (idempotencyKey != null) {
            EnhanceJobDO existing = enhanceJobMapper.selectByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                if (!requestHash.equals(existing.getRequestHash())) throw exception(ENHANCE_IDEMPOTENCY_CONFLICT);
                return existing.getId();
            }
        }
        EnhanceJobDO job = new EnhanceJobDO();
        job.setJobNo("ENH-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + RandomUtil.randomStringUpper(5));
        job.setBatchId(batchId); job.setProfileId(profile.getId()); job.setProfileVersion(profile.getVersion());
        job.setStatus(JOB_PENDING); job.setPriority(priority == null ? 5 : priority);
        job.setTotalCount(assetIds.size()); job.setSuccessCount(0); job.setFailedCount(0); job.setManualCount(0);
        job.setIdempotencyKey(idempotencyKey); job.setRequestHash(requestHash); job.setSubmittedBy(getLoginUserId());
        job.setParameterSnapshotJson(JsonUtils.toJsonString(profile));
        job.setValidationJob(validation); job.setAutoReview(Boolean.TRUE.equals(autoReview));
        job.setAutoPublish(Boolean.TRUE.equals(autoPublish)); job.setRemark(remark);
        enhanceJobMapper.insert(job);
        List<EnhanceItemDO> items = assetIds.stream().map(assetId -> {
            EnhanceItemDO item = new EnhanceItemDO();
            item.setJobId(job.getId()); item.setAssetId(assetId); item.setStatus(ITEM_PENDING);
            item.setProgress(0); item.setRetryCount(0); item.setLockVersion(0);
            return item;
        }).toList();
        itemMapper.insertBatch(items);
        return job.getId();
    }

    @Override public PageResult<EnhanceJobDO> getPage(EnhanceJobPageReqVO req) {
        return enhanceJobMapper.selectPage(req);
    }

    @Override
    public EnhanceJobDO get(Long id) {
        EnhanceJobDO job = enhanceJobMapper.selectById(id);
        if (job == null) throw exception(ENHANCE_JOB_NOT_EXISTS);
        return job;
    }

    @Override public List<EnhanceItemDO> getItems(Long jobId) { get(jobId); return itemMapper.selectByJobId(jobId); }

    @Override
    public List<EnhanceStepLogDO> getSteps(Long itemId) {
        if (itemMapper.selectById(itemId) == null) throw exception(ENHANCE_ITEM_NOT_EXISTS);
        return stepLogMapper.selectByItemId(itemId);
    }

    @Override
    public EnhanceJobVO.StatisticsResp getStatistics() {
        List<EnhanceJobDO> jobs = enhanceJobMapper.selectList();
        EnhanceJobVO.StatisticsResp statistics = new EnhanceJobVO.StatisticsResp();
        statistics.setPendingCount(jobs.stream().filter(job -> JOB_PENDING.equals(job.getStatus())).count());
        statistics.setProcessingCount(jobs.stream().filter(job -> JOB_PROCESSING.equals(job.getStatus())).count());
        statistics.setManualCount(jobs.stream()
                .map(EnhanceJobDO::getManualCount)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum());

        LocalDate today = LocalDate.now();
        List<EnhanceJobDO> todayJobs = jobs.stream()
                .filter(job -> job.getCreateTime() != null && today.equals(job.getCreateTime().toLocalDate()))
                .toList();
        long success = todayJobs.stream().map(EnhanceJobDO::getSuccessCount).filter(Objects::nonNull)
                .mapToLong(Integer::longValue).sum();
        long failed = todayJobs.stream().map(EnhanceJobDO::getFailedCount).filter(Objects::nonNull)
                .mapToLong(Integer::longValue).sum();
        statistics.setTodaySuccessRate(success + failed == 0 ? 0D
                : Math.round(success * 1000D / (success + failed)) / 10D);

        long completedCount = jobs.stream()
                .filter(job -> job.getStartedAt() != null && job.getFinishedAt() != null)
                .count();
        long totalDuration = jobs.stream()
                .filter(job -> job.getStartedAt() != null && job.getFinishedAt() != null)
                .mapToLong(job -> Duration.between(job.getStartedAt(), job.getFinishedAt()).toMillis())
                .sum();
        statistics.setAverageDurationMs(completedCount == 0 ? 0L : totalDuration / completedCount);
        return statistics;
    }

    @Override
    public void retryItem(Long id, EnhanceJobVO.RetryReq req) {
        EnhanceItemDO item = itemMapper.selectById(id);
        if (item == null) throw exception(ENHANCE_ITEM_NOT_EXISTS);
        if (!Set.of(ITEM_FAILED, ITEM_MANUAL_REQUIRED).contains(item.getStatus())) {
            throw exception(ENHANCE_JOB_STATE_INVALID);
        }
        EnhanceItemDO update = new EnhanceItemDO();
        update.setId(id); update.setStatus(ITEM_PENDING); update.setProgress(0);
        update.setRetryCount(item.getRetryCount() + 1);
        update.setManualOverridesJson(req == null || req.getManualOverrides() == null ? null
                : JsonUtils.toJsonString(req.getManualOverrides()));
        update.setErrorCode(null); update.setErrorMessage(null); update.setWorkerId(null); update.setLeaseUntil(null);
        itemMapper.updateById(update);
        reopenJob(item.getJobId());
    }

    @Override
    public void retryFailed(Long jobId) {
        get(jobId);
        itemMapper.update(new EnhanceItemDO(), new LambdaUpdateWrapper<EnhanceItemDO>()
                .set(EnhanceItemDO::getStatus, ITEM_PENDING).set(EnhanceItemDO::getProgress, 0)
                .setSql("retry_count = retry_count + 1")
                .eq(EnhanceItemDO::getJobId, jobId)
                .in(EnhanceItemDO::getStatus, ITEM_FAILED, ITEM_MANUAL_REQUIRED));
        reopenJob(jobId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long jobId) {
        EnhanceJobDO job = get(jobId);
        if (Set.of(JOB_COMPLETED, JOB_FAILED, JOB_CANCELLED).contains(job.getStatus())) {
            throw exception(ENHANCE_JOB_STATE_INVALID);
        }
        itemMapper.update(new EnhanceItemDO(), new LambdaUpdateWrapper<EnhanceItemDO>()
                .set(EnhanceItemDO::getStatus, ITEM_CANCELLED)
                .set(EnhanceItemDO::getFinishedAt, LocalDateTime.now())
                .eq(EnhanceItemDO::getJobId, jobId).eq(EnhanceItemDO::getStatus, ITEM_PENDING));
        refreshJob(jobId);
    }

    @Override
    public void refreshJob(Long jobId) {
        EnhanceJobDO job = get(jobId);
        List<EnhanceItemDO> items = itemMapper.selectByJobId(jobId);
        int success = (int) items.stream().filter(i -> Set.of(ITEM_PENDING_REVIEW, ITEM_REVIEW_PASSED).contains(i.getStatus())).count();
        int failed = (int) items.stream().filter(i -> ITEM_FAILED.equals(i.getStatus())).count();
        int manual = (int) items.stream().filter(i -> ITEM_MANUAL_REQUIRED.equals(i.getStatus())).count();
        boolean active = items.stream().anyMatch(i -> Set.of(ITEM_PENDING, ITEM_PROCESSING).contains(i.getStatus()));
        String status;
        if (active) status = items.stream().anyMatch(i -> ITEM_PROCESSING.equals(i.getStatus())) ? JOB_PROCESSING : JOB_PENDING;
        else if (success == items.size()) status = JOB_COMPLETED;
        else if (success > 0) status = JOB_PARTIAL_SUCCESS;
        else if (items.stream().allMatch(i -> ITEM_CANCELLED.equals(i.getStatus()))) status = JOB_CANCELLED;
        else status = JOB_FAILED;
        EnhanceJobDO update = new EnhanceJobDO();
        update.setId(jobId); update.setStatus(status); update.setSuccessCount(success);
        update.setFailedCount(failed); update.setManualCount(manual);
        if (!active) update.setFinishedAt(LocalDateTime.now());
        if (JOB_PROCESSING.equals(status) && job.getStartedAt() == null) update.setStartedAt(LocalDateTime.now());
        enhanceJobMapper.updateById(update);
    }

    private List<Long> resolveAssetIds(EnhanceJobVO.CreateReq req) {
        if (CollUtil.isNotEmpty(req.getAssetIds()) == (req.getBatchId() != null)) {
            throw exception(ENHANCE_PROFILE_INVALID, "assetIds和batchId必须且只能提供一个");
        }
        List<Long> ids = req.getAssetIds();
        if (ids == null) ids = assetMapper.selectList(ImageAssetDO::getBatchId, req.getBatchId())
                .stream().map(ImageAssetDO::getId).toList();
        validateAssets(ids);
        return ids;
    }

    private void validateAssets(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) throw exception(ENHANCE_PROFILE_INVALID, "没有可处理的图片");
        if (assetMapper.selectByIds(ids).size() != new HashSet<>(ids).size()) throw exception(IMAGE_ASSET_NOT_EXISTS);
    }

    private void reopenJob(Long jobId) {
        EnhanceJobDO update = new EnhanceJobDO();
        update.setId(jobId); update.setStatus(JOB_PENDING); update.setFinishedAt(null);
        enhanceJobMapper.updateById(update);
    }
}
