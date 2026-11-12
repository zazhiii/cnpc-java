package cn.iocoder.yudao.module.drill.service.collection;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionTaskVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.*;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskFormEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoFileEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoItemEntity;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.*;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.controller.android.enums.TaskStatus.DRAFT;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;

@Service
public class AppCollectionTaskServiceImpl implements AppCollectionTaskService {
    private static final String PENDING_QUALITY = "PENDING_QUALITY";
    private static final String COMPLETED = "COMPLETED";

    @Resource
    private CollectionTaskEntityMapper taskEntityMapper;
    @Resource
    private CollectionTaskFormEntityMapper taskFormEntityMapper;
    @Resource
    private CollectionTaskPhotoItemEntityMapper taskPhotoItemEntityMapper;
    @Resource
    private CollectionTaskPhotoFileEntityMapper taskPhotoFileEntityMapper;
    @Resource
    private FileService fileService;

    @Override
    @TenantIgnore
    @Transactional(rollbackFor = Exception.class)
    public AppCollectionTaskVO.Resp create(Long userId, AppCollectionTaskVO.CreateReq req) {
        CollectionTaskEntity existing = taskEntityMapper.selectByUserIdAndClientId(userId, req.getClientId());
        if (existing != null) {
            CollectionTaskFormEntity existingForm = taskFormEntityMapper.selectByTaskId(existing.getId());
            List<CollectionTaskPhotoItemEntity> existingItems =
                    taskPhotoItemEntityMapper.selectListByTaskId(existing.getId());
            if (!sameCreateRequest(existing, existingForm, existingItems, req)) {
                throw exception(APP_COLLECTION_IDEMPOTENCY_CONFLICT);
            }
            return convertCreatedTask(existing, existingForm, existingItems);
        }

        CollectionTaskEntity task = new CollectionTaskEntity()
                .setClientId(req.getClientId())
                .setUserId(userId)
                .setTaskNo("PENDING-" + IdUtil.fastSimpleUUID())
                .setTaskType(req.getTaskType())
                .setStatus(DRAFT.getCode());
        try {
            taskEntityMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            CollectionTaskEntity concurrent =
                    taskEntityMapper.selectByUserIdAndClientIdForUpdate(userId, req.getClientId());
            if (concurrent != null) {
                CollectionTaskFormEntity concurrentForm = taskFormEntityMapper.selectByTaskId(concurrent.getId());
                List<CollectionTaskPhotoItemEntity> concurrentItems =
                        taskPhotoItemEntityMapper.selectListByTaskId(concurrent.getId());
                if (sameCreateRequest(concurrent, concurrentForm, concurrentItems, req)) {
                    return convertCreatedTask(concurrent, concurrentForm, concurrentItems);
                }
            }
            throw exception(APP_COLLECTION_IDEMPOTENCY_CONFLICT);
        }
        task.setTaskNo("CJ-" + task.getCreateTime().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format("%06d", task.getId()));
        taskEntityMapper.updateById(task);

        CollectionTaskFormEntity form = buildTaskForm(task.getId(), req);
        taskFormEntityMapper.insert(form);
        List<CollectionTaskPhotoItemEntity> photoItems = createPhotoItems(task.getId(), req.getBladeCount());
        return convertCreatedTask(task, form, photoItems);
    }

    @Override
    @TenantIgnore
    @Transactional(rollbackFor = Exception.class)
    public AppCollectionTaskVO.Resp updateDraft(Long userId, Long id, AppCollectionTaskVO.UpdateReq req) {
        // 判断采集任务是否存在
        CollectionTaskEntity task = taskEntityMapper.selectByIdForUpdate(id);
        if (task == null) throw exception(APP_COLLECTION_TASK_NOT_EXISTS);
        // 判断是否是当前用户的任务
        if (!Objects.equals(task.getUserId(), userId)) throw exception(APP_COLLECTION_TASK_FORBIDDEN);
        if (!DRAFT.getCode().equals(task.getStatus())) throw exception(APP_COLLECTION_TASK_NOT_DRAFT);

        CollectionTaskFormEntity form = taskFormEntityMapper.selectByTaskId(id);
        if (form == null) {
            form = buildTaskForm(id, req.getDrillBit(), req.getOperation());
            taskFormEntityMapper.insert(form);
        } else {
            fillTaskForm(form, req.getDrillBit(), req.getOperation());
            form.setUpdateTime(LocalDateTime.now());
            taskFormEntityMapper.updateById(form);
        }
        List<CollectionTaskPhotoItemEntity> photoItems = replacePhotoItems(id, req.getBladeCount());

        // 主表本身没有可变的业务字段；显式刷新 update_time，作为任务级最后修改时间。
        task.setUpdateTime(LocalDateTime.now());
        taskEntityMapper.updateById(task);
        return convertCreatedTask(task, form, photoItems);
    }

