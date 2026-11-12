package cn.iocoder.yudao.module.drill.service.collection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoFileEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoItemEntity;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskEntityMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskPhotoFileEntityMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskPhotoItemEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.APP_COLLECTION_INCOMPLETE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppCollectionServiceImplTest {

    private final CollectionTaskEntityMapper taskEntityMapper = mock(CollectionTaskEntityMapper.class);
    private final CollectionTaskPhotoFileEntityMapper taskPhotoFileEntityMapper =
            mock(CollectionTaskPhotoFileEntityMapper.class);
    private final CollectionTaskPhotoItemEntityMapper taskPhotoItemEntityMapper =
            mock(CollectionTaskPhotoItemEntityMapper.class);
    private final AppCollectionServiceImpl service = new AppCollectionServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "taskEntityMapper", taskEntityMapper);
        ReflectionTestUtils.setField(service, "taskPhotoFileEntityMapper", taskPhotoFileEntityMapper);
        ReflectionTestUtils.setField(service, "taskPhotoItemEntityMapper", taskPhotoItemEntityMapper);
    }

    @Test
    void legacySubmitShouldCompleteWithoutCreatingDemoResult() {
        CollectionTaskEntity task = task();
        when(taskEntityMapper.selectByUserIdAndClientId(9L, "client-1")).thenReturn(task);
        when(taskPhotoItemEntityMapper.selectListByTaskId(7L)).thenReturn(List.of(
                capture("overall"), capture("blade-1")));
        when(taskPhotoFileEntityMapper.selectListByTaskId(7L)).thenReturn(List.of(
                photo("overall", "PASSED"), photo("blade-1", "WARNING")));

        AppCollectionVO.TaskResp result = service.submit(9L, "client-1");

        assertEquals("COMPLETED", result.getStatus());
        assertNull(result.getResultJson());
        verify(taskEntityMapper).updateById(task);
    }

    @Test
    void legacySubmitShouldRequireEachDynamicCapture() {
        CollectionTaskEntity task = task();
        when(taskEntityMapper.selectByUserIdAndClientId(9L, "client-1")).thenReturn(task);
        when(taskPhotoItemEntityMapper.selectListByTaskId(7L)).thenReturn(List.of(
                capture("overall"), capture("blade-1")));
        when(taskPhotoFileEntityMapper.selectListByTaskId(7L)).thenReturn(List.of(
                photo("overall", "PASSED"), photo("other", "PASSED")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submit(9L, "client-1"));

        assertEquals(APP_COLLECTION_INCOMPLETE.getCode(), error.getCode());
        verify(taskEntityMapper, never()).updateById(any(CollectionTaskEntity.class));
    }

    private static CollectionTaskEntity task() {
        return new CollectionTaskEntity().setId(7L).setClientId("client-1")
                .setUserId(9L).setStatus("DRAFT");
    }

    private static CollectionTaskPhotoItemEntity capture(String code) {
        Integer bladeNo = code.startsWith("blade-")
                ? Integer.parseInt(code.substring("blade-".length())) : null;
        return new CollectionTaskPhotoItemEntity().setTaskId(7L)
                .setPhotoType(bladeNo == null ? "OVERALL" : "BLADE")
                .setBladeNo(bladeNo).setRequiredFlag(true);
    }

    private static CollectionTaskPhotoFileEntity photo(String code, String state) {
        return new CollectionTaskPhotoFileEntity().setTaskId(7L).setCaptureId(code)
                .setPhotoState(state);
    }
}
