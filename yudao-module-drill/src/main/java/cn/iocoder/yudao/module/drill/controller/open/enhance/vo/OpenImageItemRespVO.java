package cn.iocoder.yudao.module.drill.controller.open.enhance.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenImageItemRespVO {

    private Long assetId;
    private String assetNo;
    private Long batchId;
    private String drillCode;
    private String drillType;
    private String sceneType;
    private String photoType;
    private String bladeNo;
    private Long imageVersionId;
    private Integer versionNo;
    private String purpose;
    private Boolean defaultVersion;
    private Integer width;
    private Integer height;
    private String mimeType;
    private Long fileSize;
    private String sha256;
    private String profileCode;
    private String profileVersion;
    private LocalDateTime publishedAt;

}
