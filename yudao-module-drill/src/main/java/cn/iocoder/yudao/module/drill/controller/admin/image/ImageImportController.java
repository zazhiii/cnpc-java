package cn.iocoder.yudao.module.drill.controller.admin.image;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageAssetUploadReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageImportCompleteReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageImportRespVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImagePresignedUploadReqVO;
import cn.iocoder.yudao.module.drill.service.image.ImageImportService;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 独立的图片导入入口。 */
@Tag(name = "管理后台 - 图片导入")
@RestController
@RequestMapping("/drill/image-import")
@Validated
public class ImageImportController {

    @Resource
    private ImageImportService imageImportService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "直接上传并导入图片",
            description = "允许部分成功；只登记图片资产，不自动触发增强或质检。")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<ImageImportRespVO> uploadAndImport(
            @Valid @ModelAttribute ImageAssetUploadReqVO uploadReqVO) {
        return success(imageImportService.uploadAndImport(uploadReqVO));
    }

    @PostMapping("/presigned-upload")
    @Operation(summary = "申请图片预签名上传地址")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<FilePresignedUrlRespVO> createPresignedUpload(
            @Valid @RequestBody ImagePresignedUploadReqVO reqVO) {
        return success(imageImportService.createPresignedUpload(reqVO));
    }

    @PostMapping("/import-complete")
    @Operation(summary = "确认预签名上传并导入图片")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<ImageImportRespVO> completeImport(
            @Valid @RequestBody ImageImportCompleteReqVO reqVO) {
        return success(imageImportService.completeImport(reqVO));
    }

}
