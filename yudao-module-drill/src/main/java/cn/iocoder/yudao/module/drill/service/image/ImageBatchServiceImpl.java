package cn.iocoder.yudao.module.drill.service.image;

import cn.hutool.core.util.RandomUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchCreateReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageBatchDO;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageBatchMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
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

    @Override
    public Long createBatch(ImageBatchCreateReqVO createReqVO) {
        ImageBatchDO batch = BeanUtils.toBean(createReqVO, ImageBatchDO.class);
        batch.setBatchNo("IMG-" + LocalDateTime.now().format(BATCH_NO_FORMATTER)
                + "-" + RandomUtil.randomNumbers(4));
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
    public void archiveBatch(Long id) {
        ImageBatchDO batch = getBatch(id);
        if (BATCH_STATUS_ARCHIVED.equals(batch.getStatus())) {
            return;
        }
        if (BATCH_STATUS_IMPORTING.equals(batch.getStatus())) {
            throw exception(IMAGE_BATCH_STATUS_INVALID);
        }
        imageBatchMapper.updateById(new ImageBatchDO()
                .setId(id)
                .setStatus(BATCH_STATUS_ARCHIVED));
    }

    @Override
    public void updateImportStatistics(Long id, int total, int success, int failed,
                                       int duplicate, int pendingMetadata) {
        imageBatchMapper.updateImportStatistics(id, total, success, failed,
                duplicate, pendingMetadata);
    }

}
