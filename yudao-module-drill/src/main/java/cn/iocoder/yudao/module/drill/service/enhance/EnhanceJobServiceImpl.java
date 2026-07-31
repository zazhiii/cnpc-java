package cn.iocoder.yudao.module.drill.service.enhance;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceJobPageReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceJobVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceItemDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceJobDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceProfileDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceStepLogDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.EnhanceItemMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.EnhanceJobMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.EnhanceStepLogMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageAssetMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.ITEM_CANCELLED;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.ITEM_FAILED;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.ITEM_MANUAL_REQUIRED;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.ITEM_PENDING;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.ITEM_PENDING_REVIEW;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.ITEM_PROCESSING;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.ITEM_REVIEW_PASSED;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.JOB_CANCELLED;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.JOB_COMPLETED;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.JOB_FAILED;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.JOB_PARTIAL_SUCCESS;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.JOB_PENDING;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.JOB_PROCESSING;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.PROFILE_ENABLED;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.ENHANCE_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.ENHANCE_ITEM_NOT_EXISTS;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.ENHANCE_JOB_NOT_EXISTS;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.ENHANCE_JOB_STATE_INVALID;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.ENHANCE_PROFILE_INVALID;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.ENHANCE_PROFILE_STATE_INVALID;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_ASSET_NOT_EXISTS;

/**
 * 图像增强任务 Service 实现。
 *
 * <p>负责增强任务的创建、查询、重试、取消及状态汇总。实际的图片增强处理由任务执行器完成，
 * 本类只维护任务及任务项的生命周期。</p>
 */
@Service
public class EnhanceJobServiceImpl implements EnhanceJobService {

    private static final int DEFAULT_PRIORITY = 5;
    private static final int VALIDATION_JOB_PRIORITY = 8;
    private static final int INITIAL_PROGRESS = 0;
    private static final int INITIAL_RETRY_COUNT = 0;
    private static final int INITIAL_LOCK_VERSION = 0;

    private static final String AUTO_REVIEW_POLICY = "AUTO";
    private static final String JOB_NO_PREFIX = "ENH-";
    private static final String JOB_NO_TIME_PATTERN = "yyyyMMddHHmmss";
    private static final int JOB_NO_RANDOM_LENGTH = 5;
    private static final String VALIDATION_JOB_REMARK = "增强方案验证试运行";

    private static final DateTimeFormatter JOB_NO_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(JOB_NO_TIME_PATTERN);

    /** 失败或需要人工处理的任务项，允许重新进入待处理队列。 */
    private static final Set<String> RETRYABLE_ITEM_STATUSES =
            Set.of(ITEM_FAILED, ITEM_MANUAL_REQUIRED);

    /** 已进入审核环节的任务项视为增强处理成功。 */
    private static final Set<String> SUCCESSFUL_ITEM_STATUSES =
            Set.of(ITEM_PENDING_REVIEW, ITEM_REVIEW_PASSED);

    /** 存在以下状态的任务项时，任务仍处于活跃状态。 */
    private static final Set<String> ACTIVE_ITEM_STATUSES =
            Set.of(ITEM_PENDING, ITEM_PROCESSING);

    /** 终态任务不允许再次取消。 */
    private static final Set<String> TERMINAL_JOB_STATUSES =
            Set.of(JOB_COMPLETED, JOB_FAILED, JOB_CANCELLED);

    @Resource
    private EnhanceJobMapper enhanceJobMapper;
    @Resource
    private EnhanceItemMapper enhanceItemMapper;
    @Resource
    private EnhanceStepLogMapper enhanceStepLogMapper;
    @Resource
    private EnhanceProfileService enhanceProfileService;
    @Resource
    private ImageAssetMapper imageAssetMapper;

    // ==================== 创建任务 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(EnhanceJobVO.CreateReq createReqVO, String idempotencyKey) {
        EnhanceProfileDO profile = enhanceProfileService.get(createReqVO.getProfileId());
        validateProfileForCreate(profile, createReqVO.getAutoPublish());

