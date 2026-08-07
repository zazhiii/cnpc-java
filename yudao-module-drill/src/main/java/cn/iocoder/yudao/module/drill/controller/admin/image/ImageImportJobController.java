package cn.iocoder.yudao.module.drill.controller.admin.image;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.job.*;
import cn.iocoder.yudao.module.drill.service.image.ImageImportJobService;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ZIP 图片异步导入")
@RestController
@RequestMapping("/drill/image-import-job")
@Validated
public class ImageImportJobController {
    @Resource private ImageImportJobService imageImportJobService;

    @PostMapping("/create")
    @Operation(summary = "创建目录 ZIP 导入任务")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<ImageImportJobRespVO> create(@Valid @RequestBody ImageImportJobCreateReqVO reqVO) {
        return success(imageImportJobService.createJob(reqVO));
    }

    @PostMapping("/package/{id}/presign")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<FilePresignedUrlRespVO> presign(@PathVariable("id") Long id) {
        return success(imageImportJobService.presignPackage(id));
    }

    @PostMapping("/package/{id}/upload-complete")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<Boolean> uploadComplete(@PathVariable("id") Long id,
                                                 @Valid @RequestBody ImageImportUploadCompleteReqVO reqVO) {
        imageImportJobService.completePackageUpload(id, reqVO);
        return success(true);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<Boolean> start(@PathVariable("id") Long id) {
        imageImportJobService.startJob(id);
        return success(true);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:query')")
    public CommonResult<ImageImportJobRespVO> get(@PathVariable("id") Long id) {
        return success(imageImportJobService.getJob(id));
    }

    @GetMapping("/by-batch")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:query')")
    public CommonResult<ImageImportJobRespVO> getByBatch(@RequestParam("batchId") Long batchId) {
        return success(imageImportJobService.getJobByBatchId(batchId));
    }

    @GetMapping("/package/page")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:query')")
    public CommonResult<PageResult<ImageImportPackageRespVO>> packagePage(
            @Valid ImageImportPackagePageReqVO reqVO) {
        return success(imageImportJobService.getPackagePage(reqVO));
    }

    @PostMapping("/package/{id}/retry")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<Boolean> retry(@PathVariable("id") Long id) {
        imageImportJobService.retryPackage(id);
        return success(true);
    }

    @PostMapping("/package/{id}/reset-upload")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<FilePresignedUrlRespVO> resetUpload(@PathVariable("id") Long id) {
        return success(imageImportJobService.resetPackageUpload(id));
    }

    @PostMapping("/{id}/finish-partial")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<Boolean> finishPartial(@PathVariable("id") Long id) {
        imageImportJobService.finishPartial(id);
        return success(true);
    }
}
