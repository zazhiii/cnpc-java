package cn.iocoder.yudao.module.drill.service.collection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.drill.config.E11QualityGateProperties;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.service.image.ImageFileValidator;
import cn.iocoder.yudao.module.drill.service.quality.checker.E11ModelQualityChecker;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_FILE_EMPTY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppCollectionPhotoQualityServiceTest {

    private final ImageFileValidator validator = mock(ImageFileValidator.class);
    private final E11ModelQualityChecker modelChecker = mock(E11ModelQualityChecker.class);
    private final E11QualityGateProperties properties = new E11QualityGateProperties();
    private final AppCollectionPhotoQualityService service = new AppCollectionPhotoQualityService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "imageFileValidator", validator);
        ReflectionTestUtils.setField(service, "modelChecker", modelChecker);
        ReflectionTestUtils.setField(service, "properties", properties);
    }

    @Test
    void checkShouldCallE11OnceWithoutQualityProfileTable() {
        byte[] content = new byte[]{1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "cutter.jpg", "image/jpeg", content);
        when(validator.validate(any(byte[].class), eq("cutter.jpg"), eq(30L * 1024 * 1024),
                eq(ImageFileValidator.MAX_PIXEL_COUNT)))
                .thenReturn(new ImageFileValidator.ImageInfo(1600, 1200, "image/jpeg"));
        ImageQualityModelResult model = new ImageQualityModelResult();
        model.setPassed(true);
        model.setAcceptedCount(2);
        model.setModelVersion("E11");
        when(modelChecker.executeModel(any(QualityImageInput.class), anyMap())).thenReturn(model);

        AppCollectionVO.PhotoQualityResp result = service.check(file);

        assertTrue(result.getPassed());
        assertEquals(2, result.getAcceptedCount());
        verify(modelChecker, times(1)).executeModel(argThat(input ->
                input.assetId() == null && input.width() == 1600 && input.height() == 1200
                        && input.mimeType().equals("image/jpeg") && input.content() == content),
                eq(Map.of("confidence", 0.75D, "minDetections", 1)));
    }

    @Test
    void checkShouldRejectEmptyFileBeforeModelCall() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        ServiceException error = assertThrows(ServiceException.class, () -> service.check(file));

        assertEquals(IMAGE_FILE_EMPTY.getCode(), error.getCode());
        verifyNoInteractions(modelChecker);
    }

    @Test
    void checkShouldUseConfiguredAppThresholds() {
        MockMultipartFile file = new MockMultipartFile("file", "cutter.jpg", "image/jpeg", new byte[]{1});
        when(validator.validate(any(byte[].class), eq("cutter.jpg"), anyLong(), anyLong()))
                .thenReturn(new ImageFileValidator.ImageInfo(100, 100, "image/jpeg"));
        properties.setAppConfidence(0.85D);
        properties.setAppMinDetections(2);
        ImageQualityModelResult model = new ImageQualityModelResult();
        model.setPassed(false);
        when(modelChecker.executeModel(any(QualityImageInput.class), anyMap())).thenReturn(model);

        AppCollectionVO.PhotoQualityResp result = service.check(file);

        assertFalse(result.getPassed());
        verify(modelChecker).executeModel(any(QualityImageInput.class),
                eq(Map.of("confidence", 0.85D, "minDetections", 2)));
    }
}
