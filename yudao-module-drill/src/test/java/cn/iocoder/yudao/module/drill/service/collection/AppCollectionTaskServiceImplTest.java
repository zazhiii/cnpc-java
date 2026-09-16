package cn.iocoder.yudao.module.drill.service.collection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.AppCollectionTaskVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionCaptureDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionPhotoDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionTaskChangeDO;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionCaptureMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionPhotoMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskChangeMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.APP_COLLECTION_TASK_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.APP_COLLECTION_INCOMPLETE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppCollectionTaskServiceImplTest {

    private final CollectionTaskMapper taskMapper = mock(CollectionTaskMapper.class);
    private final CollectionCaptureMapper captureMapper = mock(CollectionCaptureMapper.class);
    private final CollectionTaskChangeMapper changeMapper = mock(CollectionTaskChangeMapper.class);
    private final CollectionPhotoMapper photoMapper = mock(CollectionPhotoMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final AppCollectionTaskServiceImpl service = new AppCollectionTaskServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "captureMapper", captureMapper);
        ReflectionTestUtils.setField(service, "changeMapper", changeMapper);
        ReflectionTestUtils.setField(service, "photoMapper", photoMapper);
        ReflectionTestUtils.setField(service, "fileService", fileService);
    }

    @Test
    void createShouldBeIdempotentByClientId() {
        AppCollectionTaskVO.CreateReq request = request("same-client");
        CollectionTaskDO existing = task(7L, "same-client", 1);
        when(taskMapper.selectByClientId(9L, "same-client")).thenReturn(existing);
        when(captureMapper.selectByTaskId(7L)).thenReturn(List.of(
                new CollectionCaptureDO().setTaskId(7L).setCaptureCode("blade-1").setBladeNo(1)));

        AppCollectionTaskVO.Resp result = service.create(9L, request);

        assertEquals(7L, result.getId());
        verify(taskMapper, never()).insert(any(CollectionTaskDO.class));
        verify(captureMapper, never()).insert(any(CollectionCaptureDO.class));
    }

    @Test
    void createShouldGenerateDefaultChecklistAndTaskNumber() {
        AppCollectionTaskVO.CreateReq request = request("new-client");
        when(captureMapper.selectByTaskId(501L)).thenReturn(List.of());
        doAnswer(invocation -> {
            CollectionTaskDO inserted = invocation.getArgument(0);
            inserted.setId(501L);
            inserted.setCreateTime(LocalDateTime.of(2026, 9, 9, 8, 0));
            inserted.setUpdateTime(inserted.getCreateTime());
            return 1;
        }).when(taskMapper).insert(any(CollectionTaskDO.class));

        AppCollectionTaskVO.Resp result = service.create(9L, request);

        assertEquals("CJ-20260909-000501", result.getTaskNo());
        assertEquals(1, result.getVersion());
        verify(captureMapper, times(2)).insert(any(CollectionCaptureDO.class));
        verify(captureMapper).insert(org.mockito.ArgumentMatchers.<CollectionCaptureDO>argThat(capture -> capture.getTaskId().equals(501L)
                && capture.getCaptureCode().equals("overall") && capture.getState().equals("PENDING")));
        verify(captureMapper).insert(org.mockito.ArgumentMatchers.<CollectionCaptureDO>argThat(capture ->
                capture.getCaptureCode().equals("blade-1") && capture.getBladeNo().equals(1)));
        verify(changeMapper).insert(org.mockito.ArgumentMatchers.<CollectionTaskChangeDO>argThat(change -> change.getClientId().equals("new-client")
                && change.getOperation().equals("UPSERT")));
    }

    @Test
    void createShouldPersistClientBladeCount() {
        AppCollectionTaskVO.CreateReq request = request("dynamic-client");
        request.setBladeCount(3);
        when(captureMapper.selectByTaskId(502L)).thenReturn(List.of());
        doAnswer(invocation -> {
            CollectionTaskDO inserted = invocation.getArgument(0);
            inserted.setId(502L);
            inserted.setCreateTime(LocalDateTime.of(2026, 9, 11, 8, 0));
            inserted.setUpdateTime(inserted.getCreateTime());
            return 1;
        }).when(taskMapper).insert(any(CollectionTaskDO.class));

        service.create(9L, request);

        verify(captureMapper, times(4)).insert(any(CollectionCaptureDO.class));
        verify(captureMapper).insert(org.mockito.ArgumentMatchers.<CollectionCaptureDO>argThat(capture ->
                capture.getCaptureCode().equals("overall") && capture.getBladeNo() == null));
        verify(captureMapper).insert(org.mockito.ArgumentMatchers.<CollectionCaptureDO>argThat(capture ->
                capture.getCaptureCode().equals("blade-3") && capture.getBladeNo().equals(3)));
    }

    @Test
    void updateShouldReportOptimisticLockConflict() {
        AppCollectionTaskVO.UpdateReq request = new AppCollectionTaskVO.UpdateReq();
        request.setVersion(1);
        request.setDrillBit(request("x").getDrillBit());
        request.setOperation(request("x").getOperation());
        request.setBladeCount(1);
        when(taskMapper.update(any(), any())).thenReturn(0);
        when(taskMapper.selectById(7L)).thenReturn(task(7L, "x", 2));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateDraft(9L, 7L, request));

        assertEquals(APP_COLLECTION_TASK_VERSION_CONFLICT.getCode(), error.getCode());
        verify(changeMapper, never()).insert(any(CollectionTaskChangeDO.class));
    }

    @Test
    void submitShouldRequireEveryDynamicRequiredCapture() {
        CollectionTaskDO task = task(7L, "missing-photo", 1);
        List<CollectionCaptureDO> captures = List.of(
                capture(7L, "overall", true),
                capture(7L, "blade-1", true));
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(captureMapper.selectByTaskId(7L)).thenReturn(captures);
        when(photoMapper.selectListByTask(7L)).thenReturn(List.of(
                photo(7L, "overall", "PASSED")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submit(9L, 7L));

        assertEquals(APP_COLLECTION_INCOMPLETE.getCode(), error.getCode());
        verify(taskMapper, never()).updateById(any(CollectionTaskDO.class));
        verify(changeMapper, never()).insert(any(CollectionTaskChangeDO.class));
    }

    @Test
    void uploadPhotoShouldPersistFileAndUpdateCaptureState() {
        CollectionTaskDO task = task(7L, "upload", 1);
        CollectionCaptureDO capture = capture(7L, "overall", true).setId(21L).setState("PENDING");
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(captureMapper.selectByTaskId(7L)).thenReturn(List.of(capture));
        when(fileService.createFile(any(byte[].class), eq("overall.jpg"),
                eq("drill/collection/7"), eq("image/jpeg")))
                .thenReturn("https://files.example/overall.jpg");

        AppCollectionVO.UploadReq request = new AppCollectionVO.UploadReq();
        request.setFile(new MockMultipartFile(
                "file", "overall.jpg", "image/jpeg", new byte[]{1, 2, 3}));
        request.setPhotoType("钻头全局图");
        request.setPhotoState("WARNING");

        AppCollectionVO.PhotoResp result = service.uploadPhoto(9L, 7L, "overall", request);

        assertEquals("overall", result.getCaptureId());
        assertEquals("WARNING", result.getPhotoState());
        assertEquals("https://files.example/overall.jpg", result.getFileUrl());
        verify(photoMapper).insert(org.mockito.ArgumentMatchers.<CollectionPhotoDO>argThat(photo ->
                photo.getTaskId().equals(7L) && photo.getCaptureId().equals("overall")
                        && photo.getPhotoState().equals("WARNING")));
        verify(captureMapper).updateById(org.mockito.ArgumentMatchers.<CollectionCaptureDO>argThat(item ->
                item.getId().equals(21L) && item.getState().equals("WARNING")));
    }

    @Test
    void submitShouldIgnoreMissingOptionalCaptureAndCompleteTask() {
        CollectionTaskDO task = task(7L, "ready", 3);
        List<CollectionCaptureDO> captures = List.of(
                capture(7L, "overall", true),
                capture(7L, "blade-1", true),
                capture(7L, "extra", false));
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(captureMapper.selectByTaskId(7L)).thenReturn(captures);
        when(photoMapper.selectListByTask(7L)).thenReturn(List.of(
                photo(7L, "overall", "PASSED"),
                photo(7L, "blade-1", "WARNING")));

        AppCollectionTaskVO.Resp result = service.submit(9L, 7L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(4, result.getVersion());
        assertNotNull(task.getSubmitTime());
        verify(taskMapper).updateById(task);
        verify(changeMapper).insert(org.mockito.ArgumentMatchers.<CollectionTaskChangeDO>argThat(change ->
                change.getClientId().equals("ready") && change.getTaskVersion().equals(4)));
    }

    @Test
    void resubmitPendingQualityTaskShouldCompleteIt() {
        CollectionTaskDO task = task(7L, "old-pending", 3).setStatus("PENDING_QUALITY");
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(captureMapper.selectByTaskId(7L)).thenReturn(List.of(capture(7L, "overall", true)));
        when(photoMapper.selectListByTask(7L)).thenReturn(List.of(photo(7L, "overall", "PASSED")));

        AppCollectionTaskVO.Resp result = service.submit(9L, 7L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(4, result.getVersion());
        verify(taskMapper).updateById(task);
    }

    @Test
    void resubmitCompletedTaskShouldBeIdempotent() {
        CollectionTaskDO task = task(7L, "done", 4).setStatus("COMPLETED");
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(captureMapper.selectByTaskId(7L)).thenReturn(List.of());

        AppCollectionTaskVO.Resp result = service.submit(9L, 7L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(4, result.getVersion());
        verify(taskMapper, never()).updateById(any(CollectionTaskDO.class));
    }

    private static AppCollectionTaskVO.CreateReq request(String clientId) {
        AppCollectionTaskVO.DrillBit drill = new AppCollectionTaskVO.DrillBit();
        drill.setCode("PDC-001");
        drill.setType("PDC钻头");
        AppCollectionTaskVO.Operation operation = new AppCollectionTaskVO.Operation();
        operation.setWellNumber("川页H12-3井");
        AppCollectionTaskVO.CreateReq request = new AppCollectionTaskVO.CreateReq();
        request.setClientId(clientId);
        request.setTaskType("PULL_OUT");
        request.setDrillBit(drill);
        request.setOperation(operation);
        request.setBladeCount(1);
        return request;
    }

    private static CollectionTaskDO task(Long id, String clientId, int version) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 8, 0);
        CollectionTaskDO task = new CollectionTaskDO().setId(id).setClientId(clientId).setTaskNo("CJ-20260909-000007")
                .setTaskType("PULL_OUT").setStatus("DRAFT").setVersion(version)
                .setCollectorUserId(9L).setDrillCode("PDC-001").setDrillType("PDC钻头");
        task.setCreateTime(now);
        task.setUpdateTime(now);
        return task;
    }

    private static CollectionCaptureDO capture(Long taskId, String code, boolean required) {
        return new CollectionCaptureDO().setTaskId(taskId).setCaptureCode(code)
                .setTitle(code).setDescription(code).setRequiredFlag(required)
                .setSortNo(1).setState("PASSED");
    }

    private static CollectionPhotoDO photo(Long taskId, String captureId, String state) {
        return new CollectionPhotoDO().setTaskId(taskId).setCaptureId(captureId)
                .setPhotoType(captureId).setPhotoState(state).setFileUrl("/files/" + captureId + ".jpg");
    }
}