        List<Long> assetIds = resolveAssetIds(createReqVO);
        return createJob(
                profile,
                assetIds,
                createReqVO.getBatchId(),
                createReqVO.getPriority(),
                createReqVO.getAutoReview(),
                createReqVO.getAutoPublish(),
                false,
                createReqVO.getRemark(),
                idempotencyKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createValidationJob(
            EnhanceProfileDO profile, List<Long> assetIds, String idempotencyKey) {
        validateAssets(assetIds);
        return createJob(
                profile,
                assetIds,
                null,
                VALIDATION_JOB_PRIORITY,
                false,
                false,
                true,
                VALIDATION_JOB_REMARK,
                idempotencyKey);
    }

    /**
     * 创建任务主记录及其任务项。
     *
     * <p>调用方必须处于事务中，确保任务主记录与所有任务项同时成功或同时回滚。</p>
     */
    private Long createJob(
            EnhanceProfileDO profile,
            List<Long> assetIds,
            Long batchId,
            Integer priority,
            Boolean autoReview,
            Boolean autoPublish,
            boolean validationJob,
            String remark,
            String idempotencyKey) {
        String requestHash = buildRequestHash(profile.getId(), assetIds, validationJob);

        Long existingJobId = findIdempotentJob(idempotencyKey, requestHash);
        if (existingJobId != null) {
            return existingJobId;
        }

        EnhanceJobDO job = buildJob(
                profile,
                assetIds.size(),
                batchId,
                priority,
                autoReview,
                autoPublish,
                validationJob,
                remark,
                idempotencyKey,
                requestHash);
        enhanceJobMapper.insert(job);

        // 主记录入库后才能取得 jobId，并建立任务项与任务的关联。
        enhanceItemMapper.insertBatch(buildJobItems(job.getId(), assetIds));
        return job.getId();
    }

    private void validateProfileForCreate(EnhanceProfileDO profile, Boolean autoPublish) {
        if (!PROFILE_ENABLED.equals(profile.getStatus())) {
            throw exception(ENHANCE_PROFILE_STATE_INVALID);
        }
        if (Boolean.TRUE.equals(autoPublish)
                && !AUTO_REVIEW_POLICY.equals(profile.getReviewPolicy())) {
            throw exception(ENHANCE_PROFILE_INVALID, "自动发布仅适用于AUTO审核方案");
        }
    }

    /**
     * 根据请求的图片编号或批次编号解析待处理图片。
     *
     * <p>两种数据来源互斥：调用方必须且只能指定其中一种。</p>
     */
    private List<Long> resolveAssetIds(EnhanceJobVO.CreateReq createReqVO) {
        boolean hasAssetIds = CollUtil.isNotEmpty(createReqVO.getAssetIds());
        boolean hasBatchId = createReqVO.getBatchId() != null;
        if (hasAssetIds == hasBatchId) {
            throw exception(ENHANCE_PROFILE_INVALID, "assetIds和batchId必须且只能提供一个");
        }

        List<Long> assetIds = hasAssetIds
                ? createReqVO.getAssetIds()
                : imageAssetMapper.selectByBatchId(createReqVO.getBatchId()).stream()
                        .map(ImageAssetDO::getId)
                        .toList();
        validateAssets(assetIds);
        return assetIds;
    }

    private void validateAssets(List<Long> assetIds) {
        if (CollUtil.isEmpty(assetIds)) {
            throw exception(ENHANCE_PROFILE_INVALID, "没有可处理的图片");
        }

        // 使用去重后的请求数量比较查询结果，避免重复 ID 干扰存在性校验。
        int distinctAssetCount = new HashSet<>(assetIds).size();
        if (imageAssetMapper.selectByIds(assetIds).size() != distinctAssetCount) {
            throw exception(IMAGE_ASSET_NOT_EXISTS);
        }
    }

    /**
     * 查询同一幂等请求。
     *
     * @return 已创建的任务编号；未提供幂等键或尚未创建时返回 {@code null}
     */
    private Long findIdempotentJob(String idempotencyKey, String requestHash) {
        if (idempotencyKey == null) {
            return null;
        }

        EnhanceJobDO existingJob = enhanceJobMapper.selectByIdempotencyKey(idempotencyKey);
        if (existingJob == null) {
            return null;
        }
        if (!requestHash.equals(existingJob.getRequestHash())) {
            // 相同幂等键不得代表不同请求，防止客户端误复用键值。
            throw exception(ENHANCE_IDEMPOTENCY_CONFLICT);
        }
        return existingJob.getId();
    }

    private String buildRequestHash(Long profileId, List<Long> assetIds, boolean validationJob) {
        Map<String, Object> requestIdentity = Map.of(
                "profileId", profileId,
                "assetIds", assetIds,
                "validation", validationJob);
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(requestIdentity));
    }

