package cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 图片资产 Response VO")
@Data
public class ImageAssetRespVO {

    private Long id;
    private String assetNo;
    private Long batchId;
    private Long projectId;
    private String drillCode;
    private String drillType;
    private String sceneType;
    private String photoType;
    private String bladeNo;
    private String sourceType;
    private Long originalFileId;
    private Long fileConfigId;
    private String objectKey;
    private String originalUrl;
    private String originalName;
    private String mimeType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String sha256;
    private LocalDateTime captureTime;
    private String metadataStatus;
    private String archiveStatus;
    private String remark;
    private LocalDateTime createTime;

}
