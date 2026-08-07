package cn.iocoder.yudao.module.drill.controller.admin.quality;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityRecordPageReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityRecordRespVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.ImageQualityRecordDO;
import cn.iocoder.yudao.module.drill.service.quality.ImageQualityRecordService;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "图像质检 - 质检记录")
@RestController
@RequestMapping("/drill/image-quality-record")
@Validated
public class ImageQualityRecordController {

    @Resource
    private ImageQualityRecordService imageQualityRecordService;

    @GetMapping("/page")
    @Operation(summary = "分页查询图片质检记录")
    @PreAuthorize("@ss.hasPermission('drill:image-quality:query')")
    public CommonResult<PageResult<ImageQualityRecordRespVO>> page(
            @Valid ImageQualityRecordPageReqVO reqVO) {
        PageResult<ImageQualityRecordDO> page = imageQualityRecordService.getPage(reqVO);
        return success(new PageResult<>(page.getList().stream().map(this::convert).toList(), page.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "获取图片质检记录详情")
    @PreAuthorize("@ss.hasPermission('drill:image-quality:query')")
    public CommonResult<ImageQualityRecordRespVO> get(@RequestParam Long id) {
        return success(convert(imageQualityRecordService.get(id)));
    }

    @GetMapping("/latest")
    @Operation(summary = "获取图片资产最新质检记录")
    @PreAuthorize("@ss.hasPermission('drill:image-quality:query')")
    public CommonResult<ImageQualityRecordRespVO> latest(@RequestParam Long assetId) {
        return success(convert(imageQualityRecordService.getLatest(assetId)));
    }

    private ImageQualityRecordRespVO convert(ImageQualityRecordDO record) {
        ImageQualityRecordRespVO resp = new ImageQualityRecordRespVO();
        resp.setId(record.getId());
        resp.setAssetId(record.getAssetId());
        resp.setProfileId(record.getProfileId());
        resp.setProfileCode(record.getProfileCode());
        resp.setProfileVersion(record.getProfileVersion());
        resp.setModelCode(record.getModelCode());
        resp.setModelVersion(record.getModelVersion());
        resp.setEffectiveConfig(JsonUtils.parseMap(record.getEffectiveConfigJson()));
        resp.setExecutionStatus(record.getExecutionStatus());
        resp.setPassed(record.getPassed());
        resp.setReason(record.getReason());
        resp.setResult(JsonUtils.parseObjectQuietly(record.getResultJson(), ImageQualityModelResult.class));
        resp.setErrorCode(record.getErrorCode());
        resp.setErrorMessage(record.getErrorMessage());
        resp.setDurationMs(record.getDurationMs());
        resp.setStartedAt(record.getStartedAt());
        resp.setFinishedAt(record.getFinishedAt());
        resp.setCreateTime(record.getCreateTime());
        return resp;
    }

}
