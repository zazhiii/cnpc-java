package cn.iocoder.yudao.module.drill.service.image;

import cn.hutool.core.util.RandomUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchCreateReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageBatchDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportJobDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportPackageDO;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageAssetMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageBatchMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageImportRecordMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageImportJobMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageImportPackageMapper;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Collection;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_BATCH_EMPTY;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_BATCH_NOT_EXISTS;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_BATCH_NOT_READY;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_BATCH_REFERENCED;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_BATCH_STATUS_INVALID;
import static cn.iocoder.yudao.module.drill.enums.ImageImportConstants.*;

@Service
public class ImageBatchServiceImpl implements ImageBatchService {

    private static final DateTimeFormatter BATCH_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    @Resource
    private ImageBatchMapper imageBatchMapper;
    @Resource
    private ImageAssetMapper imageAssetMapper;
    @Resource
    private ImageImportRecordMapper imageImportRecordMapper;
    @Resource
    private ImageImportJobMapper imageImportJobMapper;
    @Resource
    private ImageImportPackageMapper imageImportPackageMapper;
    @Resource
    private ImageAssetReferenceService imageAssetReferenceService;
    @Resource
    private FileService fileService;

    @Override
    public Long createBatch(ImageBatchCreateReqVO createReqVO) {
        ImageBatchDO batch = BeanUtils.toBean(createReqVO, ImageBatchDO.class);
        batch.setBatchNo("IMG-" + LocalDateTime.now().format(BATCH_NO_FORMATTER)
                + "-" + RandomUtil.randomNumbers(4));
        batch.setSourceType(SOURCE_LOCAL_UPLOAD);
        batch.setStatus(BATCH_STATUS_IMPORTING); // 状态：导入中
        batch.setTotalCount(0);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setDuplicateCount(0);
        batch.setPendingMetadataCount(0);
        batch.setSourcePackageCount(0);
        batch.setFailedPackageCount(0);
        imageBatchMapper.insert(batch);
        return batch.getId();
    }

    @Override
    public Long createZipBatch(String name, int sourcePackageCount) {
        ImageBatchDO batch = new ImageBatchDO();
        batch.setBatchNo("IMG-" + LocalDateTime.now().format(BATCH_NO_FORMATTER)
                + "-" + RandomUtil.randomNumbers(4));
        batch.setName(name);
        batch.setSourceType(SOURCE_ZIP_DIRECTORY);
        batch.setStatus(BATCH_STATUS_IMPORTING);
        batch.setTotalCount(0);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setDuplicateCount(0);
        batch.setPendingMetadataCount(0);
        batch.setSourcePackageCount(sourcePackageCount);
        batch.setFailedPackageCount(0);
        imageBatchMapper.insert(batch);
        return batch.getId();
    }

    @Override
    public PageResult<ImageBatchDO> getBatchPage(ImageBatchPageReqVO pageReqVO) {
        return imageBatchMapper.selectPage(pageReqVO);
    }

    @Override
    public ImageBatchDO getBatch(Long id) {
        ImageBatchDO batch = imageBatchMapper.selectById(id);
        if (batch == null) {
            throw exception(IMAGE_BATCH_NOT_EXISTS);
        }
        return batch;
    }

    @Override
    public List<ImageBatchDO> getBatches(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of() : imageBatchMapper.selectByIds(ids);
    }

    @Override
    public ImageBatchDO validateBatchForImport(Long id) {
        ImageBatchDO batch = getBatch(id);
        if (!BATCH_STATUS_IMPORTING.equals(batch.getStatus())) {
            throw exception(IMAGE_BATCH_STATUS_INVALID);
        }
        return batch;
    }

    @Override
    public ImageBatchDO validateBatchReady(Long id) {
        ImageBatchDO batch = getBatch(id);
        if (!BATCH_STATUS_READY.equals(batch.getStatus())
                && !BATCH_STATUS_PARTIAL_READY.equals(batch.getStatus())) {
            throw exception(IMAGE_BATCH_NOT_READY);
        }
        return batch;
    }

    @Override
    public void finishImport(Long id) {
        finishImport(id, false);
    }

    @Override
    public void finishImport(Long id, boolean sourcePartial) {
        ImageBatchDO batch = validateBatchForImport(id);
        if (batch.getTotalCount() == null || batch.getTotalCount() == 0) {
            throw exception(IMAGE_BATCH_EMPTY);
        }
        if (imageBatchMapper.finishImport(id, sourcePartial) == 0) {
            throw exception(IMAGE_BATCH_STATUS_INVALID);
        }
    }

    @Override
    public void cancelImport(Long id) {
        if (imageBatchMapper.cancelImport(id) == 0) {
            throw exception(IMAGE_BATCH_STATUS_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long id) throws Exception {
        getBatch(id);
        List<ImageAssetDO> assets = imageAssetMapper.selectByBatchId(id);
//        if (imageAssetReferenceService.hasReferences(
//                assets.stream().map(ImageAssetDO::getId).toList())) {
//            throw exception(IMAGE_BATCH_REFERENCED);
//        }
        List<Long> fileIds = assets.stream()
                .map(ImageAssetDO::getOriginalFileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        ImageImportJobDO importJob = imageImportJobMapper.selectByBatchId(id);
        if (importJob != null) {
            List<Long> archiveFileIds = imageImportPackageMapper.selectByJobId(importJob.getId()).stream()
                    .map(ImageImportPackageDO::getArchiveFileId)
                    .filter(Objects::nonNull).toList();
            if (!archiveFileIds.isEmpty()) {
                fileService.deleteFileList(archiveFileIds);
            }
            imageImportPackageMapper.deleteByJobId(importJob.getId());
            imageImportJobMapper.deleteById(importJob.getId());
        }
        if (!fileIds.isEmpty()) {
            fileService.deleteFileList(fileIds);
        }
        imageImportRecordMapper.deleteByBatchId(id);
        imageAssetMapper.deleteByBatchId(id);
        imageBatchMapper.deleteById(id);
    }

    @Override
    public void updateImportStatistics(Long id, int total, int success, int failed,
                                       int duplicate, int pendingMetadata) {
        imageBatchMapper.updateImportStatistics(id, total, success, failed,
                duplicate, pendingMetadata);
    }

    @Override
    public void refreshMetadataStatistics(Long id) {
        imageBatchMapper.refreshMetadataStatistics(id);
    }

    @Override
    public void replaceImportStatistics(Long id, int total, int success, int failed,
                                        int duplicate, int pendingMetadata, int failedPackages) {
        imageBatchMapper.replaceImportStatistics(id, total, success, failed, duplicate,
                pendingMetadata, failedPackages);
    }

}