    private EnhanceJobDO buildJob(
            EnhanceProfileDO profile,
            int totalCount,
            Long batchId,
            Integer priority,
            Boolean autoReview,
            Boolean autoPublish,
            boolean validationJob,
            String remark,
            String idempotencyKey,
            String requestHash) {
        EnhanceJobDO job = new EnhanceJobDO();
        job.setJobNo(generateJobNo());
        job.setBatchId(batchId);
        job.setProfileId(profile.getId());
        job.setProfileVersion(profile.getVersion());
        job.setStatus(JOB_PENDING);
        job.setPriority(priority == null ? DEFAULT_PRIORITY : priority);
        job.setTotalCount(totalCount);
        job.setSuccessCount(0);
        job.setFailedCount(0);
        job.setManualCount(0);
        job.setIdempotencyKey(idempotencyKey);
        job.setRequestHash(requestHash);
        job.setSubmittedBy(getLoginUserId());

        // 保存方案快照，保证方案后续修改不会影响已经提交的任务。
        job.setParameterSnapshotJson(JsonUtils.toJsonString(profile));
        job.setValidationJob(validationJob);
        job.setAutoReview(Boolean.TRUE.equals(autoReview));
        job.setAutoPublish(Boolean.TRUE.equals(autoPublish));
        job.setRemark(remark);
        return job;
    }

    private String generateJobNo() {
        return JOB_NO_PREFIX
                + LocalDateTime.now().format(JOB_NO_TIME_FORMATTER)
                + "-"
                + RandomUtil.randomStringUpper(JOB_NO_RANDOM_LENGTH);
    }

    private List<EnhanceItemDO> buildJobItems(Long jobId, List<Long> assetIds) {
        return assetIds.stream()
                .map(assetId -> buildJobItem(jobId, assetId))
                .toList();
    }

    private EnhanceItemDO buildJobItem(Long jobId, Long assetId) {
        EnhanceItemDO item = new EnhanceItemDO();
        item.setJobId(jobId);
        item.setAssetId(assetId);
        item.setStatus(ITEM_PENDING);
        item.setProgress(INITIAL_PROGRESS);
        item.setRetryCount(INITIAL_RETRY_COUNT);
        item.setLockVersion(INITIAL_LOCK_VERSION);
        return item;
    }

    // ==================== 查询任务 ====================

    @Override
    public PageResult<EnhanceJobDO> getPage(EnhanceJobPageReqVO pageReqVO) {
        return enhanceJobMapper.selectPage(pageReqVO);
    }

    @Override
    public EnhanceJobDO get(Long id) {
        EnhanceJobDO job = enhanceJobMapper.selectById(id);
        if (job == null) {
            throw exception(ENHANCE_JOB_NOT_EXISTS);
        }
        return job;
    }

    @Override
    public List<EnhanceItemDO> getItems(Long jobId) {
        get(jobId);
        return enhanceItemMapper.selectByJobId(jobId);
    }

    @Override
    public List<EnhanceStepLogDO> getSteps(Long itemId) {
        if (enhanceItemMapper.selectById(itemId) == null) {
            throw exception(ENHANCE_ITEM_NOT_EXISTS);
        }
        return enhanceStepLogMapper.selectByItemId(itemId);
    }

