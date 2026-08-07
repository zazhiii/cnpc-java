package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.QualityProfileVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileDO;
import cn.iocoder.yudao.module.drill.dal.mysql.quality.ImageQualityRecordMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.quality.QualityProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.QUALITY_DEFAULT_PROFILE_PROTECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QualityProfileServiceImplTest {

    private final QualityProfileMapper profileMapper = mock(QualityProfileMapper.class);
    private final ImageQualityRecordMapper recordMapper = mock(ImageQualityRecordMapper.class);
    private final ImageQualityModelRegistry registry = mock(ImageQualityModelRegistry.class);
    private final ImageQualityModelExecutor executor = mock(ImageQualityModelExecutor.class);
    private final QualityProfileServiceImpl service = new QualityProfileServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "profileMapper", profileMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "modelRegistry", registry);
        when(registry.getRequired("E11")).thenReturn(executor);
    }

    @Test
    void createShouldNormalizeVersionAndCreateDraft() {
        QualityProfileVO.SaveReq req = new QualityProfileVO.SaveReq();
        req.setProfileCode(" E11_STANDARD ");
        req.setName("标准方案");
        req.setVersion("v1.2.3");
        req.setModelCode("e11");
        req.setModelConfig(Map.of("confidence", 0.75, "minDetections", 1));
        doAnswer(invocation -> {
            QualityProfileDO profile = invocation.getArgument(0);
            profile.setId(99L);
            assertEquals("DRAFT", profile.getStatus());
            return 1;
        }).when(profileMapper).insert(any(QualityProfileDO.class));

        assertEquals(99L, service.create(req));
        assertEquals("E11_STANDARD", req.getProfileCode());
        assertEquals("1.2.3", req.getVersion());
        assertEquals("E11", req.getModelCode());
        verify(executor).validateConfig(req.getModelConfig());
    }

    @Test
    void disableShouldRejectDefaultProfile() {
        QualityProfileDO profile = new QualityProfileDO();
        profile.setId(7L);
        profile.setStatus("ENABLED");
        profile.setDefaultProfile(true);
        when(profileMapper.selectById(7L)).thenReturn(profile);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.disable(7L));
        assertEquals(QUALITY_DEFAULT_PROFILE_PROTECTED.getCode(), ex.getCode());
        verify(profileMapper, never()).updateById(any(QualityProfileDO.class));
    }

    @Test
    void deleteShouldRejectProfileWithQualityRecords() {
        QualityProfileDO profile = new QualityProfileDO();
        profile.setId(8L);
        profile.setStatus("DISABLED");
        profile.setDefaultProfile(false);
        when(profileMapper.selectById(8L)).thenReturn(profile);
        when(recordMapper.selectCountByProfileId(8L)).thenReturn(1L);

        assertThrows(ServiceException.class, () -> service.delete(8L));
        verify(profileMapper, never()).deleteById(8L);
    }

    @Test
    void setDefaultShouldClearPreviousDefault() {
        QualityProfileDO profile = new QualityProfileDO();
        profile.setId(9L);
        profile.setStatus("ENABLED");
        profile.setDefaultProfile(false);
        when(profileMapper.selectById(9L)).thenReturn(profile);

        service.setDefault(9L);

        verify(profileMapper).clearDefault();
        verify(profileMapper).updateById(argThat((QualityProfileDO update) -> update.getId().equals(9L)
                && Boolean.TRUE.equals(update.getDefaultProfile())));
    }

}
