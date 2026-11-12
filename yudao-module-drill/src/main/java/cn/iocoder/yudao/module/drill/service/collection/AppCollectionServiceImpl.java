package cn.iocoder.yudao.module.drill.service.collection;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoFileEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoItemEntity;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskEntityMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskPhotoFileEntityMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskPhotoItemEntityMapper;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;

@Service
@TenantIgnore
public class AppCollectionServiceImpl implements AppCollectionService {
    private static final int REQUIRED_PHOTO_COUNT = 6;
    private static final String STATUS_PENDING_UPLOAD = "PENDING_UPLOAD";
    private static final String STATUS_COMPLETED = "COMPLETED";

    @Resource private CollectionTaskEntityMapper taskEntityMapper;
    @Resource private CollectionTaskPhotoFileEntityMapper taskPhotoFileEntityMapper;
    @Resource private CollectionTaskPhotoItemEntityMapper taskPhotoItemEntityMapper;
    @Resource private FileService fileService;

    @Override
    public AppCollectionVO.TaskResp sync(Long userId, AppCollectionVO.SyncReq req) {
        CollectionTaskEntity task = taskEntityMapper.selectByUserIdAndClientId(userId, req.getClientId());
        if (task == null) {
            task = new CollectionTaskEntity()
                    .setClientId(req.getClientId())
                    .setUserId(userId);
            copy(req, task);
            taskEntityMapper.insert(task);
        } else {
            copy(req, task);
            taskEntityMapper.updateById(task);
        }
        return convert(task);
    }

    @Override
    public List<AppCollectionVO.TaskResp> list(Long userId) {
        return taskEntityMapper.selectListByUserId(userId).stream().map(this::convert).toList();
    }

    @Override
    public AppCollectionVO.StatisticsResp statistics(Long userId) {
        AppCollectionVO.StatisticsResp resp = new AppCollectionVO.StatisticsResp();
        resp.setTotalTaskCount(taskEntityMapper.selectCountByUserIdAndStatus(userId, null));
        resp.setPendingUploadTaskCount(
                taskEntityMapper.selectCountByUserIdAndStatus(userId, STATUS_PENDING_UPLOAD));
        resp.setCompletedTaskCount(
                taskEntityMapper.selectCountByUserIdAndStatus(userId, STATUS_COMPLETED));
        return resp;
    }

    @Override
    public AppCollectionVO.TaskResp get(Long userId, String clientId) {
        return convert(requireTask(userId, clientId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCollectionVO.PhotoResp upload(Long userId, String clientId, String captureId,
                                            AppCollectionVO.UploadReq req) {
        CollectionTaskEntity task = requireTask(userId, clientId);
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
                taskPhotoFileEntityMapper.selectByTaskAndCapture(task.getId(), captureId);
        if (photo == null) {
            photo = new CollectionTaskPhotoFileEntity().setTaskId(task.getId()).setCaptureId(captureId);
            fillPhoto(req, url, photo);
            taskPhotoFileEntityMapper.insert(photo);
        } else {
            fillPhoto(req, url, photo);
            taskPhotoFileEntityMapper.updateById(photo);
        }
        return convert(photo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCollectionVO.TaskResp submit(Long userId, String clientId) {
        CollectionTaskEntity task = requireTask(userId, clientId);
        if (STATUS_COMPLETED.equals(task.getStatus())) return convert(task);
        List<CollectionTaskPhotoFileEntity> photos =
                taskPhotoFileEntityMapper.selectListByTaskId(task.getId());
        List<CollectionTaskPhotoItemEntity> required =
                taskPhotoItemEntityMapper.selectListByTaskId(task.getId()).stream()
                .filter(capture -> Boolean.TRUE.equals(capture.getRequiredFlag())).toList();
        long usable;
        int requiredCount;
        if (required.isEmpty()) {
            // 兼容尚未生成动态拍摄清单的旧任务。
            requiredCount = REQUIRED_PHOTO_COUNT;
            usable = photos.stream().filter(photo -> isUsable(photo.getPhotoState())).count();
        } else {
            requiredCount = required.size();
            Map<String, CollectionTaskPhotoFileEntity> byCapture = photos.stream().collect(Collectors.toMap(
                    CollectionTaskPhotoFileEntity::getCaptureId, Function.identity(), (first, last) -> last));
            usable = required.stream().filter(capture -> {
                CollectionTaskPhotoFileEntity photo = byCapture.get(toCaptureCode(capture));
                return photo != null && isUsable(photo.getPhotoState());
            }).count();
        }
        if (usable < requiredCount) {
            throw exception(APP_COLLECTION_INCOMPLETE, usable, requiredCount);
        }
        task.setStatus(STATUS_COMPLETED);
        taskEntityMapper.updateById(task);
        return convert(task);
    }

    private boolean isUsable(String photoState) {
        return "PASSED".equals(photoState) || "WARNING".equals(photoState);
    }

    private CollectionTaskEntity requireTask(Long userId, String clientId) {
        CollectionTaskEntity task = taskEntityMapper.selectByUserIdAndClientId(userId, clientId);
        if (task == null) throw exception(APP_COLLECTION_TASK_NOT_EXISTS);
        return task;
    }

    private void copy(AppCollectionVO.SyncReq req, CollectionTaskEntity task) {
        task.setTaskNo(req.getTaskNo());
        task.setTaskType(req.getTaskType());
        task.setStatus(req.getStatus());
    }

    private void fillPhoto(AppCollectionVO.UploadReq req, String url, CollectionTaskPhotoFileEntity photo) {
        photo.setPhotoType(req.getPhotoType());
        photo.setPhotoState(req.getPhotoState());
        photo.setFileUrl(url);
    }

    private AppCollectionVO.TaskResp convert(CollectionTaskEntity task) {
        AppCollectionVO.TaskResp resp = new AppCollectionVO.TaskResp();
        resp.setServerId(task.getId());
        resp.setClientId(task.getClientId());
        resp.setTaskNo(task.getTaskNo());
        resp.setTaskType(task.getTaskType());
        resp.setStatus(task.getStatus());
        resp.setUpdateTime(task.getUpdateTime());
        resp.setPhotos(taskPhotoFileEntityMapper.selectListByTaskId(task.getId()).stream()
                .map(this::convert).toList());
        return resp;
    }

    private AppCollectionVO.PhotoResp convert(CollectionTaskPhotoFileEntity photo) {
        AppCollectionVO.PhotoResp resp = new AppCollectionVO.PhotoResp();
        resp.setCaptureId(photo.getCaptureId());
        resp.setPhotoType(photo.getPhotoType());
        resp.setPhotoState(photo.getPhotoState());
        resp.setFileUrl(photo.getFileUrl());
        return resp;
    }

    private String toCaptureCode(CollectionTaskPhotoItemEntity item) {
        return item.getBladeNo() == null ? "overall" : "blade-" + item.getBladeNo();
    }
}
