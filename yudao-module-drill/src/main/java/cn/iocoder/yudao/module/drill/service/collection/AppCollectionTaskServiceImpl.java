package cn.iocoder.yudao.module.drill.service.collection;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.AppCollectionTaskVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.*;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.*;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;

@Service
public class AppCollectionTaskServiceImpl implements AppCollectionTaskService {
    private static final String DRAFT = "DRAFT";
    private static final String PENDING_QUALITY = "PENDING_QUALITY";
    private static final String COMPLETED = "COMPLETED";
    private static final int SYNC_LIMIT = 100;

    @Resource
    private CollectionTaskMapper taskMapper;
    @Resource
    private CollectionCaptureMapper captureMapper;
    @Resource
    private CollectionTaskChangeMapper changeMapper;
    @Resource
    private CollectionPhotoMapper photoMapper;
    @Resource
    private FileService fileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCollectionTaskVO.Resp create(Long userId, AppCollectionTaskVO.CreateReq req) {
        CollectionTaskDO existing = taskMapper.selectByClientId(userId, req.getClientId());
        if (existing != null) {
            if (!sameIdentity(existing, req)) {
                throw exception(APP_COLLECTION_IDEMPOTENCY_CONFLICT);
            }
            return convert(existing);
        }

        CollectionTaskDO task = new CollectionTaskDO()
                .setClientId(req.getClientId())
                .setTaskNo("PENDING-" + req.getClientId())
                .setTaskType(req.getTaskType())
                .setStatus(DRAFT)
                .setVersion(1)
                .setCollectorUserId(userId);
        copy(req.getDrillBit(), req.getOperation(), task);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            CollectionTaskDO concurrent = taskMapper.selectByClientId(userId, req.getClientId());
            if (concurrent != null && sameIdentity(concurrent, req)) return convert(concurrent);
            throw exception(APP_COLLECTION_IDEMPOTENCY_CONFLICT);
        }
        task.setTaskNo("CJ-" + task.getCreateTime().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format("%06d", task.getId()));
        taskMapper.updateById(task);

