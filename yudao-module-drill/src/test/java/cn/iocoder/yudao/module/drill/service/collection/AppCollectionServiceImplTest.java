package cn.iocoder.yudao.module.drill.service.collection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionCaptureDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionPhotoDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionCaptureMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionPhotoMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.APP_COLLECTION_INCOMPLETE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppCollectionServiceImplTest {

    private final CollectionTaskMapper taskMapper = mock(CollectionTaskMapper.class);
    private final CollectionPhotoMapper photoMapper = mock(CollectionPhotoMapper.class);
    private final CollectionCaptureMapper captureMapper = mock(CollectionCaptureMapper.class);
    private final AppCollectionServiceImpl service = new AppCollectionServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "photoMapper", photoMapper);
        ReflectionTestUtils.setField(service, "captureMapper", captureMapper);
    }

    @Test
    void legacySubmitShouldCompleteWithoutCreatingDemoResult() {
        CollectionTaskDO task = task();
        when(taskMapper.selectByClientId(9L, "client-1")).thenReturn(task);
        when(captureMapper.selectByTaskId(7L)).thenReturn(List.of(
                capture("overall"), capture("blade-1")));
        when(photoMapper.selectListByTask(7L)).thenReturn(List.of(
                photo("overall", "PASSED"), photo("blade-1", "WARNING")));

        AppCollectionVO.TaskResp result = service.submit(9L, "client-1");

        assertEquals("COMPLETED", result.getStatus());
        assertNull(result.getResultJson());
        assertNotNull(task.getSubmitTime());
        verify(taskMapper).updateById(task);
    }

    @Test
    void legacySubmitShouldRequireEachDynamicCapture() {
        CollectionTaskDO task = task();
        when(taskMapper.selectByClientId(9L, "client-1")).thenReturn(task);
        when(captureMapper.selectByTaskId(7L)).thenReturn(List.of(
                capture("overall"), capture("blade-1")));
        when(photoMapper.selectListByTask(7L)).thenReturn(List.of(
                photo("overall", "PASSED"), photo("other", "PASSED")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submit(9L, "client-1"));

        assertEquals(APP_COLLECTION_INCOMPLETE.getCode(), error.getCode());
        verify(taskMapper, never()).updateById(any(CollectionTaskDO.class));
    }

    private static CollectionTaskDO task() {
        return new CollectionTaskDO().setId(7L).setClientId("client-1")
                .setCollectorUserId(9L).setStatus("DRAFT");
    }

    private static CollectionCaptureDO capture(String code) {
        return new CollectionCaptureDO().setTaskId(7L).setCaptureCode(code)
                .setRequiredFlag(true);
    }

    private static CollectionPhotoDO photo(String code, String state) {
        return new CollectionPhotoDO().setTaskId(7L).setCaptureId(code)
                .setPhotoState(state);
    }
}