    @Override
    public EnhanceJobVO.StatisticsResp getStatistics() {
        List<EnhanceJobDO> jobs = enhanceJobMapper.selectList();

        EnhanceJobVO.StatisticsResp statistics = new EnhanceJobVO.StatisticsResp();
        statistics.setPendingCount(countJobsByStatus(jobs, JOB_PENDING));
        statistics.setProcessingCount(countJobsByStatus(jobs, JOB_PROCESSING));
        statistics.setManualCount(sumIntegerField(jobs.stream()
                .map(EnhanceJobDO::getManualCount)
                .toList()));

        List<EnhanceJobDO> todayJobs = filterTodayJobs(jobs);
        statistics.setTodaySuccessRate(calculateTodaySuccessRate(todayJobs));
        statistics.setAverageDurationMs(calculateAverageDuration(jobs));
        return statistics;
    }

    private long countJobsByStatus(List<EnhanceJobDO> jobs, String status) {
        return jobs.stream()
                .filter(job -> status.equals(job.getStatus()))
                .count();
    }

    private List<EnhanceJobDO> filterTodayJobs(List<EnhanceJobDO> jobs) {
        LocalDate today = LocalDate.now();
        return jobs.stream()
                .filter(job -> job.getCreateTime() != null)
                .filter(job -> today.equals(job.getCreateTime().toLocalDate()))
                .toList();
    }

    private double calculateTodaySuccessRate(List<EnhanceJobDO> todayJobs) {
        long successCount = sumIntegerField(todayJobs.stream()
                .map(EnhanceJobDO::getSuccessCount)
                .toList());
        long failedCount = sumIntegerField(todayJobs.stream()
                .map(EnhanceJobDO::getFailedCount)
                .toList());
        long completedCount = successCount + failedCount;
        if (completedCount == 0) {
            return 0D;
        }

        // 保留一位小数，返回 0 至 100 之间的百分比数值。
        return Math.round(successCount * 1000D / completedCount) / 10D;
    }

    private long calculateAverageDuration(List<EnhanceJobDO> jobs) {
        List<EnhanceJobDO> completedJobs = jobs.stream()
                .filter(this::hasCompleteExecutionTime)
                .toList();
        if (completedJobs.isEmpty()) {
            return 0L;
        }

        long totalDurationMs = completedJobs.stream()
                .mapToLong(job -> Duration.between(
                        job.getStartedAt(), job.getFinishedAt()).toMillis())
                .sum();
        return totalDurationMs / completedJobs.size();
    }

    private boolean hasCompleteExecutionTime(EnhanceJobDO job) {
        return job.getStartedAt() != null && job.getFinishedAt() != null;
    }

    private long sumIntegerField(List<Integer> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
    }

    // ==================== 重试与取消 ====================

    @Override
    public void retryItem(Long id, EnhanceJobVO.RetryReq retryReqVO) {
        EnhanceItemDO item = enhanceItemMapper.selectById(id);
        if (item == null) {
            throw exception(ENHANCE_ITEM_NOT_EXISTS);
        }
        if (!RETRYABLE_ITEM_STATUSES.contains(item.getStatus())) {
            throw exception(ENHANCE_JOB_STATE_INVALID);
        }

        enhanceItemMapper.updateById(buildRetryItemUpdate(item, retryReqVO));
        reopenJob(item.getJobId());
    }

    private EnhanceItemDO buildRetryItemUpdate(
            EnhanceItemDO item, EnhanceJobVO.RetryReq retryReqVO) {
        EnhanceItemDO update = new EnhanceItemDO();
        update.setId(item.getId());
        update.setStatus(ITEM_PENDING);
        update.setProgress(INITIAL_PROGRESS);
        update.setRetryCount(item.getRetryCount() + 1);
        update.setManualOverridesJson(serializeManualOverrides(retryReqVO));
        update.setErrorCode(null);
        update.setErrorMessage(null);
        update.setWorkerId(null);
        update.setLeaseUntil(null);
        return update;
    }

    private String serializeManualOverrides(EnhanceJobVO.RetryReq retryReqVO) {
        if (retryReqVO == null || retryReqVO.getManualOverrides() == null) {
            return null;
        }
        return JsonUtils.toJsonString(retryReqVO.getManualOverrides());
    }

