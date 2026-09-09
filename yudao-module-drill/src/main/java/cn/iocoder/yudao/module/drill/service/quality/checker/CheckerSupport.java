package cn.iocoder.yudao.module.drill.service.quality.checker;

import cn.iocoder.yudao.module.drill.service.quality.ImageQualityCheckResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_DECODE_FAILED;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.QUALITY_PROFILE_INVALID;

final class CheckerSupport {

    private CheckerSupport() {
    }

    static Map<String, Object> config(Map<String, Object> config) {
        return config == null ? Map.of() : config;
    }

    static void requireOnlyKeys(Map<String, Object> config, Set<String> allowedKeys, String checkCode) {
        for (String key : config(config).keySet()) {
            if (!allowedKeys.contains(key)) {
                throw exception(QUALITY_PROFILE_INVALID, checkCode + " 不支持参数：" + key);
            }
        }
    }

    static int integer(Map<String, Object> config, String key, Integer defaultValue) {
        Object value = config(config).get(key);
        if (value == null && defaultValue != null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)) {
            throw exception(QUALITY_PROFILE_INVALID, key + " 必须是整数");
        }
        double raw = number.doubleValue();
        if (!Double.isFinite(raw) || raw != Math.rint(raw)
                || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
            throw exception(QUALITY_PROFILE_INVALID, key + " 必须是整数");
        }
        return number.intValue();
    }

    static long longInteger(Map<String, Object> config, String key, Long defaultValue) {
        Object value = config(config).get(key);
        if (value == null && defaultValue != null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)) {
            throw exception(QUALITY_PROFILE_INVALID, key + " 必须是整数");
        }
        double raw = number.doubleValue();
        if (!Double.isFinite(raw) || raw != Math.rint(raw)) {
            throw exception(QUALITY_PROFILE_INVALID, key + " 必须是整数");
        }
        return number.longValue();
    }

    static double decimal(Map<String, Object> config, String key, Double defaultValue) {
        Object value = config(config).get(key);
        if (value == null && defaultValue != null) {
            return defaultValue;
        }
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw exception(QUALITY_PROFILE_INVALID, key + " 必须是有限数值");
        }
        return number.doubleValue();
    }

    static void range(String name, double value, double minimum, double maximum) {
        if (value < minimum || value > maximum) {
            throw exception(QUALITY_PROFILE_INVALID,
                    name + " 必须在 [" + minimum + "," + maximum + "] 范围内");
        }
    }

    static BufferedImage decode(QualityImageInput input, int maxAnalysisDimension) {
        if (input == null || input.content() == null || input.content().length == 0) {
            throw exception(IMAGE_DECODE_FAILED);
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input.content()))) {
            if (stream == null) {
                throw exception(IMAGE_DECODE_FAILED);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw exception(IMAGE_DECODE_FAILED);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int largestDimension = Math.max(width, height);
                int subsampling = Math.max(1, (int) Math.ceil((double) largestDimension / maxAnalysisDimension));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) {
                    throw exception(IMAGE_DECODE_FAILED);
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof cn.iocoder.yudao.framework.common.exception.ServiceException serviceException) {
                throw serviceException;
            }
            throw exception(IMAGE_DECODE_FAILED);
        }
    }

    static ImageQualityCheckResult result(String checkCode, boolean passed, String reasonCode,
                                          String reason, Map<String, Object> metrics,
                                          Map<String, Object> details, long startedNanos) {
        ImageQualityCheckResult result = new ImageQualityCheckResult();
        result.setCheckCode(checkCode);
        result.setPassed(passed);
        result.setReasonCode(reasonCode);
        result.setReason(reason);
        result.setMetrics(metrics == null ? Map.of() : new LinkedHashMap<>(metrics));
        result.setDetails(details == null ? Map.of() : new LinkedHashMap<>(details));
        result.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        return result;
    }
}
