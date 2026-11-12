package cn.iocoder.yudao.module.drill.service.collection;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.Photo;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.PredictionDetail;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.PredictionSummary;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.TaskDetail;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.TaskForm;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.TaskPageReq;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.TaskSummary;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskFormEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoFileEntity;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoItemEntity;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionAdminMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskEntityMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskFormEntityMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskPhotoFileEntityMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.collection.CollectionTaskPhotoItemEntityMapper;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.APP_COLLECTION_TASK_NOT_EXISTS;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.COLLECTION_BLADE_PHOTO_NOT_EXISTS;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.COLLECTION_PREDICTION_NOT_EXISTS;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

@Service
@Slf4j
public class CollectionAdminService {

    private static final Pattern BLADE_CAPTURE_ID = Pattern.compile("blade-[0-9]+");

    @Resource
    private CollectionAdminMapper adminMapper;
    @Resource
    private CollectionTaskEntityMapper taskMapper;
    @Resource
    private CollectionTaskFormEntityMapper formMapper;
    @Resource
    private CollectionTaskPhotoItemEntityMapper itemMapper;
    @Resource
    private CollectionTaskPhotoFileEntityMapper photoMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private FileService fileService;

    public PageResult<TaskSummary> getTaskPage(TaskPageReq req) {
        String keyword = req.getKeyword() == null || req.getKeyword().isBlank() ? null : req.getKeyword().trim();
        LocalDateTime from = req.getUpdatedFrom() == null ? null : req.getUpdatedFrom().atStartOfDay();
        LocalDateTime before = req.getUpdatedTo() == null ? null : req.getUpdatedTo().plusDays(1).atStartOfDay();
        IPage<TaskSummary> page = adminMapper.selectTaskPage(
                MyBatisUtils.buildPage(req), keyword, req.getTaskType(), req.getStatus(), from, before);
        List<TaskSummary> tasks = page.getRecords();
        if (tasks.isEmpty()) {
            return PageResult.empty(page.getTotal());
        }

        List<Long> taskIds = tasks.stream().map(TaskSummary::getId).toList();
        Map<Long, List<CollectionTaskPhotoItemEntity>> itemsByTask = itemMapper.selectList(
                new LambdaQueryWrapperX<CollectionTaskPhotoItemEntity>()
                        .select(CollectionTaskPhotoItemEntity::getTaskId, CollectionTaskPhotoItemEntity::getBladeNo,
                                CollectionTaskPhotoItemEntity::getRequiredFlag)
                        .in(CollectionTaskPhotoItemEntity::getTaskId, taskIds))
                .stream().collect(groupingBy(CollectionTaskPhotoItemEntity::getTaskId));
        Map<Long, Set<String>> uploadedByTask = photoMapper.selectList(
                new LambdaQueryWrapperX<CollectionTaskPhotoFileEntity>()
                        .select(CollectionTaskPhotoFileEntity::getTaskId, CollectionTaskPhotoFileEntity::getCaptureId)
                        .in(CollectionTaskPhotoFileEntity::getTaskId, taskIds))
                .stream().collect(groupingBy(CollectionTaskPhotoFileEntity::getTaskId,
                        mapping(CollectionTaskPhotoFileEntity::getCaptureId, toSet())));
        Map<Long, AdminUserRespDTO> users = adminUserApi.getUserMap(
                tasks.stream().map(TaskSummary::getUserId).collect(toSet()));

        for (TaskSummary task : tasks) {
            setCollector(task, users.get(task.getUserId()));
            setProgress(task, itemsByTask.getOrDefault(task.getId(), List.of()),
                    uploadedByTask.getOrDefault(task.getId(), Set.of()));
        }
        return new PageResult<>(tasks, page.getTotal());
    }

