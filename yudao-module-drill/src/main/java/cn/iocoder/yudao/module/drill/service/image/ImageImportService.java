package cn.iocoder.yudao.module.drill.service.image;

import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageAssetUploadReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageImportCompleteReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageImportRespVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImagePresignedUploadReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.yudao.module.drill.service.image.model.ArchiveImageImportRequest;

/** 图片导入服务。只负责图片进入资产库，不触发增强或质检。 */
public interface ImageImportService {

    ImageImportRespVO uploadAndImport(ImageAssetUploadReqVO uploadReqVO);

    FilePresignedUrlRespVO createPresignedUpload(ImagePresignedUploadReqVO reqVO);

    ImageImportRespVO completeImport(ImageImportCompleteReqVO reqVO);

    ImageImportRespVO.Item importArchiveImage(ArchiveImageImportRequest request);

    ImageImportRespVO.Item recordArchiveFailure(ArchiveImageImportRequest request,
                                                 String errorCode, String errorMessage);

}
