package cn.iocoder.yudao.module.drill.controller.admin.image;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.*;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.service.image.ImageAssetService;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 钻头图片资产")
@RestController
@RequestMapping("/drill/image-asset")
@Validated
public class ImageAssetController {

    private static final int ASSET_ACCESS_URL_EXPIRATION_SECONDS = 3600;

    @Resource
    private ImageAssetService imageAssetService;
    @Resource
    private FileService fileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "直接上传并导入图片",
            description = "适合小批量图片。允许部分成功，响应包含每个文件的处理结果。")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<ImageImportRespVO> uploadAndImport(
            @Valid @ModelAttribute ImageAssetUploadReqVO uploadReqVO) {
        return success(imageAssetService.uploadAndImport(uploadReqVO));
    }

    @PostMapping("/presigned-upload")
    @Operation(summary = "申请图片预签名上传地址",
            description = "适合大批量图片。浏览器直传对象存储后，再调用 import-complete。")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<FilePresignedUrlRespVO> createPresignedUpload(
            @Valid @RequestBody ImagePresignedUploadReqVO reqVO) {
        return success(imageAssetService.createPresignedUpload(reqVO));
    }

    @PostMapping("/import-complete")
    @Operation(summary = "确认预签名上传并导入图片",
            description = "服务端重新读取对象，校验大小、摘要、格式和分辨率后登记图片资产。")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:import')")
    public CommonResult<ImageImportRespVO> completeImport(
            @Valid @RequestBody ImageImportCompleteReqVO reqVO) {
        return success(imageAssetService.completeImport(reqVO));
    }

    @GetMapping("/page")
    @Operation(summary = "获得图片资产分页")
    @PreAuthorize("@ss.hasPermission('drill:image-asset:query')")
    public CommonResult<PageResult<ImageAssetRespVO>> getAssetPage(
            @Valid ImageAssetPageReqVO pageReqVO) {
        PageResult<ImageAssetDO> pageResult = imageAssetService.getAssetPage(pageReqVO);
        return success(new PageResult<>(pageResult.getList().stream()
                .map(this::convertAssetResp)
                .toList(), pageResult.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得图片资产详情")
    @Parameter(name = "id", description = "图片资产编号", required = true)
    @PreAuthorize("@ss.hasPermission('drill:image-asset:query')")
    public CommonResult<ImageAssetRespVO> getAsset(@RequestParam("id") Long id) {
        return success(convertAssetResp(imageAssetService.getAsset(id)));
    }

    @PutMapping("/update-metadata")
    @Operation(summary = "更新图片业务元数据")
    @PreAuthorize("@ss.hasPermission('drill:image-asset:update')")
    public CommonResult<Boolean> updateAssetMetadata(
            @Valid @RequestBody ImageAssetUpdateMetadataReqVO updateReqVO) {
        imageAssetService.updateAssetMetadata(updateReqVO);
        return success(true);
    }

    private ImageAssetRespVO convertAssetResp(ImageAssetDO asset) {
        ImageAssetRespVO respVO = BeanUtils.toBean(asset, ImageAssetRespVO.class);
        respVO.setOriginalUrl(fileService.presignGetUrl(asset.getFileConfigId(), asset.getObjectKey(),
                ASSET_ACCESS_URL_EXPIRATION_SECONDS));
        return respVO;
    }

}
