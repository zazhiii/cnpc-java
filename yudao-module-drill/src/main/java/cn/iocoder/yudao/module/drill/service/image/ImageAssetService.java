package cn.iocoder.yudao.module.drill.service.image;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.*;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;

public interface ImageAssetService {

    PageResult<ImageAssetDO> getAssetPage(ImageAssetPageReqVO pageReqVO);

    ImageAssetDO getAsset(Long id);

    ImageImportRespVO uploadAndImport(ImageAssetUploadReqVO uploadReqVO);

    FilePresignedUrlRespVO createPresignedUpload(ImagePresignedUploadReqVO reqVO);

    ImageImportRespVO completeImport(ImageImportCompleteReqVO reqVO);

    void updateAssetMetadata(ImageAssetUpdateMetadataReqVO updateReqVO);

}
