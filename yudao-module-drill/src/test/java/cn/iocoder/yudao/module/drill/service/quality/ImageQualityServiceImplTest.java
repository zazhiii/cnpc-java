package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityCheckReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityCheckRespVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.ImageQualityRecordDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileDO;
import cn.iocoder.yudao.module.drill.service.image.ImageAssetService;
import cn.iocoder.yudao.module.drill.service.image.ImageFileValidator;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.E11_QUALITY_GATE_TIMEOUT;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.QUALITY_CHECK_REQUEST_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageQualityServiceImplTest {

    private final QualityProfileService profileService = mock(QualityProfileService.class);
    private final ImageQualityModelRegistry registry = mock(ImageQualityModelRegistry.class);
    private final ImageQualityModelExecutor executor = mock(ImageQualityModelExecutor.class);
    private final ImageQualityRecordService recordService = mock(ImageQualityRecordService.class);
    private final ImageAssetService assetService = mock(ImageAssetService.class);
    private final ImageFileValidator validator = mock(ImageFileValidator.class);
    private final ImageQualityServiceImpl service = new ImageQualityServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "profileService", profileService);
        ReflectionTestUtils.setField(service, "modelRegistry", registry);
        ReflectionTestUtils.setField(service, "imageQualityRecordService", recordService);
        ReflectionTestUtils.setField(service, "imageAssetService", assetService);
        ReflectionTestUtils.setField(service, "imageFileValidator", validator);
    }

    @Test
    void checkShouldRequireExactlyOneAssetSelector() {
        ImageQualityCheckReqVO req = new ImageQualityCheckReqVO();
        ServiceException ex = assertThrows(ServiceException.class, () -> service.check(req));
        assertEquals(QUALITY_CHECK_REQUEST_INVALID.getCode(), ex.getCode());

        req.setAssetId(1L);
        req.setAssetIds(List.of(2L));
        assertThrows(ServiceException.class, () -> service.check(req));
    }

    @Test
    void checkShouldRejectDuplicateAndOversizedAssetLists() {
        ImageQualityCheckReqVO duplicates = new ImageQualityCheckReqVO();
        duplicates.setAssetIds(List.of(1L, 1L));
        assertThrows(ServiceException.class, () -> service.check(duplicates));

        ImageQualityCheckReqVO tooMany = new ImageQualityCheckReqVO();
        tooMany.setAssetIds(java.util.stream.LongStream.rangeClosed(1, 21).boxed().toList());
        assertThrows(ServiceException.class, () -> service.check(tooMany));
    }

    @Test
    void checkShouldContinueAfterOneAssetFails() {
        ImageQualityCheckReqVO req = new ImageQualityCheckReqVO();
        req.setAssetIds(List.of(11L, 12L));
        req.setConfidence(0.8);

        QualityProfileDO profile = new QualityProfileDO();
        profile.setId(5L);
        profile.setProfileCode("E11_STANDARD");
        profile.setVersion("1.0.0");
        profile.setModelCode("E11");
        profile.setModelConfigJson("{\"confidence\":0.75,\"minDetections\":1}");
        when(profileService.getDefaultEnabled()).thenReturn(profile);
        when(registry.getRequired("E11")).thenReturn(executor);

        ImageAssetDO first = asset(11L);
        ImageAssetDO second = asset(12L);
        when(assetService.getAssets(List.of(11L, 12L))).thenReturn(List.of(first, second));
        when(assetService.getAssetContent(anyLong())).thenReturn(new byte[]{1, 2, 3});
        when(validator.validate(any(byte[].class), anyString()))
                .thenReturn(new ImageFileValidator.ImageInfo(100, 80, "image/png"));
        when(executor.execute(any(QualityImageInput.class), anyMap())).thenAnswer(invocation -> {
            QualityImageInput input = invocation.getArgument(0);
            if (input.assetId() == 12L) {
                throw exception(E11_QUALITY_GATE_TIMEOUT, 1000);
            }
            ImageQualityModelResult result = new ImageQualityModelResult();
            result.setPassed(false);
            result.setReason("rejected");
            result.setModelVersion("E11");
            result.setThreshold(0.8);
            result.setMinDetections(1);
            result.setDurationMs(30L);
            return result;
        });
        when(recordService.saveSuccess(eq(11L), eq(profile), anyMap(), any(), any(LocalDateTime.class)))
                .thenReturn(record(101L, 11L, "SUCCESS"));
        when(recordService.saveFailure(eq(12L), eq(profile), anyMap(), anyInt(), anyString(), anyLong(),
                any(LocalDateTime.class))).thenReturn(record(102L, 12L, "FAILED"));

        ImageQualityCheckRespVO response = service.check(req);

        assertEquals(2, response.getTotalCount());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
        assertFalse(response.getItems().get(0).getPassed());
        assertEquals(0.8, response.getItems().get(0).getThreshold());
        assertEquals("FAILED", response.getItems().get(1).getExecutionStatus());
        assertEquals(E11_QUALITY_GATE_TIMEOUT.getCode(), response.getItems().get(1).getErrorCode());
        verify(executor, times(2)).execute(any(), argThat(config -> config.get("confidence").equals(0.8)));
    }

    private ImageAssetDO asset(Long id) {
        ImageAssetDO asset = new ImageAssetDO();
        asset.setId(id);
        asset.setOriginalName(id + ".png");
        return asset;
    }

    private ImageQualityRecordDO record(Long id, Long assetId, String status) {
        ImageQualityRecordDO record = new ImageQualityRecordDO();
        record.setId(id);
        record.setAssetId(assetId);
        record.setExecutionStatus(status);
        return record;
    }

}
