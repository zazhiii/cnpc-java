package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.E11QualityGateRespVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将模型进程 JSON 转为对外响应，并校验模型契约。 */
final class E11GateReportParser {

    private static final String EXPECTED_LABEL = "CUTTING_TOOTH";

    private final ObjectMapper objectMapper;

    E11GateReportParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    E11QualityGateRespVO parse(String json, int exitCode, double requestedConfidence,
                               int requestedMinDetections) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw invalid("根节点不是 JSON 对象");
            }
            E11QualityGateRespVO result = new E11QualityGateRespVO();
            boolean passed = requiredBoolean(root, "passed");
            if (passed != (exitCode == 0)) {
                throw invalid("passed 与进程退出码不一致");
            }
            result.setPassed(passed);
            result.setReason(requiredText(root, "reason"));
            result.setModelVersion(requiredText(root, "model_version"));

            double threshold = requiredDouble(root, "threshold");
            int minDetections = requiredNonNegativeInt(root, "min_detections");
            if (Math.abs(threshold - requestedConfidence) > 1.0E-9
                    || minDetections != requestedMinDetections) {
                throw invalid("模型返回的门禁参数与请求不一致");
            }
            result.setThreshold(threshold);
            result.setMinDetections(minDetections);

            int acceptedCount = requiredNonNegativeInt(root, "accepted_count");
            int candidateCount = requiredNonNegativeInt(root, "candidate_count");
            if (candidateCount < acceptedCount || passed != (acceptedCount >= minDetections)) {
                throw invalid("检测数量与 passed 不一致");
            }
            result.setAcceptedCount(acceptedCount);
            result.setCandidateCount(candidateCount);
            result.setRejectionCounts(integerMap(root.get("rejection_counts")));
            result.setConfidenceSummary(confidenceSummary(root.get("confidence_summary")));
            result.setImage(imageInfo(root.get("image")));

            List<E11QualityGateRespVO.Detection> detections = detections(root.get("detections"), threshold);
            if (detections.size() != acceptedCount) {
                throw invalid("detections 数量与 accepted_count 不一致");
            }
            result.setDetections(detections);
            result.setInferenceSpeedMs(doubleMap(root.get("inference_speed_ms")));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid("无法解析 JSON");
        }
    }

    private List<E11QualityGateRespVO.Detection> detections(JsonNode node, double threshold) {
        if (node == null || !node.isArray()) {
            throw invalid("detections 必须是数组");
        }
        List<E11QualityGateRespVO.Detection> result = new ArrayList<>();
        for (JsonNode item : node) {
            E11QualityGateRespVO.Detection detection = new E11QualityGateRespVO.Detection();
            String label = requiredText(item, "label");
            if (!EXPECTED_LABEL.equals(label)) {
                throw invalid("检测标签不是 CUTTING_TOOTH");
            }
            double confidence = requiredDouble(item, "confidence");
            if (confidence < threshold || confidence > 1D) {
                throw invalid("检测置信度超出有效范围");
            }
            JsonNode polygonNode = item.get("polygon");
            if (polygonNode == null || !polygonNode.isArray() || polygonNode.size() < 3) {
                throw invalid("polygon 至少需要三个点");
            }
            List<List<Double>> polygon = new ArrayList<>();
            for (JsonNode point : polygonNode) {
                if (!point.isArray() || point.size() != 2) {
                    throw invalid("polygon 点格式错误");
                }
                double x = finiteNumber(point.get(0), "polygon.x");
                double y = finiteNumber(point.get(1), "polygon.y");
                if (x < 0D || y < 0D) {
                    throw invalid("polygon 坐标不能为负数");
                }
                polygon.add(List.of(x, y));
            }
            detection.setLabel(label);
            detection.setConfidence(confidence);
            detection.setPolygon(polygon);
            result.add(detection);
        }
        return result;
    }

    private E11QualityGateRespVO.ImageInfo imageInfo(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalid("image 必须是对象");
        }
        int width = requiredNonNegativeInt(node, "width");
        int height = requiredNonNegativeInt(node, "height");
        if (width == 0 || height == 0) {
            throw invalid("图片尺寸必须大于零");
        }
        E11QualityGateRespVO.ImageInfo info = new E11QualityGateRespVO.ImageInfo();
        info.setWidth(width);
        info.setHeight(height);
        return info;
    }

    private E11QualityGateRespVO.ConfidenceSummary confidenceSummary(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalid("confidence_summary 必须是对象");
        }
        E11QualityGateRespVO.ConfidenceSummary summary = new E11QualityGateRespVO.ConfidenceSummary();
        summary.setMinimum(nullableDouble(node.get("minimum"), "minimum"));
        summary.setMaximum(nullableDouble(node.get("maximum"), "maximum"));
        summary.setMean(nullableDouble(node.get("mean"), "mean"));
        return summary;
    }

    private Map<String, Integer> integerMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalid("rejection_counts 必须是对象");
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        fields.forEachRemaining(entry -> result.put(entry.getKey(), nonNegativeInt(entry.getValue(), entry.getKey())));
        return result;
    }

    private Map<String, Double> doubleMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalid("inference_speed_ms 必须是对象");
        }
        Map<String, Double> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        fields.forEachRemaining(entry -> result.put(entry.getKey(), finiteNumber(entry.getValue(), entry.getKey())));
        return result;
    }

    private boolean requiredBoolean(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isBoolean()) {
            throw invalid(field + " 必须是布尔值");
        }
        return value.booleanValue();
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + " 必须是非空字符串");
        }
        return value.textValue();
    }

    private int requiredNonNegativeInt(JsonNode root, String field) {
        return nonNegativeInt(root.get(field), field);
    }

    private int nonNegativeInt(JsonNode value, String field) {
        if (value == null || !value.canConvertToInt() || !value.isIntegralNumber() || value.intValue() < 0) {
            throw invalid(field + " 必须是非负整数");
        }
        return value.intValue();
    }

    private double requiredDouble(JsonNode root, String field) {
        return finiteNumber(root.get(field), field);
    }

    private Double nullableDouble(JsonNode value, String field) {
        return value == null || value.isNull() ? null : finiteNumber(value, field);
    }

    private double finiteNumber(JsonNode value, String field) {
        if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw invalid(field + " 必须是有限数值");
        }
        return value.doubleValue();
    }

    private IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException(detail);
    }

}
