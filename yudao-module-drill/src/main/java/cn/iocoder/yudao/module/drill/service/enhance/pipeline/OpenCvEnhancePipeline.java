package cn.iocoder.yudao.module.drill.service.enhance.pipeline;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.*;
import lombok.Data;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;
import org.springframework.stereotype.Component;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.*;
import java.util.List;
import java.util.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * 保守、确定性的 CPU 增强流水线。生成式恢复算法不在此类中实现。
 */
@Component
public class OpenCvEnhancePipeline {

    private final Map<String, EnhanceStepProcessor> processors = new LinkedHashMap<>();

    public OpenCvEnhancePipeline() {
        register(new CropProcessor());
        register(new NormalizeProcessor());
        register(new ExposureProcessor());
        register(new ReflectionProcessor());
        register(new BackgroundProcessor());
        register(new ClarityProcessor());
    }

    public PipelineResult execute(byte[] input, List<StepConfig> steps, OutputSpec output,
                                  ManualOverrides manual, StepListener listener) throws IOException {
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(input));
        if (decoded == null) throw new IOException("IMAGE_DECODE_FAILED");
        Context context = new Context();
        context.setImage(toArgb(decoded));
        context.setManual(manual == null ? new ManualOverrides() : manual);
        context.getMetrics().put("sharpnessBefore", sharpness(context.getImage()));
        for (StepConfig step : steps) {
            if (!Boolean.TRUE.equals(step.getEnabled())) continue;
            EnhanceStepProcessor processor = processors.get(step.getCode());
            if (processor == null) throw new IllegalArgumentException("Unsupported step: " + step.getCode());
            long start = System.currentTimeMillis();
            try {
                processor.process(context, step);
                listener.completed(step, System.currentTimeMillis() - start,
                        new LinkedHashMap<>(context.getLastStepMetrics()));
            } catch (RuntimeException ex) {
                listener.failed(step, System.currentTimeMillis() - start, ex);
                throw ex;
            }
        }
        context.setImage(applyOutputSpec(context.getImage(), output));
        if (context.getMask() != null) {
            context.setMask(resizeMask(context.getMask(), context.getImage().getWidth(), context.getImage().getHeight()));
        }
        context.getMetrics().put("sharpnessAfter", sharpness(context.getImage()));
        boolean hasAlpha = context.getImage().getColorModel().hasAlpha()
                && containsTransparency(context.getImage());
        String format = hasAlpha ? "png" : "jpg";
        byte[] content = encode(context.getImage(), format, output.getQuality() == null ? 92 : output.getQuality());
        PipelineResult result = new PipelineResult();
        result.setContent(content);
        result.setExtension(format);
        result.setMimeType("png".equals(format) ? "image/png" : "image/jpeg");
        result.setWidth(context.getImage().getWidth());
        result.setHeight(context.getImage().getHeight());
        result.setSha256(DigestUtil.sha256Hex(content));
        result.setMetrics(context.getMetrics());
        result.setWarnings(context.getWarnings());
        result.setTransformMatrix(context.getTransformMatrix());
        if (context.getMask() != null) result.setMaskContent(encode(context.getMask(), "png", 100));
        return result;
    }

    private void register(EnhanceStepProcessor processor) {
        processors.put(processor.code(), processor);
    }

    public interface StepListener {
        void completed(StepConfig step, long durationMs, Map<String, Object> metrics);
        void failed(StepConfig step, long durationMs, RuntimeException exception);
    }

    @Data
    public static class Context {
        private BufferedImage image;
        private BufferedImage mask;
        private ManualOverrides manual;
        private Map<String, Object> metrics = new LinkedHashMap<>();
        private Map<String, Object> lastStepMetrics = new LinkedHashMap<>();
        private List<String> warnings = new ArrayList<>();
        private double[] transformMatrix = new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1};

        void stepMetrics(Object... pairs) {
            lastStepMetrics.clear();
            for (int i = 0; i < pairs.length; i += 2) lastStepMetrics.put(String.valueOf(pairs[i]), pairs[i + 1]);
            metrics.putAll(lastStepMetrics);
        }
    }

    private static class CropProcessor implements EnhanceStepProcessor {
        @Override public String code() { return "SUBJECT_CROP"; }
        @Override
        public void process(Context ctx, StepConfig config) {
            CropBox box = ctx.getManual().getCropBox();
            if (box == null) {
                throw new ManualRequiredException("ALGORITHM_VERSION_UNAVAILABLE",
                        "主体分割服务尚未配置，请提供人工裁剪框", "PROVIDE_CROP_BOX");
            }
            BufferedImage src = ctx.getImage();
            if (box.getX() == null || box.getY() == null || box.getWidth() == null || box.getHeight() == null
                    || box.getX() < 0 || box.getY() < 0 || box.getWidth() < 32 || box.getHeight() < 32
                    || box.getX() + box.getWidth() > src.getWidth()
                    || box.getY() + box.getHeight() > src.getHeight()) {
                throw new IllegalArgumentException("INVALID_MANUAL_OVERRIDE: cropBox");
            }
            BufferedImage cropped = new BufferedImage(box.getWidth(), box.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = qualityGraphics(cropped);
            g.drawImage(src, 0, 0, box.getWidth(), box.getHeight(), box.getX(), box.getY(),
                    box.getX() + box.getWidth(), box.getY() + box.getHeight(), null);
            g.dispose();
            BufferedImage mask = new BufferedImage(box.getWidth(), box.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D mg = mask.createGraphics(); mg.setColor(Color.WHITE);
            mg.fillRect(0, 0, mask.getWidth(), mask.getHeight()); mg.dispose();
            ctx.setImage(cropped); ctx.setMask(mask);
            ctx.setTransformMatrix(multiply(new double[]{1, 0, -box.getX(), 0, 1, -box.getY(), 0, 0, 1},
                    ctx.getTransformMatrix()));
            ctx.stepMetrics("cropX", box.getX(), "cropY", box.getY(),
                    "cropWidth", box.getWidth(), "cropHeight", box.getHeight(), "source", "MANUAL");
        }
    }

    private static class NormalizeProcessor implements EnhanceStepProcessor {
        @Override public String code() { return "ORIENTATION_NORMALIZE"; }
        @Override
        public void process(Context ctx, StepConfig config) {
            Double degrees = ctx.getManual().getRotationDegrees();
            if (degrees == null) {
                throw new ManualRequiredException("ALGORITHM_VERSION_UNAVAILABLE",
                        "关键点服务尚未配置，请提供人工旋转角度", "PROVIDE_ROTATION");
            }
            if (!Double.isFinite(degrees) || Math.abs(degrees) > 180) {
                throw new IllegalArgumentException("INVALID_MANUAL_OVERRIDE: rotationDegrees");
            }
            ctx.setImage(rotate(ctx.getImage(), degrees, false));
            if (ctx.getMask() != null) ctx.setMask(rotate(ctx.getMask(), degrees, true));
            double r = Math.toRadians(degrees), cos = Math.cos(r), sin = Math.sin(r);
            ctx.setTransformMatrix(multiply(new double[]{cos, -sin, 0, sin, cos, 0, 0, 0, 1},
                    ctx.getTransformMatrix()));
            ctx.stepMetrics("rotationDegrees", degrees, "source", "MANUAL");
        }
    }

    private static class ExposureProcessor implements EnhanceStepProcessor {
        @Override public String code() { return "EXPOSURE_ADJUST"; }
        @Override
        public void process(Context ctx, StepConfig config) {
            BufferedImage image = ctx.getImage();
            byte[] bgr = toBgr(image);
            Mat src = new Mat(image.getHeight(), image.getWidth(), CV_8UC3);
            src.data().put(bgr);
            Mat lab = new Mat();
            cvtColor(src, lab, COLOR_BGR2Lab);
            MatVector channels = new MatVector();
            split(lab, channels);
            double clip = number(config, "clipLimit", 2.0);
            int grid = (int) number(config, "tileGridSize", 8);
            CLAHE clahe = createCLAHE(clip, new Size(grid, grid));
            clahe.apply(channels.get(0), channels.get(0));
            merge(channels, lab);
            cvtColor(lab, src, COLOR_Lab2BGR);
            src.data().get(bgr);
            BufferedImage adjusted = fromBgr(bgr, image.getWidth(), image.getHeight(), image);
            double mean = meanLuma(adjusted);
            double gamma = Math.max(0.85, Math.min(1.15, Math.log(0.5) / Math.log(Math.max(0.05, mean / 255d))));
            ctx.setImage(applyGamma(adjusted, gamma));
            ctx.stepMetrics("meanLuma", meanLuma(ctx.getImage()), "gamma", gamma,
                    "clipLimit", clip, "tileGridSize", grid);
            clahe.close(); channels.close(); lab.close(); src.close();
        }
    }

    private static class ReflectionProcessor implements EnhanceStepProcessor {
        @Override public String code() { return "REFLECTION_SUPPRESSION"; }
        @Override
        public void process(Context ctx, StepConfig config) {
            BufferedImage src = ctx.getImage();
            BufferedImage illumination = blur(src, 80);
            double strength = number(config, "strength", 0.35);
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            int highlights = 0, shadows = 0, pixels = src.getWidth() * src.getHeight();
            for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
                Color c = new Color(src.getRGB(x, y), true);
                Color il = new Color(illumination.getRGB(x, y), true);
                double lum = luma(c), local = Math.max(20, luma(il));
                float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
                double gain = Math.pow(128d / local, strength);
                boolean highlight = lum > 242 && hsb[1] < 0.22f;
                if (highlight) { gain *= 0.82; highlights++; }
                if (lum < 45) shadows++;
                int r = clamp((int) (c.getRed() * gain));
                int g = clamp((int) (c.getGreen() * gain));
                int b = clamp((int) (c.getBlue() * gain));
                out.setRGB(x, y, new Color(r, g, b, c.getAlpha()).getRGB());
            }
            double highlightRatio = (double) highlights / pixels;
            if (highlightRatio > 0.15) ctx.getWarnings().add("REFLECTION_AREA_TOO_LARGE");
            ctx.setImage(out);
            ctx.stepMetrics("highlightRatio", highlightRatio, "shadowRatio", (double) shadows / pixels,
                    "strength", strength, "retinexScales", List.of(15, 80, 250));
        }
    }

    private static class BackgroundProcessor implements EnhanceStepProcessor {
        @Override public String code() { return "BACKGROUND_WEAKEN"; }
        @Override
        public void process(Context ctx, StepConfig config) {
            if (ctx.getMask() == null) {
                throw new ManualRequiredException("MASK_REQUIRED", "背景弱化需要可信主体掩膜",
                        "PROVIDE_SUBJECT_MASK");
            }
            BufferedImage src = ctx.getImage(), blurred = blur(src, number(config, "blurSigma", 12));
            BufferedImage mask = blur(ctx.getMask(), number(config, "featherRadius", 8));
            double saturation = number(config, "saturationFactor", 0.6);
            double brightness = number(config, "brightnessFactor", 0.8);
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
                Color fg = new Color(src.getRGB(x, y), true);
                Color bg = new Color(blurred.getRGB(x, y), true);
                float[] hsb = Color.RGBtoHSB(bg.getRed(), bg.getGreen(), bg.getBlue(), null);
                Color weak = Color.getHSBColor(hsb[0], (float) (hsb[1] * saturation),
                        (float) Math.min(1, hsb[2] * brightness));
                double a = new Color(mask.getRGB(x, y)).getRed() / 255d;
                int r = clamp((int) (weak.getRed() * (1 - a) + fg.getRed() * a));
                int g = clamp((int) (weak.getGreen() * (1 - a) + fg.getGreen() * a));
                int b = clamp((int) (weak.getBlue() * (1 - a) + fg.getBlue() * a));
                out.setRGB(x, y, new Color(r, g, b, fg.getAlpha()).getRGB());
            }
            ctx.setImage(out);
            ctx.stepMetrics("saturationFactor", saturation, "brightnessFactor", brightness,
                    "blurSigma", number(config, "blurSigma", 12));
        }
    }

    private static class ClarityProcessor implements EnhanceStepProcessor {
        @Override public String code() { return "CLARITY_ENHANCE"; }
        @Override
        public void process(Context ctx, StepConfig config) {
            double before = sharpness(ctx.getImage());
            if (before < 8) throw new ManualRequiredException("SEVERE_BLUR",
                    "图像严重模糊，禁止强行恢复", "REPLACE_SOURCE_IMAGE");
            double radius = number(config, "radius", 1.2);
            double amount = number(config, "amount", 0.6);
            BufferedImage src = ctx.getImage(), smooth = blur(src, radius);
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
                Color a = new Color(src.getRGB(x, y), true), b = new Color(smooth.getRGB(x, y), true);
                int r = sharpen(a.getRed(), b.getRed(), amount);
                int g = sharpen(a.getGreen(), b.getGreen(), amount);
                int bl = sharpen(a.getBlue(), b.getBlue(), amount);
                out.setRGB(x, y, new Color(r, g, bl, a.getAlpha()).getRGB());
            }
            ctx.setImage(out);
            ctx.stepMetrics("sharpnessBeforeStep", before, "sharpnessAfterStep", sharpness(out),
                    "radius", radius, "amount", amount);
        }
    }

    private static int sharpen(int src, int blur, double amount) {
        int delta = src - blur;
        return Math.abs(delta) < 3 ? src : clamp((int) (src + amount * delta));
    }

    private static double number(StepConfig config, String key, double fallback) {
        Object value = config.getParameters() == null ? null : config.getParameters().get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static BufferedImage rotate(BufferedImage src, double degrees, boolean mask) {
        double r = Math.toRadians(degrees), sin = Math.abs(Math.sin(r)), cos = Math.abs(Math.cos(r));
        int w = (int) Math.floor(src.getWidth() * cos + src.getHeight() * sin);
        int h = (int) Math.floor(src.getHeight() * cos + src.getWidth() * sin);
        BufferedImage out = new BufferedImage(w, h, mask ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = qualityGraphics(out);
        g.translate((w - src.getWidth()) / 2d, (h - src.getHeight()) / 2d);
        g.rotate(r, src.getWidth() / 2d, src.getHeight() / 2d);
        g.drawRenderedImage(src, null); g.dispose();
        return out;
    }

    private static BufferedImage blur(BufferedImage src, double radius) {
        int r = Math.max(1, Math.min(60, (int) Math.ceil(radius)));
        BufferedImage small = new BufferedImage(Math.max(1, src.getWidth() / r),
                Math.max(1, src.getHeight() / r), src.getType() == BufferedImage.TYPE_BYTE_GRAY
                ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = qualityGraphics(small); g.drawImage(src, 0, 0, small.getWidth(), small.getHeight(), null); g.dispose();
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), small.getType());
        g = qualityGraphics(out); g.drawImage(small, 0, 0, out.getWidth(), out.getHeight(), null); g.dispose();
        return out;
    }

    private static BufferedImage applyOutputSpec(BufferedImage src, OutputSpec output) {
        if (Boolean.TRUE.equals(output.getKeepAspectRatio()) || output.getWidth() == null || output.getHeight() == null) return src;
        BufferedImage out = new BufferedImage(output.getWidth(), output.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = qualityGraphics(out); g.setColor(Color.BLACK); g.fillRect(0, 0, out.getWidth(), out.getHeight());
        double scale = Math.min((double) out.getWidth() / src.getWidth(), (double) out.getHeight() / src.getHeight());
        int w = (int) Math.round(src.getWidth() * scale), h = (int) Math.round(src.getHeight() * scale);
        g.drawImage(src, (out.getWidth() - w) / 2, (out.getHeight() - h) / 2, w, h, null); g.dispose();
        return out;
    }

    private static BufferedImage resizeMask(BufferedImage src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) return src;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = qualityGraphics(out); g.drawImage(src, 0, 0, w, h, null); g.dispose(); return out;
    }

    private static Graphics2D qualityGraphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static BufferedImage toArgb(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics(); g.drawImage(src, 0, 0, null); g.dispose(); return out;
    }

    private static byte[] toBgr(BufferedImage image) {
        byte[] out = new byte[image.getWidth() * image.getHeight() * 3]; int i = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int rgb = image.getRGB(x, y); out[i++] = (byte) rgb; out[i++] = (byte) (rgb >> 8); out[i++] = (byte) (rgb >> 16);
        }
        return out;
    }

    private static BufferedImage fromBgr(byte[] bgr, int w, int h, BufferedImage alphaSource) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); int i = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int b = bgr[i++] & 255, g = bgr[i++] & 255, r = bgr[i++] & 255;
            int alpha = (alphaSource.getRGB(x, y) >>> 24) & 255;
            out.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
        }
        return out;
    }

    private static BufferedImage applyGamma(BufferedImage src, double gamma) {
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) table[i] = clamp((int) (255 * Math.pow(i / 255d, gamma)));
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
            Color c = new Color(src.getRGB(x, y), true);
            out.setRGB(x, y, new Color(table[c.getRed()], table[c.getGreen()], table[c.getBlue()], c.getAlpha()).getRGB());
        }
        return out;
    }

    private static double meanLuma(BufferedImage image) {
        double sum = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            sum += luma(new Color(image.getRGB(x, y), true));
        return sum / (image.getWidth() * image.getHeight());
    }

    private static double sharpness(BufferedImage image) {
        if (image.getWidth() < 3 || image.getHeight() < 3) return 0;
        double sum = 0, sumSq = 0; long n = 0;
        for (int y = 1; y < image.getHeight() - 1; y++) for (int x = 1; x < image.getWidth() - 1; x++) {
            double v = 4 * luma(new Color(image.getRGB(x, y)))
                    - luma(new Color(image.getRGB(x - 1, y))) - luma(new Color(image.getRGB(x + 1, y)))
                    - luma(new Color(image.getRGB(x, y - 1))) - luma(new Color(image.getRGB(x, y + 1)));
            sum += v; sumSq += v * v; n++;
        }
        double mean = sum / n; return sumSq / n - mean * mean;
    }

    private static double luma(Color c) { return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue(); }
    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }

    private static boolean containsTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) return false;
        for (int y = 0; y < image.getHeight(); y += Math.max(1, image.getHeight() / 100))
            for (int x = 0; x < image.getWidth(); x += Math.max(1, image.getWidth() / 100))
                if (((image.getRGB(x, y) >>> 24) & 255) < 255) return true;
        return false;
    }

    private static byte[] encode(BufferedImage image, String format, int quality) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if ("jpg".equals(format)) {
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = rgb.createGraphics(); g.setColor(Color.BLACK); g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null); g.dispose();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(stream);
                ImageWriteParam p = writer.getDefaultWriteParam(); p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                p.setCompressionQuality(Math.max(0.7f, Math.min(1f, quality / 100f)));
                writer.write(null, new IIOImage(rgb, null, null), p);
            } finally { writer.dispose(); }
        } else ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static double[] multiply(double[] a, double[] b) {
        double[] r = new double[9];
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++)
            for (int k = 0; k < 3; k++) r[row * 3 + col] += a[row * 3 + k] * b[k * 3 + col];
        return r;
    }
}
