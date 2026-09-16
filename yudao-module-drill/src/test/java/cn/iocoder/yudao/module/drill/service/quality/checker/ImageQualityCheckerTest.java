package cn.iocoder.yudao.module.drill.service.quality.checker;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.drill.config.E11QualityGateProperties;
import cn.iocoder.yudao.module.drill.service.quality.E11QualityGateHttpClient;
import cn.iocoder.yudao.module.drill.service.quality.ImageQualityCheckResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageQualityCheckerTest {

    @Test
    void e11ShouldExecuteAndMapModelResult() {
        E11QualityGateHttpClient client = mock(E11QualityGateHttpClient.class);
        E11ModelQualityChecker checker = new E11ModelQualityChecker(
                new E11QualityGateProperties(), client, new ObjectMapper());
        Map<String, Object> config = Map.of("confidence", 0.75D, "minDetections", 1);
        QualityImageInput input = input(solidImage(8, 8, Color.BLACK), 8, 8);
        when(client.predict(eq(input), eq(0.75D), eq(1), anyString()))
                .thenReturn(new E11QualityGateHttpClient.Response(200, rejectedReport()));

        ImageQualityCheckResult result = checker.check(input, config);

        assertEquals("E11_MODEL", result.getCheckCode());
        assertFalse(result.getPassed());
        assertEquals("MODEL_NO_DETECTION", result.getReasonCode());
        assertEquals("E11", result.getMetrics().get("modelVersion"));
        verify(client).predict(eq(input), eq(0.75D), eq(1), anyString());
    }

    @Test
    void overExposureShouldRejectBrightImageAndAcceptDarkImage() {
        OverExposureQualityChecker checker = new OverExposureQualityChecker();
        Map<String, Object> config = Map.of(
                "pixelThreshold", 245,
                "maxRatio", 0.1D,
                "maxMeanLuminance", 220D);

        ImageQualityCheckResult bright = checker.check(
                input(solidImage(10, 10, Color.WHITE), 10, 10), config);
        ImageQualityCheckResult dark = checker.check(
                input(solidImage(10, 10, Color.DARK_GRAY), 10, 10), config);

        assertFalse(bright.getPassed());
        assertEquals("OVEREXPOSED_RATIO_EXCEEDED", bright.getReasonCode());
        assertEquals(1D, (Double) bright.getMetrics().get("overexposedRatio"), 0.0001D);
        assertTrue(dark.getPassed());
    }

    @Test
    void minResolutionShouldCheckDimensionsAndValidateRequiredConfig() {
        MinResolutionQualityChecker checker = new MinResolutionQualityChecker();
        Map<String, Object> config = Map.of("minWidth", 1920, "minHeight", 1080);

        assertTrue(checker.check(new QualityImageInput(1L, new byte[]{1}, "a.png",
                "image/png", 1920, 1080), config).getPassed());
        ImageQualityCheckResult rejected = checker.check(new QualityImageInput(2L, new byte[]{1}, "b.png",
                "image/png", 1280, 720), config);
        assertFalse(rejected.getPassed());
        assertEquals("RESOLUTION_TOO_LOW", rejected.getReasonCode());
        assertThrows(ServiceException.class,
                () -> checker.validateConfig(Map.of("minWidth", 1920)));
    }

    @Test
    void blurShouldRejectFlatImageAndAcceptHighContrastEdges() {
        BlurQualityChecker checker = new BlurQualityChecker();
        Map<String, Object> config = Map.of("minLaplacianVariance", 100D);
        BufferedImage checkerboard = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < checkerboard.getHeight(); y++) {
            for (int x = 0; x < checkerboard.getWidth(); x++) {
                checkerboard.setRGB(x, y, ((x + y) & 1) == 0 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }

        ImageQualityCheckResult flat = checker.check(
                input(solidImage(20, 20, Color.GRAY), 20, 20), config);
        ImageQualityCheckResult sharp = checker.check(input(checkerboard, 20, 20), config);

        assertFalse(flat.getPassed());
        assertEquals("IMAGE_BLURRY", flat.getReasonCode());
        assertTrue(sharp.getPassed());
    }

    @Test
    void checkersShouldRejectUnknownConfigKeys() {
        assertThrows(ServiceException.class,
                () -> new OverExposureQualityChecker().validateConfig(Map.of("maxRaito", 0.1D)));
        assertThrows(ServiceException.class,
                () -> new BlurQualityChecker().validateConfig(Map.of("threshold", 100D)));
    }

    private QualityImageInput input(BufferedImage image, int width, int height) {
        return new QualityImageInput(1L, encode(image), "test.png", "image/png", width, height);
    }

    private BufferedImage solidImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        return image;
    }

    private byte[] encode(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String rejectedReport() {
        return """
                {
                  "schema_version":"1.0",
                  "model_version":"E11",
                  "task":"cutting_tooth_instance_segmentation_quality_gate",
                  "passed":false,
                  "reason":"insufficient_detections_above_threshold",
                  "threshold":0.75,
                  "min_detections":1,
                  "accepted_count":0,
                  "candidate_count":0,
                  "rejection_counts":{"below_threshold":0,"invalid_polygon":0,"unexpected_label":0},
                  "confidence_summary":{"minimum":null,"maximum":null,"mean":null},
                  "image":{"width":8,"height":8},
                  "detections":[],
                  "inference_speed_ms":{}
                }
                """;
    }
}