    public TaskDetail getTask(Long taskId) {
        CollectionTaskEntity entity = requireTask(taskId);
        TaskSummary task = BeanUtils.toBean(entity, TaskSummary.class);
        setCollector(task, adminUserApi.getUser(entity.getUserId()));
        TaskForm form = BeanUtils.toBean(formMapper.selectList(
                new LambdaQueryWrapperX<CollectionTaskFormEntity>()
                        .eq(CollectionTaskFormEntity::getTaskId, taskId)
                        .orderByDesc(CollectionTaskFormEntity::getId)
                        .last("LIMIT 1")).stream().findFirst().orElse(null), TaskForm.class);
        if (form != null) {
            task.setDrillCode(form.getDrillCode());
            task.setDrillModel(form.getDrillModel());
            task.setWellNumber(form.getWellNumber());
        }

        List<CollectionTaskPhotoItemEntity> items = itemMapper.selectListByTaskId(taskId);
        Map<String, CollectionTaskPhotoFileEntity> filesByCapture = new LinkedHashMap<>();
        photoMapper.selectList(new LambdaQueryWrapperX<CollectionTaskPhotoFileEntity>()
                        .eq(CollectionTaskPhotoFileEntity::getTaskId, taskId)
                        .orderByDesc(CollectionTaskPhotoFileEntity::getUpdateTime, CollectionTaskPhotoFileEntity::getId))
                .forEach(file -> filesByCapture.putIfAbsent(file.getCaptureId(), file));
        setProgress(task, items, filesByCapture.keySet());

        List<Photo> photos = new ArrayList<>();
        for (CollectionTaskPhotoItemEntity item : items) {
            String captureId = captureId(item);
            CollectionTaskPhotoFileEntity file = filesByCapture.remove(captureId);
            photos.add(new Photo(captureId, item.getTitle(), Boolean.TRUE.equals(item.getRequiredFlag()),
                    item.getBladeNo() != null, file == null ? item.getStatus() : file.getPhotoState(),
                    file == null ? null : file.getId(), file == null ? null : previewUrl(file)));
        }
        filesByCapture.values().stream().sorted(Comparator.comparing(CollectionTaskPhotoFileEntity::getId))
                .map(this::toUnlistedPhoto).forEach(photos::add);
        return new TaskDetail(task, form, photos);
    }

    public PageResult<PredictionSummary> getPredictionPage(Long taskId, Long photoId, PageParam req) {
        requireBladePhoto(taskId, photoId);
        IPage<PredictionSummary> page = adminMapper.selectPredictionPage(MyBatisUtils.buildPage(req), photoId);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    public PredictionDetail getPrediction(Long taskId, Long photoId, Long predictionId) {
        requireBladePhoto(taskId, photoId);
        PredictionSummary prediction = adminMapper.selectPrediction(photoId, predictionId);
        if (prediction == null) {
            throw exception(COLLECTION_PREDICTION_NOT_EXISTS);
        }
        return new PredictionDetail(prediction,
                "SUCCESS".equals(prediction.getStatus()) ? adminMapper.selectTeeth(predictionId) : List.of());
    }

    private CollectionTaskEntity requireTask(Long taskId) {
        CollectionTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw exception(APP_COLLECTION_TASK_NOT_EXISTS);
        }
        return task;
    }

    private void requireBladePhoto(Long taskId, Long photoId) {
        requireTask(taskId);
        CollectionTaskPhotoFileEntity file = photoMapper.selectById(photoId);
        if (file == null || !Objects.equals(taskId, file.getTaskId()) || !isBladePhoto(file)) {
            throw exception(COLLECTION_BLADE_PHOTO_NOT_EXISTS);
        }
    }

    private static void setCollector(TaskSummary task, AdminUserRespDTO user) {
        if (user != null) {
            task.setCollectorName(user.getNickname());
        }
    }

    private static void setProgress(TaskSummary task, List<CollectionTaskPhotoItemEntity> items, Set<String> uploaded) {
        if (items.isEmpty()) {
            return;
        }
        Set<String> required = items.stream().filter(item -> Boolean.TRUE.equals(item.getRequiredFlag()))
                .map(CollectionAdminService::captureId).collect(toSet());
        task.setRequiredPhotoCount(required.size());
        task.setUploadedRequiredPhotoCount((int) required.stream().filter(uploaded::contains).count());
    }

    private static String captureId(CollectionTaskPhotoItemEntity item) {
        return item.getBladeNo() == null ? "overall" : "blade-" + item.getBladeNo();
    }

    private static boolean isBladePhoto(CollectionTaskPhotoFileEntity file) {
        String captureId = file.getCaptureId();
        return !"overall".equals(captureId)
                && (BLADE_CAPTURE_ID.matcher(captureId).matches() || "BLADE".equals(file.getPhotoType()));
    }

    private Photo toUnlistedPhoto(CollectionTaskPhotoFileEntity file) {
        String captureId = file.getCaptureId();
        String title = switch (captureId) {
            case "overall" -> "钻头全局图";
            default -> BLADE_CAPTURE_ID.matcher(captureId).matches()
                    ? captureId.substring("blade-".length()) + "号刀翼" : file.getPhotoType();
        };
        return new Photo(captureId, title, false, isBladePhoto(file),
                file.getPhotoState(), file.getId(), previewUrl(file));
    }

    private String previewUrl(CollectionTaskPhotoFileEntity photo) {
        String url = photo.getFileUrl();
        if (url == null || !url.contains("X-Amz-Signature=")) {
            return url;
        }
        try {
            FileDO file = fileService.getFileByUrl(url);
            if (file != null) {
                return fileService.presignGetUrl(file.getConfigId(), file.getPath(), 3600);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not renew preview URL for collection photo {}", photo.getId());
        }
        return url;
    }
}