    @Override
    @TenantIgnore
    public AppCollectionTaskVO.Resp get(Long userId, Long id) {
        CollectionTaskEntity task = requireTask(userId, id);
        return convertCreatedTask(task, requireTaskForm(id),
                taskPhotoItemEntityMapper.selectListByTaskId(id));
    }

    @Override
    @TenantIgnore
    public PageResult<AppCollectionTaskVO.Resp> getPage(Long userId, AppCollectionTaskVO.PageReq req) {
        PageResult<CollectionTaskEntity> page = taskEntityMapper.selectPageByUserId(userId, req);
        List<AppCollectionTaskVO.Resp> rows = page.getList().stream().map(task ->
                convertCreatedTask(task, requireTaskForm(task.getId()),
                        taskPhotoItemEntityMapper.selectListByTaskId(task.getId()))).toList();
        return new PageResult<>(rows, page.getTotal());
    }

    @Override
    @TenantIgnore
    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(Long userId, Long id, Integer version) {
        CollectionTaskEntity current = taskEntityMapper.selectByIdForUpdate(id);
        if (current == null) throw exception(APP_COLLECTION_TASK_NOT_EXISTS);
        if (!Objects.equals(current.getUserId(), userId)) throw exception(APP_COLLECTION_TASK_FORBIDDEN);
        if (!DRAFT.getCode().equals(current.getStatus())) throw exception(APP_COLLECTION_TASK_DELETE_FORBIDDEN);
        taskEntityMapper.deleteById(id);
    }

    @Override
    @TenantIgnore
    @Transactional(rollbackFor = Exception.class)
    public AppCollectionVO.PhotoResp uploadPhoto(Long userId, Long id, String captureCode,
                                                  AppCollectionVO.UploadReq req) {
        CollectionTaskEntity task = requireTask(userId, id);
        if (!DRAFT.getCode().equals(task.getStatus())) throw exception(APP_COLLECTION_TASK_NOT_DRAFT);
        CollectionTaskPhotoItemEntity capture = taskPhotoItemEntityMapper.selectListByTaskId(id).stream()
                .filter(item -> Objects.equals(toCaptureCode(item), captureCode))
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
        CollectionTaskPhotoFileEntity photo =
                taskPhotoFileEntityMapper.selectByTaskAndCapture(task.getId(), captureCode);
        if (photo == null) {
            photo = new CollectionTaskPhotoFileEntity().setTaskId(task.getId()).setCaptureId(captureCode);
            fillPhoto(req, url, photo);
            taskPhotoFileEntityMapper.insert(photo);
        } else {
            fillPhoto(req, url, photo);
            taskPhotoFileEntityMapper.updateById(photo);
        }
        capture.setStatus(req.getPhotoState());
        taskPhotoItemEntityMapper.updateById(capture);
        return convertPhoto(photo);
    }

