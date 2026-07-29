package cn.iocoder.yudao.module.drill.service.image;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchCreateReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageBatchDO;

public interface ImageBatchService {

    Long createBatch(ImageBatchCreateReqVO createReqVO);

    PageResult<ImageBatchDO> getBatchPage(ImageBatchPageReqVO pageReqVO);

    ImageBatchDO getBatch(Long id);

    ImageBatchDO validateBatchForImport(Long id);

    void archiveBatch(Long id);

    void updateImportStatistics(Long id, int total, int success, int failed,
                                int duplicate, int pendingMetadata);

}
