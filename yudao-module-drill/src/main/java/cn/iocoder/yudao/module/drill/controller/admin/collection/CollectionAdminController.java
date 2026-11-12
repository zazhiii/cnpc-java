package cn.iocoder.yudao.module.drill.controller.admin.collection;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.PredictionDetail;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.PredictionSummary;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.TaskDetail;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.TaskPageReq;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.TaskSummary;
import cn.iocoder.yudao.module.drill.service.collection.CollectionAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - App 采集任务")
@RestController
@RequestMapping("/drill/collection/tasks")
@Validated
public class CollectionAdminController {

    @Resource
    private CollectionAdminService collectionAdminService;

    @GetMapping("/page")
    @Operation(summary = "分页查询 App 采集任务")
    @PreAuthorize("@ss.hasPermission('drill:collection:query')")
    public CommonResult<PageResult<TaskSummary>> page(@Valid TaskPageReq req) {
        return success(collectionAdminService.getTaskPage(req));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查询采集任务详情和照片")
    @PreAuthorize("@ss.hasPermission('drill:collection:query')")
    public CommonResult<TaskDetail> get(@PathVariable @Positive Long taskId) {
        return success(collectionAdminService.getTask(taskId));
    }

    @GetMapping("/{taskId}/photos/{photoId}/predictions/page")
    @Operation(summary = "分页查询刀翼照片的预测历史")
    @PreAuthorize("@ss.hasPermission('drill:collection:query')")
    public CommonResult<PageResult<PredictionSummary>> getPredictionPage(
            @PathVariable @Positive Long taskId, @PathVariable @Positive Long photoId, @Valid PageParam page) {
        return success(collectionAdminService.getPredictionPage(taskId, photoId, page));
    }

    @GetMapping("/{taskId}/photos/{photoId}/predictions/{predictionId}")
    @Operation(summary = "查询单次预测及其逐齿结果")
    @PreAuthorize("@ss.hasPermission('drill:collection:query')")
    public CommonResult<PredictionDetail> getPrediction(
            @PathVariable @Positive Long taskId, @PathVariable @Positive Long photoId,
            @PathVariable @Positive Long predictionId) {
        return success(collectionAdminService.getPrediction(taskId, photoId, predictionId));
    }
}
