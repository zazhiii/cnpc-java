package cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "管理后台 - 直接上传并导入图片 Request VO")
@Data
public class ImageAssetUploadReqVO {

    @NotNull(message = "批次编号不能为空")
    private Long batchId;

    @NotNull(message = "图片文件不能为空")
    private MultipartFile[] files;

    private String drillCode;
    private String drillType;
    private String sceneType;
    private String photoType;
    private String bladeNo;
    private String unknownMetadataPolicy = "PENDING";
    private String remark;

}
