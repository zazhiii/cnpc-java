package cn.iocoder.yudao.module.drill.controller.admin.quality.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "Android App - E11 图片质量检测 Response VO")
@Data
public class E11QualityGateRespVO {

    @Schema(description = "图片是否合格", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean passed;
    @Schema(description = "模型判定原因", example = "passed")
    private String reason;
    @Schema(description = "模型版本", example = "E11")
    private String modelVersion;
    private Double threshold;
    private Integer minDetections;
    private Integer acceptedCount;
    private Integer candidateCount;
    private Map<String, Integer> rejectionCounts;
    private ConfidenceSummary confidenceSummary;
    private ImageInfo image;
    private List<Detection> detections;
    private Map<String, Double> inferenceSpeedMs;
    @Schema(description = "接口侧总处理耗时（毫秒）")
    private Long durationMs;

    @Data
    public static class ConfidenceSummary {
        private Double minimum;
        private Double maximum;
        private Double mean;
    }

    @Data
    public static class ImageInfo {
        private Integer width;
        private Integer height;
    }

    @Data
    public static class Detection {
        private String label;
        private Double confidence;
        @Schema(description = "原图像素坐标多边形点集，点格式为 [x, y]")
        private List<List<Double>> polygon;
    }

}
