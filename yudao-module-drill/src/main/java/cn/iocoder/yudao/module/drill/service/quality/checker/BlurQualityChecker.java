package cn.iocoder.yudao.module.drill.service.quality.checker;

import cn.iocoder.yudao.module.drill.service.quality.ImageQualityCheckResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.QUALITY_PROFILE_INVALID;

// 通过 Laplacian 方差判断模糊。
@Component
public class BlurQualityChecker implements ImageQualityChecker {

    public static final String CHECK_CODE = "BLUR";
    private static final Set<String> CONFIG_KEYS = Set.of("minLaplacianVariance", "maxAnalysisDimension");
    private static final double DEFAULT_MIN_VARIANCE = 100D;
    private static final int DEFAULT_MAX_ANALYSIS_DIMENSION = 1600;

    @Override
    public String getCheckCode() {
        return CHECK_CODE;
    }

    @Override
    public void validateConfig(Map<String, Object> config) {
        Map<String, Object> value = CheckerSupport.config(config);
        CheckerSupport.requireOnlyKeys(value, CONFIG_KEYS, CHECK_CODE);
        double threshold = CheckerSupport.decimal(value, "minLaplacianVariance", DEFAULT_MIN_VARIANCE);
        if (threshold < 0D) {
            throw exception(QUALITY_PROFILE_INVALID, "minLaplacianVariance 必须大于等于 0");
        }
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
        double threshold = CheckerSupport.decimal(value, "minLaplacianVariance", DEFAULT_MIN_VARIANCE);
        int maxDimension = CheckerSupport.integer(value, "maxAnalysisDimension", DEFAULT_MAX_ANALYSIS_DIMENSION);
        BufferedImage image = CheckerSupport.decode(input, maxDimension);
        if (image.getWidth() < 3 || image.getHeight() < 3) {
            throw exception(QUALITY_PROFILE_INVALID, "BLUR 无法分析宽或高小于 3 像素的图片");
        }

        RunningVariance variance = new RunningVariance();
        for (int y = 1; y < image.getHeight() - 1; y++) {
            for (int x = 1; x < image.getWidth() - 1; x++) {
                double center = grayscale(image.getRGB(x, y));
                double laplacian = grayscale(image.getRGB(x - 1, y))
                        + grayscale(image.getRGB(x + 1, y))
                        + grayscale(image.getRGB(x, y - 1))
                        + grayscale(image.getRGB(x, y + 1))
                        - 4D * center;
                variance.add(laplacian);
            }
        }
        double actualVariance = variance.populationVariance();
        boolean passed = actualVariance >= threshold;
        String reason = passed ? "图片清晰度符合要求"
                : String.format("Laplacian 方差 %.2f，低于清晰度阈值 %.2f", actualVariance, threshold);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("laplacianVariance", actualVariance);
        metrics.put("minLaplacianVariance", threshold);
        metrics.put("sampleCount", variance.count());
        metrics.put("analysisWidth", image.getWidth());
        metrics.put("analysisHeight", image.getHeight());
        return CheckerSupport.result(CHECK_CODE, passed, passed ? null : "IMAGE_BLURRY",
                reason, metrics, Map.of(), startedNanos);
    }

    private double grayscale(int argb) {
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        return 0.2126D * red + 0.7152D * green + 0.0722D * blue;
    }

    private static final class RunningVariance {
        private long count;
        private double mean;
        private double sumOfSquares;

        void add(double value) {
            count++;
            double delta = value - mean;
            mean += delta / count;
            sumOfSquares += delta * (value - mean);
        }

        double populationVariance() {
            return count == 0 ? 0D : sumOfSquares / count;
        }

        long count() {
            return count;
        }
    }
}
