package cn.iocoder.yudao.module.drill.controller.admin.image;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchCreateReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchPageReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchRespVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageBatchDO;
import cn.iocoder.yudao.module.drill.service.image.ImageBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 钻头图片导入批次")
@RestController
@RequestMapping("/drill/image-batch")
@Validated
public class ImageBatchController {

    @Resource
    private ImageBatchService imageBatchService;

    @PostMapping("/create")
    @Operation(summary = "创建图片导入批次")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<Long> createBatch(@Valid @RequestBody ImageBatchCreateReqVO createReqVO) {
        return success(imageBatchService.createBatch(createReqVO));
    }

    @GetMapping("/page")
    @Operation(summary = "获得图片导入批次分页")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:query')")
    public CommonResult<PageResult<ImageBatchRespVO>> getBatchPage(
            @Valid ImageBatchPageReqVO pageReqVO) {
        PageResult<ImageBatchDO> pageResult = imageBatchService.getBatchPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ImageBatchRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得图片导入批次详情")
    @Parameter(name = "id", description = "批次编号", required = true)
    @PreAuthorize("@ss.hasPermission('drill:image-batch:query')")
    public CommonResult<ImageBatchRespVO> getBatch(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(imageBatchService.getBatch(id), ImageBatchRespVO.class));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除图片导入批次及关联图片")
    @Parameter(name = "id", description = "批次编号", required = true)
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<Boolean> deleteBatch(@RequestParam("id") Long id) throws Exception {
        imageBatchService.deleteBatch(id);
        return success(true);
    }

}
