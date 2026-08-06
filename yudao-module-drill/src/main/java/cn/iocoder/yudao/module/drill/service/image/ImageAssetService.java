package cn.iocoder.yudao.module.drill.service.image;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageAssetPageReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageAssetUpdateMetadataReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;

import java.util.Collection;
import java.util.List;

public interface ImageAssetService {

    PageResult<ImageAssetDO> getAssetPage(ImageAssetPageReqVO pageReqVO);

    ImageAssetDO getAsset(Long id);

    ImageAssetDO getAssetForUpdate(Long id);

    List<ImageAssetDO> getAssets(Collection<Long> ids);

    List<ImageAssetDO> getAssetsByBatchId(Long batchId);

    byte[] getAssetContent(Long id);

    void validateAssetsAvailable(Collection<Long> ids);

    void updateAssetMetadata(ImageAssetUpdateMetadataReqVO updateReqVO);

}
