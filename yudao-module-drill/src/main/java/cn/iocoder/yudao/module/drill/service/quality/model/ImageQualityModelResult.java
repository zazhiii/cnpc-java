package cn.iocoder.yudao.module.drill.service.quality.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ImageQualityModelResult {

    private Boolean passed;
    private String reason;
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
    private Long durationMs;
    private Map<String, Object> details;

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
        private List<List<Double>> polygon;
    }

}
