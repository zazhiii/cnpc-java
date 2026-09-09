package cn.iocoder.yudao.module.drill.service.quality.checker;

import cn.iocoder.yudao.module.drill.service.quality.ImageQualityCheckResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class OverExposureQualityChecker implements ImageQualityChecker {

    public static final String CHECK_CODE = "OVER_EXPOSURE";
    private static final Set<String> CONFIG_KEYS = Set.of(
            "pixelThreshold", "maxRatio", "maxMeanLuminance", "maxAnalysisDimension");
    private static final int DEFAULT_PIXEL_THRESHOLD = 245;
    private static final double DEFAULT_MAX_RATIO = 0.15D;
    private static final double DEFAULT_MAX_MEAN_LUMINANCE = 220D;
    private static final int DEFAULT_MAX_ANALYSIS_DIMENSION = 1600;

    @Override
    public String getCheckCode() {
        return CHECK_CODE;
    }

    @Override
    public void validateConfig(Map<String, Object> config) {
        Map<String, Object> value = CheckerSupport.config(config);
        CheckerSupport.requireOnlyKeys(value, CONFIG_KEYS, CHECK_CODE);
        CheckerSupport.range("pixelThreshold",
                CheckerSupport.integer(value, "pixelThreshold", DEFAULT_PIXEL_THRESHOLD), 0, 255);
        CheckerSupport.range("maxRatio",
                CheckerSupport.decimal(value, "maxRatio", DEFAULT_MAX_RATIO), 0D, 1D);
        CheckerSupport.range("maxMeanLuminance",
                CheckerSupport.decimal(value, "maxMeanLuminance", DEFAULT_MAX_MEAN_LUMINANCE), 0D, 255D);
        CheckerSupport.range("maxAnalysisDimension",
                CheckerSupport.integer(value, "maxAnalysisDimension", DEFAULT_MAX_ANALYSIS_DIMENSION), 64, 4096);
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
        int pixelThreshold = CheckerSupport.integer(value, "pixelThreshold", DEFAULT_PIXEL_THRESHOLD);
        double maxRatio = CheckerSupport.decimal(value, "maxRatio", DEFAULT_MAX_RATIO);
        double maxMean = CheckerSupport.decimal(value, "maxMeanLuminance", DEFAULT_MAX_MEAN_LUMINANCE);
        int maxDimension = CheckerSupport.integer(value, "maxAnalysisDimension", DEFAULT_MAX_ANALYSIS_DIMENSION);
        BufferedImage image = CheckerSupport.decode(input, maxDimension);

        long visiblePixels = 0L;
        long overexposedPixels = 0L;
        double luminanceSum = 0D;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (((argb >>> 24) & 0xff) == 0) {
                    continue;
                }
                int red = (argb >>> 16) & 0xff;
                int green = (argb >>> 8) & 0xff;
                int blue = argb & 0xff;
                double luminance = 0.2126D * red + 0.7152D * green + 0.0722D * blue;
                luminanceSum += luminance;
                visiblePixels++;
                if (luminance >= pixelThreshold) {
                    overexposedPixels++;
                }
            }
        }

        double mean = visiblePixels == 0 ? 0D : luminanceSum / visiblePixels;
        double ratio = visiblePixels == 0 ? 0D : (double) overexposedPixels / visiblePixels;
        boolean ratioPassed = ratio <= maxRatio;
        boolean meanPassed = mean <= maxMean;
        boolean passed = ratioPassed && meanPassed;
        String reasonCode = !ratioPassed ? "OVEREXPOSED_RATIO_EXCEEDED"
                : (!meanPassed ? "MEAN_LUMINANCE_EXCEEDED" : null);
        String reason = passed ? "曝光度符合要求"
                : (!ratioPassed
                ? String.format("过曝像素占比 %.2f%%，超过阈值 %.2f%%", ratio * 100D, maxRatio * 100D)
                : String.format("平均亮度 %.2f，超过阈值 %.2f", mean, maxMean));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("meanLuminance", mean);
        metrics.put("overexposedRatio", ratio);
        metrics.put("overexposedPixels", overexposedPixels);
        metrics.put("analyzedPixels", visiblePixels);
        metrics.put("analysisWidth", image.getWidth());
        metrics.put("analysisHeight", image.getHeight());
        metrics.put("pixelThreshold", pixelThreshold);
        metrics.put("maxRatio", maxRatio);
        metrics.put("maxMeanLuminance", maxMean);
        return CheckerSupport.result(CHECK_CODE, passed, reasonCode, reason, metrics, Map.of(), startedNanos);
    }
}
