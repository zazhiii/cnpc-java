package cn.iocoder.yudao.module.drill.service.image;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchCreateReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageBatchDO;

import java.util.Collection;
import java.util.List;

public interface ImageBatchService {

    /**
     * 创建数据批次
     * @param createReqVO
     * @return
     */
    Long createBatch(ImageBatchCreateReqVO createReqVO);

    PageResult<ImageBatchDO> getBatchPage(ImageBatchPageReqVO pageReqVO);

    ImageBatchDO getBatch(Long id);

    List<ImageBatchDO> getBatches(Collection<Long> ids);

    ImageBatchDO validateBatchForImport(Long id);

    ImageBatchDO validateBatchReady(Long id);

    void finishImport(Long id);

    void deleteBatch(Long id) throws Exception;

    void updateImportStatistics(Long id, int total, int success, int failed,
                                int duplicate, int pendingMetadata);

    void refreshMetadataStatistics(Long id);

}