        replaceDynamicCaptures(task.getId(), req.getBladeCount());
        recordChange(task, "UPSERT");
        return convert(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCollectionTaskVO.Resp updateDraft(Long userId, Long id, AppCollectionTaskVO.UpdateReq req) {
        CollectionTaskDO update = new CollectionTaskDO();
        copy(req.getDrillBit(), req.getOperation(), update);
        int updated = taskMapper.update(update, new LambdaUpdateWrapper<CollectionTaskDO>()
                .eq(CollectionTaskDO::getId, id)
                .eq(CollectionTaskDO::getCollectorUserId, userId)
                .eq(CollectionTaskDO::getStatus, DRAFT)
                .eq(CollectionTaskDO::getVersion, req.getVersion())
                .setSql("version = version + 1"));
        if (updated == 0) {
            CollectionTaskDO current = requireTask(userId, id);
            if (!DRAFT.equals(current.getStatus())) throw exception(APP_COLLECTION_TASK_NOT_DRAFT);
            throw exception(APP_COLLECTION_TASK_VERSION_CONFLICT);
        }
        CollectionTaskDO task = requireTask(userId, id);
        replaceDynamicCaptures(task.getId(), req.getBladeCount());
        recordChange(task, "UPSERT");
        return convert(task);
    }

    @Override
    public AppCollectionTaskVO.Resp get(Long userId, Long id) {
        return convert(requireTask(userId, id));
    }

    @Override
    public PageResult<AppCollectionTaskVO.Resp> getPage(Long userId, AppCollectionTaskVO.PageReq req) {
        PageResult<CollectionTaskDO> page = taskMapper.selectPage(userId, req);
        return new PageResult<>(page.getList().stream().map(this::convert).toList(), page.getTotal());
    }

    @Override
    public AppCollectionTaskVO.SyncResp sync(Long userId, String cursorValue) {
        long cursor;
        try {
            cursor = cursorValue == null || cursorValue.isBlank() ? 0L : Long.parseLong(cursorValue);
        } catch (NumberFormatException ex) {
            throw exception(APP_COLLECTION_SYNC_CURSOR_INVALID);
        }
        List<CollectionTaskChangeDO> rows = changeMapper.selectChanges(userId, cursor, SYNC_LIMIT + 1);
        boolean hasMore = rows.size() > SYNC_LIMIT;
        if (hasMore) rows = new ArrayList<>(rows.subList(0, SYNC_LIMIT));

        AppCollectionTaskVO.SyncResp resp = new AppCollectionTaskVO.SyncResp();
        List<AppCollectionTaskVO.Change> changes = new ArrayList<>();
        for (CollectionTaskChangeDO row : rows) {
            AppCollectionTaskVO.Change item = new AppCollectionTaskVO.Change();
            item.setRevision(row.getId());
            item.setOperation(row.getOperation());
            item.setClientId(row.getClientId());
            item.setVersion(row.getTaskVersion());
            if (!"DELETE".equals(row.getOperation())) {
                CollectionTaskDO task = taskMapper.selectById(row.getTaskId());
                if (task != null && Objects.equals(task.getCollectorUserId(), userId)) item.setTask(convert(task));
            }
            changes.add(item);
        }
        resp.setChanges(changes);
        resp.setHasMore(hasMore);
        resp.setNextCursor(rows.isEmpty() ? Long.toString(cursor)
                : Long.toString(rows.get(rows.size() - 1).getId()));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(Long userId, Long id, Integer version) {
        CollectionTaskDO current = taskMapper.selectById(id);
        if (current == null) {
            CollectionTaskChangeDO latest = changeMapper.selectLatest(userId, id);
            if (latest != null && "DELETE".equals(latest.getOperation())) return;
            throw exception(APP_COLLECTION_TASK_NOT_EXISTS);
        }
        if (!Objects.equals(current.getCollectorUserId(), userId)) throw exception(APP_COLLECTION_TASK_FORBIDDEN);
        if (!DRAFT.equals(current.getStatus())) throw exception(APP_COLLECTION_TASK_DELETE_FORBIDDEN);
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<CollectionTaskDO>()
                .eq(CollectionTaskDO::getId, id)
                .eq(CollectionTaskDO::getCollectorUserId, userId)
                .eq(CollectionTaskDO::getStatus, DRAFT)
                .eq(CollectionTaskDO::getVersion, version)
                .set(CollectionTaskDO::getDeleted, true)
                .setSql("version = version + 1"));
        if (updated == 0) throw exception(APP_COLLECTION_TASK_VERSION_CONFLICT);
        current.setVersion(current.getVersion() + 1);
        recordChange(current, "DELETE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCollectionVO.PhotoResp uploadPhoto(Long userId, Long id, String captureCode,
                                                  AppCollectionVO.UploadReq req) {
        CollectionTaskDO task = requireTask(userId, id);
        if (!DRAFT.equals(task.getStatus())) throw exception(APP_COLLECTION_TASK_NOT_DRAFT);
        CollectionCaptureDO capture = captureMapper.selectByTaskId(id).stream()
                .filter(item -> Objects.equals(item.getCaptureCode(), captureCode))
                .findFirst()
                .orElseThrow(() -> exception(APP_COLLECTION_CAPTURE_NOT_EXISTS));
        if (req.getFile() == null || req.getFile().isEmpty()) {
            throw exception(APP_COLLECTION_PHOTO_EMPTY);
        }
        if (req.getFile().getSize() > 30L * 1024 * 1024) {
            throw exception(IMAGE_FILE_TOO_LARGE, 30);
        }
        String contentType = StrUtil.blankToDefault(req.getFile().getContentType(), "application/octet-stream");
        if (!contentType.startsWith("image/")) {
            throw exception(IMAGE_FORMAT_UNSUPPORTED, contentType);
        }
        byte[] content;
        try {
            content = IoUtil.readBytes(req.getFile().getInputStream());
        } catch (Exception ex) {
            throw exception(APP_COLLECTION_PHOTO_EMPTY);
        }
        String url = fileService.createFile(content, req.getFile().getOriginalFilename(),
                "drill/collection/" + task.getId(), contentType);
        CollectionPhotoDO photo = photoMapper.selectByTaskAndCapture(task.getId(), captureCode);
        if (photo == null) {
            photo = new CollectionPhotoDO().setTaskId(task.getId()).setCaptureId(captureCode);
            fillPhoto(req, url, photo);
            photoMapper.insert(photo);
        } else {
            fillPhoto(req, url, photo);
            photoMapper.updateById(photo);
        }
        capture.setState(req.getPhotoState());
        captureMapper.updateById(capture);
        return convertPhoto(photo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCollectionTaskVO.Resp submit(Long userId, Long id) {
        CollectionTaskDO task = requireTask(userId, id);
        if (COMPLETED.equals(task.getStatus())) return convert(task);
        if (!DRAFT.equals(task.getStatus()) && !PENDING_QUALITY.equals(task.getStatus())) {
            throw exception(APP_COLLECTION_TASK_NOT_DRAFT);
        }

        List<CollectionCaptureDO> required = captureMapper.selectByTaskId(id).stream()
                .filter(item -> Boolean.TRUE.equals(item.getRequiredFlag())).toList();
        Map<String, CollectionPhotoDO> photos = new HashMap<>();
        for (CollectionPhotoDO photo : photoMapper.selectListByTask(id)) {
            photos.put(photo.getCaptureId(), photo);
        }
        long usable = required.stream().filter(capture -> {
            CollectionPhotoDO photo = photos.get(capture.getCaptureCode());
            return photo != null && ("PASSED".equals(photo.getPhotoState())
                    || "WARNING".equals(photo.getPhotoState()));
        }).count();
        if (usable < required.size()) {
            throw exception(APP_COLLECTION_INCOMPLETE, usable, required.size());
        }

        task.setStatus(COMPLETED);
        task.setSubmitTime(LocalDateTime.now());
        task.setVersion(task.getVersion() + 1);
        taskMapper.updateById(task);
        recordChange(task, "UPSERT");
        return convert(task);
    }

    private CollectionTaskDO requireTask(Long userId, Long id) {
        CollectionTaskDO task = taskMapper.selectById(id);
        if (task == null) throw exception(APP_COLLECTION_TASK_NOT_EXISTS);
        if (!Objects.equals(task.getCollectorUserId(), userId)) throw exception(APP_COLLECTION_TASK_FORBIDDEN);
        return task;
    }

    private boolean sameIdentity(CollectionTaskDO task, AppCollectionTaskVO.CreateReq req) {
        return Objects.equals(task.getTaskType(), req.getTaskType())
                && Objects.equals(task.getDrillCode(), req.getDrillBit().getCode())
                && Objects.equals(req.getBladeCount(),
                (int) captureMapper.selectByTaskId(task.getId()).stream()
                        .filter(item -> item.getBladeNo() != null).count());
    }

    private void copy(AppCollectionTaskVO.DrillBit drill, AppCollectionTaskVO.Operation operation,
                      CollectionTaskDO task) {
        task.setDrillCode(drill.getCode());
        task.setDrillType(drill.getType());
        task.setDrillModel(drill.getModel());
        task.setDrillSize(drill.getSize());
        task.setManufacturer(drill.getManufacturer());
        task.setProductionBatch(drill.getProductionBatch());
        task.setWellNumber(operation.getWellNumber());
        task.setWellSection(operation.getWellSection());
        task.setFormation(operation.getFormation());
        task.setOperationTime(operation.getOperationTime());
        task.setFootage(operation.getFootage());
        task.setDrillingHours(operation.getDrillingHours());
        task.setLocation(operation.getLocation());
        task.setNotes(operation.getNotes());
    }

    private void recordChange(CollectionTaskDO task, String operation) {
        changeMapper.insert(new CollectionTaskChangeDO()
                .setTaskId(task.getId())
                .setCollectorUserId(task.getCollectorUserId())
                .setClientId(task.getClientId())
                .setOperation(operation)
                .setTaskVersion(task.getVersion()));
    }

    private void fillPhoto(AppCollectionVO.UploadReq req, String url, CollectionPhotoDO photo) {
        photo.setPhotoType(req.getPhotoType());
        photo.setPhotoState(req.getPhotoState());
        photo.setFileUrl(url);
        photo.setAuditRegionJson(req.getAuditRegionJson());
        photo.setAuditReportJson(req.getAuditReportJson());
    }

    private AppCollectionVO.PhotoResp convertPhoto(CollectionPhotoDO photo) {
        AppCollectionVO.PhotoResp result = new AppCollectionVO.PhotoResp();
        result.setCaptureId(photo.getCaptureId());
        result.setPhotoType(photo.getPhotoType());
        result.setPhotoState(photo.getPhotoState());
        result.setFileUrl(photo.getFileUrl());
        result.setAuditRegionJson(photo.getAuditRegionJson());
        result.setAuditReportJson(photo.getAuditReportJson());
        return result;
    }

    private void replaceDynamicCaptures(Long taskId, int bladeCount) {
        Map<String, CollectionCaptureDO> existing = new HashMap<>();
        for (CollectionCaptureDO capture : captureMapper.selectByTaskId(taskId)) {
            existing.put(capture.getCaptureCode(), capture);
        }
        upsertDynamicCapture(taskId, existing.remove("overall"), "overall", "钻头全局图",
                "钻头完整进入画面，保持背景简洁", null, 1);
        for (int bladeNo = 1; bladeNo <= bladeCount; bladeNo++) {
            String code = "blade-" + bladeNo;
            upsertDynamicCapture(taskId, existing.remove(code), code, bladeNo + "号刀翼",
                    "拍摄" + bladeNo + "号刀翼全貌及主要切削齿", bladeNo, bladeNo + 1);
        }
        existing.values().forEach(item -> captureMapper.physicalDeleteById(item.getId()));
    }

    private void upsertDynamicCapture(Long taskId, CollectionCaptureDO current, String code,
                                      String title, String description, Integer bladeNo, int sortNo) {
        CollectionCaptureDO capture = current == null ? new CollectionCaptureDO().setTaskId(taskId)
                .setCaptureCode(code).setState("PENDING") : current;
        capture.setTitle(title);
        capture.setDescription(description);
        capture.setRequiredFlag(true);
        capture.setBladeNo(bladeNo);
        capture.setSortNo(sortNo);
        if (capture.getId() == null) captureMapper.insert(capture);
        else captureMapper.updateById(capture);
    }

    private AppCollectionTaskVO.Resp convert(CollectionTaskDO task) {
        AppCollectionTaskVO.Resp result = new AppCollectionTaskVO.Resp();
        result.setId(task.getId());
        result.setClientId(task.getClientId());
        result.setTaskNo(task.getTaskNo());
        result.setTaskType(task.getTaskType());
        result.setStatus(task.getStatus());
        result.setVersion(task.getVersion());
        AppCollectionTaskVO.DrillBit drill = new AppCollectionTaskVO.DrillBit();
        drill.setCode(task.getDrillCode());
        drill.setType(task.getDrillType());
        drill.setModel(task.getDrillModel());
        drill.setSize(task.getDrillSize());
        drill.setManufacturer(task.getManufacturer());
        drill.setProductionBatch(task.getProductionBatch());
        result.setDrillBit(drill);
        AppCollectionTaskVO.Operation operation = new AppCollectionTaskVO.Operation();
        operation.setWellNumber(task.getWellNumber());
        operation.setWellSection(task.getWellSection());
        operation.setFormation(task.getFormation());
        operation.setOperationTime(task.getOperationTime());
        operation.setFootage(task.getFootage());
        operation.setDrillingHours(task.getDrillingHours());
        operation.setLocation(task.getLocation());
        operation.setNotes(task.getNotes());
        result.setOperation(operation);
        result.setCaptures(captureMapper.selectByTaskId(task.getId()).stream().map(this::convertCapture).toList());
        result.setCreatedAt(task.getCreateTime());
        result.setUpdatedAt(task.getUpdateTime());
        result.setDeleted(false);
        return result;
    }

    private AppCollectionTaskVO.Capture convertCapture(CollectionCaptureDO capture) {
        AppCollectionTaskVO.Capture result = new AppCollectionTaskVO.Capture();
        result.setId(capture.getId());
        result.setCode(capture.getCaptureCode());
        result.setTitle(capture.getTitle());
        result.setDescription(capture.getDescription());
        result.setRequired(capture.getRequiredFlag());
        result.setBladeNo(capture.getBladeNo());
        result.setSortNo(capture.getSortNo());
        result.setState(capture.getState());
        return result;
    }
}
