package cn.iocoder.yudao.module.drill.service.image;

import cn.hutool.core.util.RandomUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchCreateReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageBatchDO;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.EnhanceJobMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.ImageVersionMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageAssetMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageBatchMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageImportRecordMapper;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_BATCH_HAS_ENHANCE_JOBS;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_BATCH_NOT_EXISTS;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_BATCH_STATUS_INVALID;
import static cn.iocoder.yudao.module.drill.enums.ImageImportConstants.*;

@Service
public class ImageBatchServiceImpl implements ImageBatchService {

    private static final DateTimeFormatter BATCH_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> IMPORTABLE_STATUSES = Set.of(
            BATCH_STATUS_IMPORTING, BATCH_STATUS_PENDING_METADATA, BATCH_STATUS_PENDING_PROCESS,
            BATCH_STATUS_PARTIAL_SUCCESS);

    @Resource
    private ImageBatchMapper imageBatchMapper;
    @Resource
    private ImageAssetMapper imageAssetMapper;
    @Resource
    private ImageImportRecordMapper imageImportRecordMapper;
    @Resource
    private EnhanceJobMapper enhanceJobMapper;
    @Resource
    private ImageVersionMapper imageVersionMapper;
    @Resource
    private FileService fileService;

    @Override
    public Long createBatch(ImageBatchCreateReqVO createReqVO) {
        ImageBatchDO batch = BeanUtils.toBean(createReqVO, ImageBatchDO.class);
        batch.setBatchNo("IMG-" + LocalDateTime.now().format(BATCH_NO_FORMATTER)
                + "-" + RandomUtil.randomNumbers(4));
        batch.setSourceType(SOURCE_LOCAL_UPLOAD);
        batch.setStatus(BATCH_STATUS_IMPORTING);
        batch.setTotalCount(0);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setDuplicateCount(0);
        batch.setPendingMetadataCount(0);
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
    public ImageBatchDO validateBatchForImport(Long id) {
        ImageBatchDO batch = getBatch(id);
        if (!IMPORTABLE_STATUSES.contains(batch.getStatus())) {
            throw exception(IMAGE_BATCH_STATUS_INVALID);
        }
        return batch;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long id) throws Exception {
        getBatch(id);
        if (enhanceJobMapper.selectCountByBatchId(id) > 0) {
            throw exception(IMAGE_BATCH_HAS_ENHANCE_JOBS);
        }
        List<ImageAssetDO> assets = imageAssetMapper.selectByBatchId(id);
        if (imageVersionMapper.selectCountByAssetIds(
                assets.stream().map(ImageAssetDO::getId).toList()) > 0) {
            throw exception(IMAGE_BATCH_HAS_ENHANCE_JOBS);
        }
        List<Long> fileIds = assets.stream()
                .map(ImageAssetDO::getOriginalFileId)
                .filter(fileId -> fileId != null)
                .distinct()
                .toList();
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

}
