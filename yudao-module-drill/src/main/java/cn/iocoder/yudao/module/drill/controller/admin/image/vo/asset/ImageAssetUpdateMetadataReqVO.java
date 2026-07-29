package cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 更新图片元数据 Request VO")
@Data
public class ImageAssetUpdateMetadataReqVO {

    @NotNull(message = "图片资产编号不能为空")
    private Long id;
    private String drillCode;
    private String drillType;
    private String sceneType;
    private String photoType;
    private String bladeNo;
    private LocalDateTime captureTime;
    private String remark;

}
