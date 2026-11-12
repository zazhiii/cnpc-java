package cn.iocoder.yudao.module.drill.service.collection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionTaskVO;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskFormEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoFileEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoItemEntity;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskEntityMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskFormEntityMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskPhotoFileEntityMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskPhotoItemEntityMapper;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.APP_COLLECTION_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.APP_COLLECTION_INCOMPLETE;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.APP_COLLECTION_TASK_FORBIDDEN;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.APP_COLLECTION_TASK_NOT_DRAFT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppCollectionTaskServiceImplTest {

    private final CollectionTaskEntityMapper taskEntityMapper = mock(CollectionTaskEntityMapper.class);
    private final CollectionTaskFormEntityMapper taskFormEntityMapper = mock(CollectionTaskFormEntityMapper.class);
    private final CollectionTaskPhotoItemEntityMapper taskPhotoItemEntityMapper =
            mock(CollectionTaskPhotoItemEntityMapper.class);
    private final CollectionTaskPhotoFileEntityMapper taskPhotoFileEntityMapper =
            mock(CollectionTaskPhotoFileEntityMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final AppCollectionTaskServiceImpl service = new AppCollectionTaskServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "taskEntityMapper", taskEntityMapper);
        ReflectionTestUtils.setField(service, "taskFormEntityMapper", taskFormEntityMapper);
        ReflectionTestUtils.setField(service, "taskPhotoItemEntityMapper", taskPhotoItemEntityMapper);
        ReflectionTestUtils.setField(service, "taskPhotoFileEntityMapper", taskPhotoFileEntityMapper);
        ReflectionTestUtils.setField(service, "fileService", fileService);
    }

    @Test
    void createShouldBeIdempotentByClientId() {
        AppCollectionTaskVO.CreateReq request = request("same-client");
        CollectionTaskEntity existing = taskEntity(7L, "same-client");
        when(taskEntityMapper.selectByUserIdAndClientId(9L, "same-client")).thenReturn(existing);
        when(taskFormEntityMapper.selectByTaskId(7L)).thenReturn(taskFormEntity(7L));
        when(taskPhotoItemEntityMapper.selectListByTaskId(7L)).thenReturn(List.of(
                photoItem(7L, "OVERALL", null), photoItem(7L, "BLADE", 1)));

        AppCollectionTaskVO.Resp result = service.create(9L, request);

        assertEquals(7L, result.getId());
        verify(taskEntityMapper, never()).insert(any(CollectionTaskEntity.class));
        verify(taskFormEntityMapper, never()).insert(any(CollectionTaskFormEntity.class));
        verify(taskPhotoItemEntityMapper, never()).insert(any(CollectionTaskPhotoItemEntity.class));
    }

    @Test
    void createShouldRejectDifferentRequestUsingSameClientId() {
        AppCollectionTaskVO.CreateReq request = request("same-client");
        request.getDrillBit().setCode("PDC-OTHER");
        CollectionTaskEntity existing = taskEntity(7L, "same-client");
        when(taskEntityMapper.selectByUserIdAndClientId(9L, "same-client")).thenReturn(existing);
        when(taskFormEntityMapper.selectByTaskId(7L)).thenReturn(taskFormEntity(7L));
        when(taskPhotoItemEntityMapper.selectListByTaskId(7L)).thenReturn(List.of(
                photoItem(7L, "OVERALL", null), photoItem(7L, "BLADE", 1)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.create(9L, request));

        assertEquals(APP_COLLECTION_IDEMPOTENCY_CONFLICT.getCode(), error.getCode());
        verify(taskEntityMapper, never()).insert(any(CollectionTaskEntity.class));
    }

    @Test
    void createShouldGenerateDefaultChecklistAndTaskNumber() {
        AppCollectionTaskVO.CreateReq request = request("new-client");
        doAnswer(invocation -> {
            CollectionTaskEntity inserted = invocation.getArgument(0);
            inserted.setId(501L);
            inserted.setCreateTime(LocalDateTime.of(2026, 9, 9, 8, 0));
            inserted.setUpdateTime(inserted.getCreateTime());
            return 1;
        }).when(taskEntityMapper).insert(any(CollectionTaskEntity.class));

        AppCollectionTaskVO.Resp result = service.create(9L, request);

        assertEquals("CJ-20260909-000501", result.getTaskNo());
        assertEquals(1, result.getVersion());
        verify(taskFormEntityMapper).insert(org.mockito.ArgumentMatchers.<CollectionTaskFormEntity>argThat(form ->
                form.getTaskId().equals(501L) && form.getDrillCode().equals("PDC-001")
                        && form.getWellNumber().equals("川页H12-3井")));
        verify(taskPhotoItemEntityMapper, times(2)).insert(any(CollectionTaskPhotoItemEntity.class));
        verify(taskPhotoItemEntityMapper).insert(org.mockito.ArgumentMatchers.<CollectionTaskPhotoItemEntity>argThat(item ->
                item.getTaskId().equals(501L) && item.getPhotoType().equals("OVERALL")
                        && item.getBladeNo() == null && item.getStatus().equals("PENDING")));
        verify(taskPhotoItemEntityMapper).insert(org.mockito.ArgumentMatchers.<CollectionTaskPhotoItemEntity>argThat(item ->
                item.getPhotoType().equals("BLADE") && item.getBladeNo().equals(1)));
    }

    @Test
    void createShouldPersistClientBladeCount() {
        AppCollectionTaskVO.CreateReq request = request("dynamic-client");
        request.setBladeCount(3);
        doAnswer(invocation -> {
            CollectionTaskEntity inserted = invocation.getArgument(0);
            inserted.setId(502L);
            inserted.setCreateTime(LocalDateTime.of(2026, 9, 11, 8, 0));
            inserted.setUpdateTime(inserted.getCreateTime());
            return 1;
        }).when(taskEntityMapper).insert(any(CollectionTaskEntity.class));

        service.create(9L, request);

        verify(taskPhotoItemEntityMapper, times(4)).insert(any(CollectionTaskPhotoItemEntity.class));
        verify(taskPhotoItemEntityMapper).insert(org.mockito.ArgumentMatchers.<CollectionTaskPhotoItemEntity>argThat(item ->
                item.getPhotoType().equals("OVERALL") && item.getBladeNo() == null));
        verify(taskPhotoItemEntityMapper).insert(org.mockito.ArgumentMatchers.<CollectionTaskPhotoItemEntity>argThat(item ->
                item.getPhotoType().equals("BLADE") && item.getBladeNo().equals(3)));
    }

    @Test
    void updateShouldUseTaskFormAndPhotoItemTables() {
        AppCollectionTaskVO.UpdateReq request = new AppCollectionTaskVO.UpdateReq();
        request.setVersion(1);
        request.setDrillBit(request("x").getDrillBit());
        request.getDrillBit().setCode("PDC-UPDATED");
        request.setOperation(request("x").getOperation());
        request.setBladeCount(1);
        CollectionTaskEntity task = taskEntity(7L, "x");
        CollectionTaskFormEntity form = taskFormEntity(7L).setId(31L);
        CollectionTaskPhotoItemEntity overall = photoItem(7L, "OVERALL", null).setId(41L);
        CollectionTaskPhotoItemEntity blade1 = photoItem(7L, "BLADE", 1).setId(42L)
                .setStatus("PASSED");
        CollectionTaskPhotoItemEntity blade2 = photoItem(7L, "BLADE", 2).setId(43L);
        when(taskEntityMapper.selectByIdForUpdate(7L)).thenReturn(task);
        when(taskFormEntityMapper.selectByTaskId(7L)).thenReturn(form);
        when(taskPhotoItemEntityMapper.selectListByTaskId(7L))
                .thenReturn(List.of(overall, blade1, blade2));

        AppCollectionTaskVO.Resp result = service.updateDraft(9L, 7L, request);

        assertEquals("PDC-UPDATED", result.getDrillBit().getCode());
        assertEquals(2, result.getCaptures().size());
        assertEquals("PASSED", result.getCaptures().get(1).getState());
        assertEquals(1, result.getVersion());
        verify(taskFormEntityMapper).updateById(org.mockito.ArgumentMatchers
                .<CollectionTaskFormEntity>argThat(item -> "PDC-UPDATED".equals(item.getDrillCode())));
        verify(taskPhotoItemEntityMapper).updateById(overall);
        verify(taskPhotoItemEntityMapper).updateById(blade1);
        verify(taskPhotoItemEntityMapper).deleteById(43L);
        verify(taskEntityMapper).updateById(task);
    }

    @Test
    void updateShouldRejectAnotherUsersTask() {
        AppCollectionTaskVO.UpdateReq request = updateRequest();
        when(taskEntityMapper.selectByIdForUpdate(7L))
                .thenReturn(taskEntity(7L, "x").setUserId(10L));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateDraft(9L, 7L, request));

        assertEquals(APP_COLLECTION_TASK_FORBIDDEN.getCode(), error.getCode());
        verifyNoInteractions(taskFormEntityMapper, taskPhotoItemEntityMapper);
    }

    @Test
    void updateShouldRejectNonDraftTask() {
        AppCollectionTaskVO.UpdateReq request = updateRequest();
        when(taskEntityMapper.selectByIdForUpdate(7L))
                .thenReturn(taskEntity(7L, "x").setStatus("COMPLETED"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.updateDraft(9L, 7L, request));

        assertEquals(APP_COLLECTION_TASK_NOT_DRAFT.getCode(), error.getCode());
        verifyNoInteractions(taskFormEntityMapper, taskPhotoItemEntityMapper);
    }

    @Test
    void submitShouldRequireEveryDynamicRequiredCapture() {
        CollectionTaskEntity task = taskEntity(7L, "missing-photo");
        List<CollectionTaskPhotoItemEntity> captures = List.of(
                photoItem(7L, "OVERALL", null),
                photoItem(7L, "BLADE", 1));
        when(taskEntityMapper.selectById(7L)).thenReturn(task);
        when(taskFormEntityMapper.selectByTaskId(7L)).thenReturn(taskFormEntity(7L));
        when(taskPhotoItemEntityMapper.selectListByTaskId(7L)).thenReturn(captures);
        when(taskPhotoFileEntityMapper.selectListByTaskId(7L)).thenReturn(List.of(
                photoFile(7L, "overall", "PASSED")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submit(9L, 7L));

        assertEquals(APP_COLLECTION_INCOMPLETE.getCode(), error.getCode());
        verify(taskEntityMapper, never()).updateById(any(CollectionTaskEntity.class));
    }

    @Test
    void uploadPhotoShouldPersistFileAndUpdateCaptureState() {
        CollectionTaskEntity task = taskEntity(7L, "upload");
        CollectionTaskPhotoItemEntity capture = photoItem(7L, "OVERALL", null)
                .setId(21L).setStatus("PENDING");
        when(taskEntityMapper.selectById(7L)).thenReturn(task);
        when(taskPhotoItemEntityMapper.selectListByTaskId(7L)).thenReturn(List.of(capture));
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
        verify(taskPhotoFileEntityMapper).insert(org.mockito.ArgumentMatchers
                .<CollectionTaskPhotoFileEntity>argThat(photo ->
                photo.getTaskId().equals(7L) && photo.getCaptureId().equals("overall")
                        && photo.getPhotoState().equals("WARNING")));
        verify(taskPhotoItemEntityMapper).updateById(org.mockito.ArgumentMatchers
                .<CollectionTaskPhotoItemEntity>argThat(item ->
                item.getId().equals(21L) && item.getStatus().equals("WARNING")));
    }

    @Test
    void submitShouldIgnoreMissingOptionalCaptureAndCompleteTask() {
        CollectionTaskEntity task = taskEntity(7L, "ready");
        List<CollectionTaskPhotoItemEntity> captures = List.of(
                photoItem(7L, "OVERALL", null),
                photoItem(7L, "BLADE", 1),
                photoItem(7L, "BLADE", 2).setRequiredFlag(false));
        when(taskEntityMapper.selectById(7L)).thenReturn(task);
        when(taskFormEntityMapper.selectByTaskId(7L)).thenReturn(taskFormEntity(7L));
        when(taskPhotoItemEntityMapper.selectListByTaskId(7L)).thenReturn(captures);
        when(taskPhotoFileEntityMapper.selectListByTaskId(7L)).thenReturn(List.of(
                photoFile(7L, "overall", "PASSED"),
                photoFile(7L, "blade-1", "WARNING")));

        AppCollectionTaskVO.Resp result = service.submit(9L, 7L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(1, result.getVersion());
        verify(taskEntityMapper).updateById(task);
    }

    @Test
    void resubmitPendingQualityTaskShouldCompleteIt() {
        CollectionTaskEntity task = taskEntity(7L, "old-pending").setStatus("PENDING_QUALITY");
        when(taskEntityMapper.selectById(7L)).thenReturn(task);
        when(taskFormEntityMapper.selectByTaskId(7L)).thenReturn(taskFormEntity(7L));
        when(taskPhotoItemEntityMapper.selectListByTaskId(7L))
                .thenReturn(List.of(photoItem(7L, "OVERALL", null)));
        when(taskPhotoFileEntityMapper.selectListByTaskId(7L))
                .thenReturn(List.of(photoFile(7L, "overall", "PASSED")));

        AppCollectionTaskVO.Resp result = service.submit(9L, 7L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(1, result.getVersion());
        verify(taskEntityMapper).updateById(task);
    }

    @Test
    void resubmitCompletedTaskShouldBeIdempotent() {
        CollectionTaskEntity task = taskEntity(7L, "done").setStatus("COMPLETED");
        when(taskEntityMapper.selectById(7L)).thenReturn(task);
        when(taskFormEntityMapper.selectByTaskId(7L)).thenReturn(taskFormEntity(7L));
        when(taskPhotoItemEntityMapper.selectListByTaskId(7L)).thenReturn(List.of());

        AppCollectionTaskVO.Resp result = service.submit(9L, 7L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(1, result.getVersion());
        verify(taskEntityMapper, never()).updateById(any(CollectionTaskEntity.class));
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

    private static AppCollectionTaskVO.UpdateReq updateRequest() {
        AppCollectionTaskVO.UpdateReq request = new AppCollectionTaskVO.UpdateReq();
        request.setVersion(1);
        request.setDrillBit(request("update").getDrillBit());
        request.setOperation(request("update").getOperation());
        request.setBladeCount(1);
        return request;
    }

    private static CollectionTaskEntity taskEntity(Long id, String clientId) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 8, 0);
        CollectionTaskEntity task = new CollectionTaskEntity().setId(id).setClientId(clientId)
                .setUserId(9L).setTaskNo("CJ-20260909-000007")
                .setTaskType("PULL_OUT").setStatus("DRAFT");
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setDeleted(false);
        return task;
    }

    private static CollectionTaskFormEntity taskFormEntity(Long taskId) {
        return new CollectionTaskFormEntity().setTaskId(taskId).setDrillCode("PDC-001")
                .setDrillType("PDC钻头").setWellNumber("川页H12-3井");
    }

    private static CollectionTaskPhotoItemEntity photoItem(Long taskId, String type, Integer bladeNo) {
        return new CollectionTaskPhotoItemEntity().setTaskId(taskId).setPhotoType(type)
                .setTitle(bladeNo == null ? "钻头全局图" : bladeNo + "号刀翼")
                .setRequiredFlag(true).setBladeNo(bladeNo)
                .setSortNo(bladeNo == null ? 1 : bladeNo + 1).setStatus("PENDING");
    }

    private static CollectionTaskPhotoFileEntity photoFile(Long taskId, String captureId, String state) {
        return new CollectionTaskPhotoFileEntity().setTaskId(taskId).setCaptureId(captureId)
                .setPhotoType(captureId).setPhotoState(state).setFileUrl("/files/" + captureId + ".jpg");
    }
}