    @Override
    @TenantIgnore
    @Transactional(rollbackFor = Exception.class)
    public AppCollectionTaskVO.Resp submit(Long userId, Long id) {
        CollectionTaskEntity task = requireTask(userId, id);
        CollectionTaskFormEntity form = requireTaskForm(id);
        List<CollectionTaskPhotoItemEntity> photoItems = taskPhotoItemEntityMapper.selectListByTaskId(id);
        if (COMPLETED.equals(task.getStatus())) return convertCreatedTask(task, form, photoItems);
        if (!DRAFT.getCode().equals(task.getStatus()) && !PENDING_QUALITY.equals(task.getStatus())) {
            throw exception(APP_COLLECTION_TASK_NOT_DRAFT);
        }

        List<CollectionTaskPhotoItemEntity> required = photoItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.getRequiredFlag())).toList();
        Map<String, CollectionTaskPhotoFileEntity> photos = new HashMap<>();
        for (CollectionTaskPhotoFileEntity photo : taskPhotoFileEntityMapper.selectListByTaskId(id)) {
            photos.put(photo.getCaptureId(), photo);
        }
        long usable = required.stream().filter(capture -> {
            CollectionTaskPhotoFileEntity photo = photos.get(toCaptureCode(capture));
            return photo != null && ("PASSED".equals(photo.getPhotoState())
                    || "WARNING".equals(photo.getPhotoState()));
        }).count();
        if (usable < required.size()) {
            throw exception(APP_COLLECTION_INCOMPLETE, usable, required.size());
        }

        task.setStatus(COMPLETED);
        taskEntityMapper.updateById(task);
        return convertCreatedTask(task, form, photoItems);
    }

    private CollectionTaskEntity requireTask(Long userId, Long id) {
        CollectionTaskEntity task = taskEntityMapper.selectById(id);
        if (task == null) throw exception(APP_COLLECTION_TASK_NOT_EXISTS);
        if (!Objects.equals(task.getUserId(), userId)) throw exception(APP_COLLECTION_TASK_FORBIDDEN);
        return task;
    }

    private CollectionTaskFormEntity requireTaskForm(Long taskId) {
        CollectionTaskFormEntity form = taskFormEntityMapper.selectByTaskId(taskId);
        if (form == null) throw exception(APP_COLLECTION_TASK_NOT_EXISTS);
        return form;
    }

    private boolean sameCreateRequest(CollectionTaskEntity task, CollectionTaskFormEntity form,
                                      List<CollectionTaskPhotoItemEntity> photoItems,
                                      AppCollectionTaskVO.CreateReq req) {
        if (!Objects.equals(task.getTaskType(), req.getTaskType()) || form == null) {
            return false;
        }
        AppCollectionTaskVO.DrillBit drill = req.getDrillBit();
        AppCollectionTaskVO.Operation operation = req.getOperation();
        boolean sameForm = Objects.equals(form.getDrillCode(), drill.getCode())
                && Objects.equals(form.getDrillType(), drill.getType())
                && Objects.equals(form.getDrillModel(), drill.getModel())
                && Objects.equals(form.getDrillSize(), drill.getSize())
                && Objects.equals(form.getManufacturer(), drill.getManufacturer())
                && Objects.equals(form.getProductionBatch(), drill.getProductionBatch())
                && Objects.equals(form.getWellNumber(), operation.getWellNumber())
                && Objects.equals(form.getWellSection(), operation.getWellSection())
                && Objects.equals(form.getFormation(), operation.getFormation())
                && Objects.equals(form.getOperationTime(), operation.getOperationTime())
                && sameDecimal(form.getFootage(), operation.getFootage())
                && sameDecimal(form.getDrillingHours(), operation.getDrillingHours())
                && Objects.equals(form.getLocation(), operation.getLocation())
                && Objects.equals(form.getNotes(), operation.getNotes());
        return sameForm && samePhotoItems(photoItems, req.getBladeCount());
    }

    private boolean sameDecimal(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.compareTo(second) == 0;
    }

    private boolean samePhotoItems(List<CollectionTaskPhotoItemEntity> photoItems, int bladeCount) {
        if (photoItems.size() != bladeCount + 1) {
            return false;
        }
        long overallCount = photoItems.stream()
                .filter(item -> "OVERALL".equals(item.getPhotoType()) && item.getBladeNo() == null)
                .count();
        if (overallCount != 1) {
            return false;
        }
        for (int bladeNo = 1; bladeNo <= bladeCount; bladeNo++) {
            int expectedBladeNo = bladeNo;
            boolean exists = photoItems.stream().anyMatch(item -> "BLADE".equals(item.getPhotoType())
                    && Objects.equals(item.getBladeNo(), expectedBladeNo));
            if (!exists) {
                return false;
            }
        }
        return true;
    }

    private CollectionTaskFormEntity buildTaskForm(Long taskId, AppCollectionTaskVO.CreateReq req) {
        return buildTaskForm(taskId, req.getDrillBit(), req.getOperation());
    }

    private CollectionTaskFormEntity buildTaskForm(Long taskId, AppCollectionTaskVO.DrillBit drill,
                                                   AppCollectionTaskVO.Operation operation) {
        return fillTaskForm(new CollectionTaskFormEntity().setTaskId(taskId), drill, operation);
    }

    private CollectionTaskFormEntity fillTaskForm(CollectionTaskFormEntity form,
                                                  AppCollectionTaskVO.DrillBit drill,
                                                  AppCollectionTaskVO.Operation operation) {
        return form
                .setDrillCode(drill.getCode())
                .setDrillType(drill.getType())
                .setDrillModel(drill.getModel())
                .setDrillSize(drill.getSize())
                .setManufacturer(drill.getManufacturer())
                .setProductionBatch(drill.getProductionBatch())
                .setWellNumber(operation.getWellNumber())
                .setWellSection(operation.getWellSection())
                .setFormation(operation.getFormation())
                .setOperationTime(operation.getOperationTime())
                .setFootage(operation.getFootage())
                .setDrillingHours(operation.getDrillingHours())
                .setLocation(operation.getLocation())
                .setNotes(operation.getNotes());
    }

    private List<CollectionTaskPhotoItemEntity> createPhotoItems(Long taskId, int bladeCount) {
        List<CollectionTaskPhotoItemEntity> items = new ArrayList<>(bladeCount + 1);
        CollectionTaskPhotoItemEntity overall = new CollectionTaskPhotoItemEntity()
                .setTaskId(taskId)
                .setPhotoType("OVERALL")
                .setTitle("钻头全局图")
                .setRequiredFlag(true)
                .setSortNo(1)
                .setStatus("PENDING");
        taskPhotoItemEntityMapper.insert(overall);
        items.add(overall);
        for (int bladeNo = 1; bladeNo <= bladeCount; bladeNo++) {
            CollectionTaskPhotoItemEntity blade = new CollectionTaskPhotoItemEntity()
                    .setTaskId(taskId)
                    .setPhotoType("BLADE")
                    .setTitle(bladeNo + "号刀翼")
                    .setRequiredFlag(true)
                    .setBladeNo(bladeNo)
                    .setSortNo(bladeNo + 1)
                    .setStatus("PENDING");
            taskPhotoItemEntityMapper.insert(blade);
            items.add(blade);
        }
        return items;
    }

    private List<CollectionTaskPhotoItemEntity> replacePhotoItems(Long taskId, int bladeCount) {
        List<CollectionTaskPhotoItemEntity> existing = taskPhotoItemEntityMapper.selectListByTaskId(taskId);
        CollectionTaskPhotoItemEntity overall = null;
        Map<Integer, CollectionTaskPhotoItemEntity> blades = new HashMap<>();
        List<CollectionTaskPhotoItemEntity> obsolete = new ArrayList<>();
        for (CollectionTaskPhotoItemEntity item : existing) {
            if (overall == null && "OVERALL".equals(item.getPhotoType()) && item.getBladeNo() == null) {
                overall = item;
            } else if ("BLADE".equals(item.getPhotoType()) && item.getBladeNo() != null
                    && !blades.containsKey(item.getBladeNo())) {
                blades.put(item.getBladeNo(), item);
            } else {
                obsolete.add(item);
            }
        }

        List<CollectionTaskPhotoItemEntity> result = new ArrayList<>(bladeCount + 1);
        result.add(upsertPhotoItem(overall, taskId, "OVERALL", "钻头全局图", null, 1));
        for (int bladeNo = 1; bladeNo <= bladeCount; bladeNo++) {
            result.add(upsertPhotoItem(blades.remove(bladeNo), taskId, "BLADE",
                    bladeNo + "号刀翼", bladeNo, bladeNo + 1));
        }
        obsolete.addAll(blades.values());
        obsolete.forEach(item -> taskPhotoItemEntityMapper.deleteById(item.getId()));
        return result;
    }

    private CollectionTaskPhotoItemEntity upsertPhotoItem(CollectionTaskPhotoItemEntity current,
                                                          Long taskId, String photoType, String title,
                                                          Integer bladeNo, int sortNo) {
        CollectionTaskPhotoItemEntity item = current == null
                ? new CollectionTaskPhotoItemEntity().setTaskId(taskId).setStatus("PENDING")
                : current;
        item.setPhotoType(photoType);
        item.setTitle(title);
        item.setRequiredFlag(true);
        item.setBladeNo(bladeNo);
        item.setSortNo(sortNo);
        if (item.getId() == null) taskPhotoItemEntityMapper.insert(item);
        else taskPhotoItemEntityMapper.updateById(item);
        return item;
    }

    private AppCollectionTaskVO.Resp convertCreatedTask(CollectionTaskEntity task,
                                                        CollectionTaskFormEntity form,
                                                        List<CollectionTaskPhotoItemEntity> photoItems) {
        AppCollectionTaskVO.Resp result = new AppCollectionTaskVO.Resp();
        result.setId(task.getId());
        result.setClientId(task.getClientId());
        result.setTaskNo(task.getTaskNo());
        result.setTaskType(task.getTaskType());
        result.setStatus(task.getStatus());
        // 兼容当前 App 协议；数据库新模型不再持久化 version。
        result.setVersion(1);

        AppCollectionTaskVO.DrillBit drill = new AppCollectionTaskVO.DrillBit();
        drill.setCode(form.getDrillCode());
        drill.setType(form.getDrillType());
        drill.setModel(form.getDrillModel());
        drill.setSize(form.getDrillSize());
        drill.setManufacturer(form.getManufacturer());
        drill.setProductionBatch(form.getProductionBatch());
        result.setDrillBit(drill);

        AppCollectionTaskVO.Operation operation = new AppCollectionTaskVO.Operation();
        operation.setWellNumber(form.getWellNumber());
        operation.setWellSection(form.getWellSection());
        operation.setFormation(form.getFormation());
        operation.setOperationTime(form.getOperationTime());
        operation.setFootage(form.getFootage());
        operation.setDrillingHours(form.getDrillingHours());
        operation.setLocation(form.getLocation());
        operation.setNotes(form.getNotes());
        result.setOperation(operation);
        result.setCaptures(photoItems.stream().map(this::convertCreatedPhotoItem).toList());
        result.setCreatedAt(task.getCreateTime());
        result.setUpdatedAt(task.getUpdateTime());
        result.setDeleted(Boolean.TRUE.equals(task.getDeleted()));
        return result;
    }

    private AppCollectionTaskVO.Capture convertCreatedPhotoItem(CollectionTaskPhotoItemEntity item) {
        AppCollectionTaskVO.Capture result = new AppCollectionTaskVO.Capture();
        result.setId(item.getId());
        result.setCode(toCaptureCode(item));
        result.setTitle(item.getTitle());
        result.setDescription(item.getBladeNo() == null
                ? "钻头完整进入画面，保持背景简洁"
                : "拍摄" + item.getBladeNo() + "号刀翼全貌及主要切削齿");
        result.setRequired(item.getRequiredFlag());
        result.setBladeNo(item.getBladeNo());
        result.setSortNo(item.getSortNo());
        result.setState(item.getStatus());
        return result;
    }

    private String toCaptureCode(CollectionTaskPhotoItemEntity item) {
        return item.getBladeNo() == null ? "overall" : "blade-" + item.getBladeNo();
    }

    private void fillPhoto(AppCollectionVO.UploadReq req, String url, CollectionTaskPhotoFileEntity photo) {
        photo.setPhotoType(req.getPhotoType());
        photo.setPhotoState(req.getPhotoState());
        photo.setFileUrl(url);
    }

    private AppCollectionVO.PhotoResp convertPhoto(CollectionTaskPhotoFileEntity photo) {
        AppCollectionVO.PhotoResp result = new AppCollectionVO.PhotoResp();
        result.setCaptureId(photo.getCaptureId());
        result.setPhotoType(photo.getPhotoType());
        result.setPhotoState(photo.getPhotoState());
        result.setFileUrl(photo.getFileUrl());
        return result;
    }
}
