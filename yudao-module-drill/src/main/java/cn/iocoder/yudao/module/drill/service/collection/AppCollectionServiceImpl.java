package cn.iocoder.yudao.module.drill.service.collection;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionCaptureDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionPhotoDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionCaptureMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionPhotoMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;

@Service
public class AppCollectionServiceImpl implements AppCollectionService {
    private static final int REQUIRED_PHOTO_COUNT = 6;
    private static final String STATUS_PENDING_UPLOAD = "PENDING_UPLOAD";
    private static final String STATUS_COMPLETED = "COMPLETED";

    @Resource private CollectionTaskMapper taskMapper;
    @Resource private CollectionPhotoMapper photoMapper;
    @Resource private CollectionCaptureMapper captureMapper;
    @Resource private FileService fileService;

    @Override
    public AppCollectionVO.TaskResp sync(Long userId, AppCollectionVO.SyncReq req) {
        CollectionTaskDO task = taskMapper.selectByClientId(userId, req.getClientId());
        if (task == null) {
            task = new CollectionTaskDO()
                    .setClientId(req.getClientId())
                    .setCollectorUserId(userId);
            copy(req, task);
            taskMapper.insert(task);
        } else {
            copy(req, task);
            taskMapper.updateById(task);
        }
        return convert(task);
    }

    @Override
    public List<AppCollectionVO.TaskResp> list(Long userId) {
        return taskMapper.selectListByUser(userId).stream().map(this::convert).toList();
    }

    @Override
    public AppCollectionVO.StatisticsResp statistics(Long userId) {
        AppCollectionVO.StatisticsResp resp = new AppCollectionVO.StatisticsResp();
        resp.setTotalTaskCount(taskMapper.selectCountByUserAndStatus(userId, null));
        resp.setPendingUploadTaskCount(taskMapper.selectCountByUserAndStatus(userId, STATUS_PENDING_UPLOAD));
        resp.setCompletedTaskCount(taskMapper.selectCountByUserAndStatus(userId, STATUS_COMPLETED));
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
        CollectionTaskDO task = requireTask(userId, clientId);
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
        CollectionPhotoDO photo = photoMapper.selectByTaskAndCapture(task.getId(), captureId);
        if (photo == null) {
            photo = new CollectionPhotoDO().setTaskId(task.getId()).setCaptureId(captureId);
            fillPhoto(req, url, photo);
            photoMapper.insert(photo);
        } else {
            fillPhoto(req, url, photo);
            photoMapper.updateById(photo);
        }
        return convert(photo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCollectionVO.TaskResp submit(Long userId, String clientId) {
        CollectionTaskDO task = requireTask(userId, clientId);
        if (STATUS_COMPLETED.equals(task.getStatus())) return convert(task);
        List<CollectionPhotoDO> photos = photoMapper.selectListByTask(task.getId());
        List<CollectionCaptureDO> required = captureMapper.selectByTaskId(task.getId()).stream()
                .filter(capture -> Boolean.TRUE.equals(capture.getRequiredFlag())).toList();
        long usable;
        int requiredCount;
        if (required.isEmpty()) {
            // 兼容尚未生成动态拍摄清单的旧任务。
            requiredCount = REQUIRED_PHOTO_COUNT;
            usable = photos.stream().filter(photo -> isUsable(photo.getPhotoState())).count();
        } else {
            requiredCount = required.size();
            Map<String, CollectionPhotoDO> byCapture = photos.stream().collect(Collectors.toMap(
                    CollectionPhotoDO::getCaptureId, Function.identity(), (first, last) -> last));
            usable = required.stream().filter(capture -> {
                CollectionPhotoDO photo = byCapture.get(capture.getCaptureCode());
                return photo != null && isUsable(photo.getPhotoState());
            }).count();
        }
        if (usable < requiredCount) {
            throw exception(APP_COLLECTION_INCOMPLETE, usable, requiredCount);
        }
        task.setStatus(STATUS_COMPLETED);
        task.setSubmitTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return convert(task);
    }

    private boolean isUsable(String photoState) {
        return "PASSED".equals(photoState) || "WARNING".equals(photoState);
    }

    private CollectionTaskDO requireTask(Long userId, String clientId) {
        CollectionTaskDO task = taskMapper.selectByClientId(userId, clientId);
        if (task == null) throw exception(APP_COLLECTION_TASK_NOT_EXISTS);
        return task;
    }

    private void copy(AppCollectionVO.SyncReq req, CollectionTaskDO task) {
        task.setTaskNo(req.getTaskNo());
        task.setTaskType(req.getTaskType());
        task.setStatus(req.getStatus());
        task.setTaskJson(req.getTaskJson());
    }

    private void fillPhoto(AppCollectionVO.UploadReq req, String url, CollectionPhotoDO photo) {
        photo.setPhotoType(req.getPhotoType());
        photo.setPhotoState(req.getPhotoState());
        photo.setFileUrl(url);
        photo.setAuditRegionJson(req.getAuditRegionJson());
        photo.setAuditReportJson(req.getAuditReportJson());
    }

    private AppCollectionVO.TaskResp convert(CollectionTaskDO task) {
        AppCollectionVO.TaskResp resp = new AppCollectionVO.TaskResp();
        resp.setServerId(task.getId());
        resp.setClientId(task.getClientId());
        resp.setTaskNo(task.getTaskNo());
        resp.setTaskType(task.getTaskType());
        resp.setStatus(task.getStatus());
        resp.setTaskJson(task.getTaskJson());
        resp.setResultJson(task.getResultJson());
        resp.setUpdateTime(task.getUpdateTime());
        resp.setPhotos(photoMapper.selectListByTask(task.getId()).stream().map(this::convert).toList());
        return resp;
    }

    private AppCollectionVO.PhotoResp convert(CollectionPhotoDO photo) {
        AppCollectionVO.PhotoResp resp = new AppCollectionVO.PhotoResp();
        resp.setCaptureId(photo.getCaptureId());
        resp.setPhotoType(photo.getPhotoType());
        resp.setPhotoState(photo.getPhotoState());
        resp.setFileUrl(photo.getFileUrl());
        resp.setAuditRegionJson(photo.getAuditRegionJson());
        resp.setAuditReportJson(photo.getAuditReportJson());
        return resp;
    }
}