    @Override
    public void retryFailed(Long jobId) {
        get(jobId);
        enhanceItemMapper.update(
                new EnhanceItemDO(),
                new LambdaUpdateWrapper<EnhanceItemDO>()
                        .set(EnhanceItemDO::getStatus, ITEM_PENDING)
                        .set(EnhanceItemDO::getProgress, INITIAL_PROGRESS)
                        .setSql("retry_count = retry_count + 1")
                        .eq(EnhanceItemDO::getJobId, jobId)
                        .in(EnhanceItemDO::getStatus, RETRYABLE_ITEM_STATUSES));
        reopenJob(jobId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long jobId) {
        EnhanceJobDO job = get(jobId);
        if (TERMINAL_JOB_STATUSES.contains(job.getStatus())) {
            throw exception(ENHANCE_JOB_STATE_INVALID);
        }

        // 正在处理的任务项交由执行器结束；这里只取消尚未开始的任务项。
        enhanceItemMapper.update(
                new EnhanceItemDO(),
                new LambdaUpdateWrapper<EnhanceItemDO>()
                        .set(EnhanceItemDO::getStatus, ITEM_CANCELLED)
                        .set(EnhanceItemDO::getFinishedAt, LocalDateTime.now())
                        .eq(EnhanceItemDO::getJobId, jobId)
                        .eq(EnhanceItemDO::getStatus, ITEM_PENDING));
        refreshJob(jobId);
    }

    private void reopenJob(Long jobId) {
        EnhanceJobDO update = new EnhanceJobDO();
        update.setId(jobId);
        update.setStatus(JOB_PENDING);
        update.setFinishedAt(null);
        enhanceJobMapper.updateById(update);
    }

    // ==================== 汇总任务状态 ====================

    @Override
    public void refreshJob(Long jobId) {
        EnhanceJobDO job = get(jobId);
        List<EnhanceItemDO> items = enhanceItemMapper.selectByJobId(jobId);

        int successCount = countItemsByStatuses(items, SUCCESSFUL_ITEM_STATUSES);
        int failedCount = countItemsByStatus(items, ITEM_FAILED);
        int manualCount = countItemsByStatus(items, ITEM_MANUAL_REQUIRED);
        boolean active = items.stream()
                .anyMatch(item -> ACTIVE_ITEM_STATUSES.contains(item.getStatus()));
        String status = determineJobStatus(items, successCount, active);

        EnhanceJobDO update = buildJobStatusUpdate(
                job, status, successCount, failedCount, manualCount, active);
        enhanceJobMapper.updateById(update);
    }

    private int countItemsByStatuses(List<EnhanceItemDO> items, Set<String> statuses) {
        return (int) items.stream()
                .filter(item -> statuses.contains(item.getStatus()))
                .count();
    }

    private int countItemsByStatus(List<EnhanceItemDO> items, String status) {
        return (int) items.stream()
                .filter(item -> status.equals(item.getStatus()))
                .count();
    }

    /**
     * 按任务项状态计算任务状态，判断顺序代表业务优先级：
     * 活跃任务、全部成功、部分成功、全部取消，最后才是全部失败。
     */
    private String determineJobStatus(
            List<EnhanceItemDO> items, int successCount, boolean active) {
        if (active) {
            boolean processing = items.stream()
                    .anyMatch(item -> ITEM_PROCESSING.equals(item.getStatus()));
            return processing ? JOB_PROCESSING : JOB_PENDING;
        }
        if (successCount == items.size()) {
            return JOB_COMPLETED;
        }
        if (successCount > 0) {
            return JOB_PARTIAL_SUCCESS;
        }
        if (items.stream().allMatch(item -> ITEM_CANCELLED.equals(item.getStatus()))) {
            return JOB_CANCELLED;
        }
        return JOB_FAILED;
    }

    private EnhanceJobDO buildJobStatusUpdate(
            EnhanceJobDO job,
            String status,
            int successCount,
            int failedCount,
            int manualCount,
            boolean active) {
        EnhanceJobDO update = new EnhanceJobDO();
        update.setId(job.getId());
        update.setStatus(status);
        update.setSuccessCount(successCount);
        update.setFailedCount(failedCount);
        update.setManualCount(manualCount);

        LocalDateTime now = LocalDateTime.now();
        if (!active) {
            update.setFinishedAt(now);
        }
        if (JOB_PROCESSING.equals(status) && job.getStartedAt() == null) {
            update.setStartedAt(now);
        }
        return update;
    }
}
