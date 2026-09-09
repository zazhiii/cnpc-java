package cn.iocoder.yudao.module.drill.service.quality.checker;

import cn.iocoder.yudao.module.drill.service.quality.ImageQualityCheckResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.QUALITY_PROFILE_INVALID;

@Component
public class MinResolutionQualityChecker implements ImageQualityChecker {

    public static final String CHECK_CODE = "MIN_RESOLUTION";
    private static final Set<String> CONFIG_KEYS = Set.of("minWidth", "minHeight", "minPixelCount");

    @Override
    public String getCheckCode() {
        return CHECK_CODE;
    }

    @Override
    public void validateConfig(Map<String, Object> config) {
        Map<String, Object> value = CheckerSupport.config(config);
        CheckerSupport.requireOnlyKeys(value, CONFIG_KEYS, CHECK_CODE);
        if (!value.containsKey("minWidth") || !value.containsKey("minHeight")) {
            throw exception(QUALITY_PROFILE_INVALID, "MIN_RESOLUTION 必须包含 minWidth、minHeight");
        }
        int minWidth = CheckerSupport.integer(value, "minWidth", null);
        int minHeight = CheckerSupport.integer(value, "minHeight", null);
        if (minWidth < 1 || minHeight < 1) {
            throw exception(QUALITY_PROFILE_INVALID, "minWidth、minHeight 必须大于等于 1");
        }
        if (value.containsKey("minPixelCount")
                && CheckerSupport.longInteger(value, "minPixelCount", null) < 1L) {
            throw exception(QUALITY_PROFILE_INVALID, "minPixelCount 必须大于等于 1");
        }
    }

    @Override
    public void checkAvailability(Map<String, Object> config) {
        validateConfig(config);
    }

    @Override
    public ImageQualityCheckResult check(QualityImageInput input, Map<String, Object> config) {
        validateConfig(config);
        long startedNanos = System.nanoTime();
        Map<String, Object> value = CheckerSupport.config(config);
        int minWidth = CheckerSupport.integer(value, "minWidth", null);
        int minHeight = CheckerSupport.integer(value, "minHeight", null);
        Long minPixelCount = value.containsKey("minPixelCount")
                ? CheckerSupport.longInteger(value, "minPixelCount", null) : null;
        int width = input.width() == null ? 0 : input.width();
        int height = input.height() == null ? 0 : input.height();
        long pixelCount = (long) width * height;
        boolean passed = width >= minWidth && height >= minHeight
                && (minPixelCount == null || pixelCount >= minPixelCount);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("width", width);
        metrics.put("height", height);
        metrics.put("pixelCount", pixelCount);
        metrics.put("minWidth", minWidth);
        metrics.put("minHeight", minHeight);
        if (minPixelCount != null) {
            metrics.put("minPixelCount", minPixelCount);
        }
        String reason = passed ? "图片分辨率符合要求"
                : String.format("图片分辨率 %dx%d，低于最低要求 %dx%d", width, height, minWidth, minHeight);
        return CheckerSupport.result(CHECK_CODE, passed,
                passed ? null : "RESOLUTION_TOO_LOW", reason, metrics, Map.of(), startedNanos);
    }
}
