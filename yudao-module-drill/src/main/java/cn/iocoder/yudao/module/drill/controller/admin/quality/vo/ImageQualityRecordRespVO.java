package cn.iocoder.yudao.module.drill.controller.admin.quality.vo;

import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ImageQualityRecordRespVO {
    private Long id;
    private Long assetId;
    private Long profileId;
    private String profileCode;
    private String profileVersion;
    private String modelCode;
    private String modelVersion;
    private Map<String, Object> effectiveConfig;
    private String executionStatus;
    private Boolean passed;
    private String reason;
    private ImageQualityModelResult result;
    private Integer errorCode;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
}
