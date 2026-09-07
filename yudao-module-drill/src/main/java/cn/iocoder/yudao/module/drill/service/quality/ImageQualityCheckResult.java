package cn.iocoder.yudao.module.drill.service.quality;

import lombok.Data;

import java.util.Map;

@Data
public class ImageQualityCheckResult {

    private String checkCode;

    private Boolean passed;

    // OVER_EXPOSURE、MODEL_NO_DETECTION、RESOLUTION_TOO_LOW
    private String reasonCode;

    private String reason;

    // 可检索、可展示的关键指标
    private Map<String, Object> metrics;

    // 检查器特有的完整结果
    private Map<String, Object> details;

    private Long durationMs;
}