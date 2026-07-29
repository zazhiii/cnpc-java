package cn.iocoder.yudao.module.drill.service.enhance.pipeline;

import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.*;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenCvEnhancePipelineTest {

    private final OpenCvEnhancePipeline pipeline = new OpenCvEnhancePipeline();
    private final OpenCvEnhancePipeline.StepListener listener = new OpenCvEnhancePipeline.StepListener() {
        @Override public void completed(StepConfig step, long durationMs, Map<String, Object> metrics) {}
        @Override public void failed(StepConfig step, long durationMs, RuntimeException exception) {}
    };

    @Test
    void shouldProduceDeterministicLocalEnhancement() throws Exception {
        byte[] source = checkerboard();
        List<StepConfig> steps = List.of(
                step(3, "EXPOSURE_ADJUST", Map.of("clipLimit", 2.0, "tileGridSize", 8)),
                step(4, "REFLECTION_SUPPRESSION", Map.of("strength", 0.25)),
                step(6, "CLARITY_ENHANCE", Map.of("radius", 1.2, "amount", 0.5)));
        OutputSpec output = output(128, 128, true);

        PipelineResult first = pipeline.execute(source, steps, output, null, listener);
        PipelineResult second = pipeline.execute(source, steps, output, null, listener);

        assertEquals("image/jpeg", first.getMimeType());
        assertEquals(128, first.getWidth());
        assertEquals(128, first.getHeight());
        assertEquals(first.getSha256(), second.getSha256());
        assertTrue(((Number) first.getMetrics().get("sharpnessAfter")).doubleValue() > 0);
    }

    @Test
    void shouldRequireManualCropWhenExternalModelIsUnavailable() throws Exception {
        StepConfig crop = step(1, "SUBJECT_CROP", Map.of());
        ManualRequiredException ex = assertThrows(ManualRequiredException.class,
                () -> pipeline.execute(checkerboard(), List.of(crop), output(64, 64, true), null, listener));
        assertEquals("ALGORITHM_VERSION_UNAVAILABLE", ex.getErrorCode());
        assertEquals("PROVIDE_CROP_BOX", ex.getManualAction());
    }

    @Test
    void shouldApplyManualCropAndRotation() throws Exception {
        CropBox box = new CropBox();
        box.setX(16); box.setY(16); box.setWidth(64); box.setHeight(64);
        ManualOverrides manual = new ManualOverrides();
        manual.setCropBox(box); manual.setRotationDegrees(10d);
        PipelineResult result = pipeline.execute(checkerboard(),
                List.of(step(1, "SUBJECT_CROP", Map.of()), step(2, "ORIENTATION_NORMALIZE", Map.of())),
                output(null, null, true), manual, listener);
        assertNotNull(result.getMaskContent());
        assertFalse(Arrays.equals(new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, result.getTransformMatrix()));
    }

    private StepConfig step(int order, String code, Map<String, Object> parameters) {
        StepConfig step = new StepConfig();
        step.setStepOrder(order); step.setCode(code); step.setName(code); step.setEnabled(true);
        step.setAlgorithm("test"); step.setAlgorithmVersion("1.0.0");
        step.setParameters(new LinkedHashMap<>(parameters));
        return step;
    }

    private OutputSpec output(Integer width, Integer height, boolean keep) {
        OutputSpec output = new OutputSpec();
        output.setWidth(width); output.setHeight(height); output.setKeepAspectRatio(keep);
        output.setQuality(92); return output;
    }

    private byte[] checkerboard() throws Exception {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            g.setColor((x + y) % 2 == 0 ? new Color(35, 45, 60) : new Color(220, 205, 180));
            g.fillRect(x * 16, y * 16, 16, 16);
        }
        g.setColor(Color.WHITE); g.fillOval(48, 48, 32, 32); g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
