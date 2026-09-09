package cn.iocoder.yudao.module.drill.service.quality.checker;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.drill.service.quality.E11QualityGateService;
import cn.iocoder.yudao.module.drill.service.quality.ImageQualityCheckResult;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.QUALITY_PROFILE_INVALID;

@Component
@RequiredArgsConstructor
public class E11ModelQualityChecker implements ImageQualityChecker {

    public static final String CHECK_CODE = "E11_MODEL";

    private final E11QualityGateService qualityGateService;

    @Override
    public String getCheckCode() {
        return CHECK_CODE;
    }

    @Override
    public void validateConfig(Map<String, Object> config) {
        if (config == null || !config.keySet().equals(Set.of("confidence", "minDetections"))) {
            throw exception(QUALITY_PROFILE_INVALID, "E11 参数必须且只能包含 confidence、minDetections");
        }
        double confidence = number(config.get("confidence"), "confidence").doubleValue();
        int minDetections = integer(config.get("minDetections"), "minDetections");
        if (!Double.isFinite(confidence) || confidence < 0D || confidence > 1D) {
            throw exception(QUALITY_PROFILE_INVALID, "confidence 必须在 [0,1] 范围内");
        }
        if (minDetections < 1 || minDetections > 300) {
            throw exception(QUALITY_PROFILE_INVALID, "minDetections 必须在 [1,300] 范围内");
        }
    }

    @Override
    public void checkAvailability(Map<String, Object> config) {
        validateConfig(config);
        qualityGateService.checkAvailability();
    }

    @Override
    public ImageQualityCheckResult check(QualityImageInput image, Map<String, Object> config) {
        validateConfig(config);
        qualityGateService.validateConfig(config);
        long startedNanos = System.nanoTime();
        ImageQualityModelResult modelResult = qualityGateService.execute(image, config);

        Map<String, Object> metrics = new LinkedHashMap<>();
        putIfNotNull(metrics, "modelVersion", modelResult.getModelVersion());
        putIfNotNull(metrics, "threshold", modelResult.getThreshold());
        putIfNotNull(metrics, "minDetections", modelResult.getMinDetections());
        putIfNotNull(metrics, "acceptedCount", modelResult.getAcceptedCount());
        putIfNotNull(metrics, "candidateCount", modelResult.getCandidateCount());
        putIfNotNull(metrics, "rejectionCounts", modelResult.getRejectionCounts());
        putIfNotNull(metrics, "confidenceSummary", modelResult.getConfidenceSummary());

        Map<String, Object> details = JsonUtils.convertObject(modelResult,
                new TypeReference<Map<String, Object>>() {
                });
        boolean passed = Boolean.TRUE.equals(modelResult.getPassed());
        return CheckerSupport.result(CHECK_CODE, passed,
                passed ? null : "MODEL_NO_DETECTION", modelResult.getReason(),
                metrics, details, startedNanos);
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Number number(Object value, String name) {
        if (!(value instanceof Number number)) {
            throw exception(QUALITY_PROFILE_INVALID, name + " 必须是数值");
        }
        return number;
    }

    private int integer(Object value, String name) {
        Number number = number(value, name);
        double raw = number.doubleValue();
        if (!Double.isFinite(raw) || raw != Math.rint(raw)) {
            throw exception(QUALITY_PROFILE_INVALID, name + " 必须是整数");
        }
        return number.intValue();
    }

}
