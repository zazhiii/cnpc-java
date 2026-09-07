package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.QualityProfileVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileItemDO;
import cn.iocoder.yudao.module.drill.dal.mysql.quality.ImageQualityRecordMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.quality.QualityProfileItemMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.quality.QualityProfileMapper;
import cn.iocoder.yudao.module.drill.service.quality.checker.ImageQualityChecker;
import cn.iocoder.yudao.module.drill.service.quality.checker.ImageQualityCheckerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.QUALITY_DEFAULT_PROFILE_PROTECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QualityProfileServiceImplTest {

    private final QualityProfileMapper profileMapper = mock(QualityProfileMapper.class);
    private final ImageQualityRecordMapper recordMapper = mock(ImageQualityRecordMapper.class);
    private final QualityProfileItemMapper profileItemMapper = mock(QualityProfileItemMapper.class);
    private final ImageQualityCheckerRegistry checkerRegistry = mock(ImageQualityCheckerRegistry.class);
    private final ImageQualityChecker e11Checker = mock(ImageQualityChecker.class);
    private final ImageQualityChecker exposureChecker = mock(ImageQualityChecker.class);
    private final QualityProfileServiceImpl service = new QualityProfileServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "profileMapper", profileMapper);
        ReflectionTestUtils.setField(service, "recordMapper", recordMapper);
        ReflectionTestUtils.setField(service, "profileItemMapper", profileItemMapper);
        ReflectionTestUtils.setField(service, "checkerRegistry", checkerRegistry);
        when(checkerRegistry.getRequired("E11_MODEL")).thenReturn(e11Checker);
        when(checkerRegistry.getRequired("OVER_EXPOSURE")).thenReturn(exposureChecker);
    }

    @Test
    void createShouldNormalizeVersionAndCreateDraft() {
        QualityProfileVO.SaveReq req = new QualityProfileVO.SaveReq();
        req.setProfileCode(" E11_STANDARD ");
        req.setName("标准方案");
        req.setVersion("v1.2.3");
        QualityProfileVO.CheckItem exposure = check("over_exposure", Map.of("maxRatio", 0.15), 20);
        QualityProfileVO.CheckItem e11 = check("e11_model", Map.of("confidence", 0.75), 10);
        req.setChecks(List.of(exposure, e11));
        doAnswer(invocation -> {
            QualityProfileDO profile = invocation.getArgument(0);
            profile.setId(99L);
            assertEquals("DRAFT", profile.getStatus());
            assertEquals(true, profile.getShortCircuit());
            return 1;
        }).when(profileMapper).insert(any(QualityProfileDO.class));

        assertEquals(99L, service.create(req));
        assertEquals("E11_STANDARD", req.getProfileCode());
        assertEquals("1.2.3", req.getVersion());
        assertEquals(List.of("E11_MODEL", "OVER_EXPOSURE"),
                req.getChecks().stream().map(QualityProfileVO.CheckItem::getCheckCode).toList());
        verify(e11Checker).validateConfig(e11.getConfig());
        verify(exposureChecker).validateConfig(exposure.getConfig());
        verify(profileItemMapper).insertBatch(argThat(items -> items.size() == 2
                && items.stream().allMatch(item -> item.getProfileId().equals(99L))));
    }

    @Test
    void createShouldRejectDuplicateChecksBeforeInsert() {
        QualityProfileVO.SaveReq req = new QualityProfileVO.SaveReq();
        req.setProfileCode("DUPLICATE_CHECK");
        req.setName("重复条件方案");
        req.setVersion("1.0.0");
        req.setChecks(List.of(
                check("blur", Map.of("threshold", 100), 10),
                check(" BLUR ", Map.of("threshold", 120), 20)));
        when(checkerRegistry.getRequired("BLUR")).thenReturn(mock(ImageQualityChecker.class));

        assertThrows(ServiceException.class, () -> service.create(req));
        verify(profileMapper, never()).insert(any(QualityProfileDO.class));
        verify(profileItemMapper, never()).insertBatch(anyCollection());
    }

    @Test
    void getChecksShouldReturnStoredItemsInOrder() {
        QualityProfileItemDO exposure = profileItem(1L, "OVER_EXPOSURE", "{\"maxRatio\":0.15}", 20);
        QualityProfileItemDO resolution = profileItem(2L, "MIN_RESOLUTION",
                "{\"minWidth\":1024,\"minHeight\":1024}", 10);
        when(profileItemMapper.selectListByProfileId(7L)).thenReturn(List.of(resolution, exposure));

        List<QualityProfileVO.CheckItem> checks = service.getChecks(7L);

        assertEquals(List.of("MIN_RESOLUTION", "OVER_EXPOSURE"),
                checks.stream().map(QualityProfileVO.CheckItem::getCheckCode).toList());
        assertEquals(1024, checks.get(0).getConfig().get("minWidth"));
        verify(profileMapper, never()).selectById(anyLong());
    }

    @Test
    void getChecksShouldConvertLegacyE11Profile() {
        QualityProfileDO profile = new QualityProfileDO();
        profile.setId(8L);
        profile.setModelCode("E11");
        profile.setModelConfigJson("{\"confidence\":0.75,\"minDetections\":1}");
        when(profileItemMapper.selectListByProfileId(8L)).thenReturn(List.of());
        when(profileMapper.selectById(8L)).thenReturn(profile);

        List<QualityProfileVO.CheckItem> checks = service.getChecks(8L);

        assertEquals(1, checks.size());
        assertEquals("E11_MODEL", checks.get(0).getCheckCode());
        assertEquals(10, checks.get(0).getSortNo());
        assertEquals(0.75D, checks.get(0).getConfig().get("confidence"));
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

    private static QualityProfileVO.CheckItem check(String checkCode, Map<String, Object> config, Integer sortNo) {
        QualityProfileVO.CheckItem item = new QualityProfileVO.CheckItem();
        item.setCheckCode(checkCode);
        item.setConfig(config);
        item.setSortNo(sortNo);
        return item;
    }

    private static QualityProfileItemDO profileItem(Long id, String checkCode, String configJson, Integer sortNo) {
        QualityProfileItemDO item = new QualityProfileItemDO();
        item.setId(id);
        item.setCheckCode(checkCode);
        item.setConfigJson(configJson);
        item.setSortNo(sortNo);
        return item;
    }

}
