package cn.iocoder.yudao.module.drill.service.image;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageFileValidatorTest {

    private final ImageFileValidator validator = new ImageFileValidator();

    @Test
    void shouldDetectPngContentBehindJpgExtension() throws Exception {
        byte[] png = image("png", 12, 8);
        ImageFileValidator.ImageInfo info = validator.validate(png, "incorrect.jpg", 1024 * 1024, 1_000);
        assertEquals("image/png", info.getMimeType());
        assertEquals(12, info.getWidth());
        assertEquals(8, info.getHeight());
    }

    @Test
    void shouldDetectJpegContent() throws Exception {
        byte[] jpeg = image("jpg", 9, 7);
        ImageFileValidator.ImageInfo info = validator.validate(jpeg, "photo.jpeg", 1024 * 1024, 1_000);
        assertEquals("image/jpeg", info.getMimeType());
    }

    @Test
    void shouldRejectFileSizeAndPixelLimits() throws Exception {
        byte[] png = image("png", 10, 10);
        assertThrows(ServiceException.class,
                () -> validator.validate(png, "large.png", png.length - 1L, 1_000));
        assertThrows(ServiceException.class,
                () -> validator.validate(png, "pixels.png", 1024 * 1024, 99));
    }

    @Test
    void shouldRejectDamagedImage() {
        assertThrows(ServiceException.class,
                () -> validator.validate(new byte[]{1, 2, 3, 4}, "broken.jpg", 1024, 1_000));
    }

    private byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
