package cn.iocoder.yudao.module.drill.controller.admin.image.vo.record;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 图片导入明细 Response VO")
@Data
public class ImageImportRecordRespVO {

    private Long id;
    private Long batchId;
    private String clientFileId;
    private String originalName;
    private Long fileSize;
    private String sha256;
    private String result;
    private Long assetId;
    private Long duplicateAssetId;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createTime;

}
