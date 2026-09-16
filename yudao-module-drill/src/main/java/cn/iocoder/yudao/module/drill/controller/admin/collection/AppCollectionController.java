package cn.iocoder.yudao.module.drill.controller.admin.collection;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.AppCollectionTaskVO;
import cn.iocoder.yudao.module.drill.service.collection.AppCollectionService;
import cn.iocoder.yudao.module.drill.service.collection.AppCollectionPhotoQualityService;
import cn.iocoder.yudao.module.drill.service.collection.AppCollectionTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Android App - 钻头采集任务")
@RestController
@RequestMapping("/drill/app-collection")
@PreAuthorize("isAuthenticated()")
public class AppCollectionController {
    @Resource private AppCollectionService service;
    @Resource private AppCollectionTaskService taskService;
    @Resource private AppCollectionPhotoQualityService photoQualityService;

    @PostMapping(value = "/photos/quality-check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "临时检测照片中是否存在钻头切削齿，不保存为任务照片")
    public CommonResult<AppCollectionVO.PhotoQualityResp> checkPhotoQuality(
            @RequestPart("file") MultipartFile file) {
        return success(photoQualityService.check(file));
    }

    @PostMapping("/tasks")
    @Operation(summary = "幂等创建现场采集任务")
    public CommonResult<AppCollectionTaskVO.Resp> createTask(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AppCollectionTaskVO.CreateReq req) {
        // clientId 是持久幂等键；Header 用于链路可观测性并保持通用客户端约定。
        return success(taskService.create(getLoginUserId(), req));
    }

    @PutMapping("/tasks/{id}/draft")
    @Operation(summary = "更新草稿任务")
    public CommonResult<AppCollectionTaskVO.Resp> updateDraft(@PathVariable Long id,
            @Valid @RequestBody AppCollectionTaskVO.UpdateReq req) {
        return success(taskService.updateDraft(getLoginUserId(), id, req));
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "查询任务详情")
    public CommonResult<AppCollectionTaskVO.Resp> getTask(@PathVariable Long id) {
        return success(taskService.get(getLoginUserId(), id));
    }

    @GetMapping("/tasks")
    @Operation(summary = "分页查询当前采集员任务")
    public CommonResult<PageResult<AppCollectionTaskVO.Resp>> getTaskPage(
            @Valid AppCollectionTaskVO.PageReq req) {
        return success(taskService.getPage(getLoginUserId(), req));
    }

    @GetMapping("/tasks/sync")
    @Operation(summary = "增量同步当前采集员任务")
    public CommonResult<AppCollectionTaskVO.SyncResp> syncTasks(
            @RequestParam(value = "cursor", required = false) String cursor) {
        return success(taskService.sync(getLoginUserId(), cursor));
    }

    @DeleteMapping("/tasks/{id}")
    @Operation(summary = "删除草稿任务")
    public CommonResult<Boolean> deleteTask(@PathVariable Long id,
            @RequestParam Integer version,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        taskService.deleteDraft(getLoginUserId(), id, version);
        return success(true);
    }

    @PostMapping(value = "/tasks/{id}/captures/{captureCode}/photo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传或替换任务拍摄项照片")
    public CommonResult<AppCollectionVO.PhotoResp> uploadTaskPhoto(
            @PathVariable Long id,
            @PathVariable String captureCode,
            @Valid @ModelAttribute AppCollectionVO.UploadReq req) {
        return success(taskService.uploadPhoto(getLoginUserId(), id, captureCode, req));
    }

    @PostMapping("/tasks/{id}/submit")
    @Operation(summary = "确认必拍照片上传完成并将任务标记为已完成")
    public CommonResult<AppCollectionTaskVO.Resp> submitTask(@PathVariable Long id) {
        return success(taskService.submit(getLoginUserId(), id));
    }

    @PostMapping("/sync")
    @Operation(summary = "幂等创建或更新本地任务")
    public CommonResult<AppCollectionVO.TaskResp> sync(@Valid @RequestBody AppCollectionVO.SyncReq req) {
        return success(service.sync(getLoginUserId(), req));
    }

    @GetMapping("/list")
    @Operation(summary = "查询当前采集员任务")
    public CommonResult<List<AppCollectionVO.TaskResp>> list() {
        return success(service.list(getLoginUserId()));
    }

    @GetMapping("/statistics")
    @Operation(summary = "查询当前采集员任务统计")
    public CommonResult<AppCollectionVO.StatisticsResp> statistics() {
        return success(service.statistics(getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "查询任务详情")
    public CommonResult<AppCollectionVO.TaskResp> get(@RequestParam String clientId) {
        return success(service.get(getLoginUserId(), clientId));
    }

    @PostMapping(value = "/{clientId}/capture/{captureId}/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传或替换拍摄项照片")
    public CommonResult<AppCollectionVO.PhotoResp> upload(
            @PathVariable String clientId,
            @PathVariable String captureId,
            @Valid @ModelAttribute AppCollectionVO.UploadReq req) {
        return success(service.upload(getLoginUserId(), clientId, captureId, req));
    }

    @PostMapping("/{clientId}/submit")
    @Operation(summary = "校验拍摄清单并提交处理")
    public CommonResult<AppCollectionVO.TaskResp> submit(@PathVariable String clientId) {
        return success(service.submit(getLoginUserId(), clientId));
    }
}
