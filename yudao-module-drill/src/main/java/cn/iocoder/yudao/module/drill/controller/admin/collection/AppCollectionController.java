package cn.iocoder.yudao.module.drill.controller.admin.collection;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.service.collection.AppCollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Android App - 钻头采集任务")
@RestController
@RequestMapping("/drill/app-collection")
@PreAuthorize("isAuthenticated()")
public class AppCollectionController {
    @Resource private AppCollectionService service;

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
