package cn.iocoder.yudao.module.drill.service.enhance.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 增强方案 JSON 字段及流水线运行时共享模型。
 */
public final class EnhanceModels {

    private EnhanceModels() {
    }

    @Data
    public static class StepConfig {
        private Integer stepOrder;
        private String code;
        private String name;
        private Boolean enabled = true;
        private String algorithm;
        private String algorithmVersion;
        private Map<String, Object> parameters = new LinkedHashMap<>();
    }

    @Data
    public static class OutputSpec {
        private Integer width;
        private Integer height;
        private Boolean keepAspectRatio = false;
        private String colorSpace = "sRGB";
        private String format = "AUTO";
        private Integer quality = 92;
    }

    @Data
    public static class ManualOverrides {
        private CropBox cropBox;
        private Double rotationDegrees;
        private Double targetSubjectRatio;
    }

    @Data
    public static class CropBox {
        private Integer x;
        private Integer y;
        private Integer width;
        private Integer height;
    }

    @Data
    public static class PipelineResult {
        private byte[] content;
        private byte[] maskContent;
        private String extension;
        private String mimeType;
        private Integer width;
        private Integer height;
        private String sha256;
        private Map<String, Object> metrics = new LinkedHashMap<>();
        private List<String> warnings = new ArrayList<>();
        private double[] transformMatrix = new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
    }
}
