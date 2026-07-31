package cn.iocoder.yudao.module.drill.controller.admin.enhance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceJobPageReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceJobVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.*;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageBatchDO;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.EnhanceItemMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.EnhanceProfileMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageAssetMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageBatchMapper;
import cn.iocoder.yudao.module.drill.service.enhance.EnhanceJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 图像增强任务")
@RestController
@RequestMapping("/drill/enhance-job")
@Validated
public class EnhanceJobController {
    @Resource private EnhanceJobService enhanceJobService;
    @Resource private EnhanceProfileMapper profileMapper;
    @Resource private EnhanceItemMapper itemMapper;
    @Resource private ImageAssetMapper assetMapper;
    @Resource private ImageBatchMapper batchMapper;

    /**
     * 创建图像增强任务。
     *
     * <p>客户端可通过 {@code Idempotency-Key} 标识同一次创建请求。相同幂等键及相同请求内容
     * 会返回已创建的任务编号，避免因网络重试而重复创建任务。</p>
     *
     * @param createReqVO 创建任务参数
     * @param idempotencyKey 幂等键，可选
     * @return 新建或已存在的增强任务编号
     */
    @PostMapping("/create")
    @Operation(summary = "创建图像增强任务")
    @PreAuthorize("@ss.hasPermission('drill:enhance-job:create')")
    public CommonResult<Long> createEnhanceJob(
            @Valid @RequestBody EnhanceJobVO.CreateReq createReqVO,
            @Parameter(description = "请求幂等键，用于避免重复创建任务")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long enhanceJobId = enhanceJobService.create(createReqVO, idempotencyKey);
        return success(enhanceJobId);
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('drill:enhance-job:query')")
    public CommonResult<PageResult<EnhanceJobVO.Resp>> page(@Valid EnhanceJobPageReqVO req) {
        PageResult<EnhanceJobDO> page = enhanceJobService.getPage(req);
        Set<Long> profileIds = page.getList().stream().map(EnhanceJobDO::getProfileId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> batchIds = page.getList().stream().map(EnhanceJobDO::getBatchId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, EnhanceProfileDO> profiles = profileIds.isEmpty() ? Map.of()
                : profileMapper.selectByIds(profileIds).stream()
                        .collect(Collectors.toMap(EnhanceProfileDO::getId, Function.identity()));
        Map<Long, ImageBatchDO> batches = batchIds.isEmpty() ? Map.of()
                : batchMapper.selectByIds(batchIds).stream()
                        .collect(Collectors.toMap(ImageBatchDO::getId, Function.identity()));
        List<EnhanceJobVO.Resp> list = page.getList().stream()
                .map(job -> convertJob(job,
                        job.getProfileId() == null ? null : profiles.get(job.getProfileId()),
                        job.getBatchId() == null ? null : batches.get(job.getBatchId())))
                .toList();
        return success(new PageResult<>(list, page.getTotal()));
    }
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('drill:enhance-job:query')")
    public CommonResult<EnhanceJobVO.Resp> get(@RequestParam Long id) {
        EnhanceJobDO job = enhanceJobService.get(id);
        return success(convertJob(job, profileMapper.selectById(job.getProfileId()),
                job.getBatchId() == null ? null : batchMapper.selectById(job.getBatchId())));
    }
    @GetMapping("/item/list")
    @PreAuthorize("@ss.hasPermission('drill:enhance-job:query')")
    public CommonResult<List<EnhanceJobVO.ItemResp>> items(@RequestParam Long jobId) {
        List<EnhanceItemDO> items = enhanceJobService.getItems(jobId);
        Set<Long> assetIds = items.stream().map(EnhanceItemDO::getAssetId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ImageAssetDO> assets = assetIds.isEmpty() ? Map.of()
                : assetMapper.selectByIds(assetIds).stream()
                        .collect(Collectors.toMap(ImageAssetDO::getId, Function.identity()));
        return success(items.stream().map(item -> {
            EnhanceJobVO.ItemResp resp = BeanUtils.toBean(item, EnhanceJobVO.ItemResp.class);
            ImageAssetDO asset = item.getAssetId() == null ? null : assets.get(item.getAssetId());
            if (asset != null) {
                resp.setAssetNo(asset.getAssetNo());
                resp.setOriginalName(asset.getOriginalName());
                resp.setOriginalUrl(asset.getOriginalUrl());
            }
            return resp;
        }).toList());
    }
    @GetMapping("/item/steps")
    @PreAuthorize("@ss.hasPermission('drill:enhance-job:query')")
    public CommonResult<List<EnhanceJobVO.StepResp>> steps(@RequestParam Long itemId) {
        return success(BeanUtils.toBean(enhanceJobService.getSteps(itemId), EnhanceJobVO.StepResp.class));
    }
    @GetMapping("/statistics")
    @PreAuthorize("@ss.hasPermission('drill:enhance-job:query')")
    public CommonResult<EnhanceJobVO.StatisticsResp> statistics() {
        return success(enhanceJobService.getStatistics());
    }
    @PostMapping("/item/retry")
    @PreAuthorize("@ss.hasPermission('drill:enhance-job:retry')")
    public CommonResult<Boolean> retry(@RequestParam Long id, @RequestBody(required = false) EnhanceJobVO.RetryReq req) {
        enhanceJobService.retryItem(id, req); return success(true);
    }
    @PostMapping("/retry-failed")
    @PreAuthorize("@ss.hasPermission('drill:enhance-job:retry')")
    public CommonResult<Boolean> retryFailed(@RequestParam Long id) { enhanceJobService.retryFailed(id); return success(true); }
    @PutMapping("/cancel")
    @PreAuthorize("@ss.hasPermission('drill:enhance-job:create')")
    public CommonResult<Boolean> cancel(@RequestParam Long id) { enhanceJobService.cancel(id); return success(true); }

    private EnhanceJobVO.Resp convertJob(EnhanceJobDO job, EnhanceProfileDO profile, ImageBatchDO batch) {
        EnhanceJobVO.Resp resp = BeanUtils.toBean(job, EnhanceJobVO.Resp.class);
        resp.setProfileName(profile == null ? "未知方案" : profile.getName());
        if (batch != null) {
            resp.setSource(batch.getName());
        } else {
            resp.setSource(Boolean.TRUE.equals(job.getValidationJob()) ? "方案验证样本" : "指定图片");
        }
        List<EnhanceItemDO> items = itemMapper.selectByJobId(job.getId());
        resp.setProgress(items.isEmpty() ? 0 : (int) Math.round(items.stream()
                .map(EnhanceItemDO::getProgress)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average().orElse(0D)));
        if (job.getStartedAt() != null) {
            resp.setDurationMs(Duration.between(job.getStartedAt(),
                    job.getFinishedAt() == null ? java.time.LocalDateTime.now() : job.getFinishedAt()).toMillis());
        }
        return resp;
    }
}
