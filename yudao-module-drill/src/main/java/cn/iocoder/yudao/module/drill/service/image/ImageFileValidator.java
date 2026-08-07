package cn.iocoder.yudao.module.drill.service.image;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;

/**
 * 图片安全校验器。只读取图片头部尺寸，不进行整图像素解码。
 */
@Component
public class ImageFileValidator {

    public static final long MAX_FILE_SIZE = 30L * 1024 * 1024;
    public static final long MAX_PIXEL_COUNT = 50_000_000L;
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "jpg", "png", "webp");

    public ImageInfo validate(byte[] content, String originalName) {
        return validate(content, originalName, MAX_FILE_SIZE, MAX_PIXEL_COUNT);
    }

    public ImageInfo validate(byte[] content, String originalName, long maxFileSize, long maxPixelCount) {
        if (content == null || content.length == 0) {
            throw exception(IMAGE_FILE_EMPTY);
        }
        if (content.length > maxFileSize) {
            throw exception(IMAGE_FILE_TOO_LARGE, maxFileSize / 1024 / 1024);
        }
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (imageInput == null) {
                throw exception(IMAGE_DECODE_FAILED);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                String extension = StrUtil.subAfter(originalName, '.', true);
                throw exception(IMAGE_FORMAT_UNSUPPORTED, extension);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!ALLOWED_FORMATS.contains(format)) {
                    throw exception(IMAGE_FORMAT_UNSUPPORTED, format);
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw exception(IMAGE_DECODE_FAILED);
                }
                if ((long) width * height > maxPixelCount) {
                    throw exception(IMAGE_DIMENSION_EXCEEDED, maxPixelCount);
                }
                return new ImageInfo(width, height, resolveMimeType(format));
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw exception(IMAGE_DECODE_FAILED);
        }
    }

    private String resolveMimeType(String format) {
        return switch (format) {
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    @Data
    @AllArgsConstructor
    public static class ImageInfo {
        private int width;
        private int height;
        private String mimeType;
    }

}
