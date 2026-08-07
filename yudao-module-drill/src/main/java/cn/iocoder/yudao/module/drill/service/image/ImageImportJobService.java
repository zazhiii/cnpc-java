package cn.iocoder.yudao.module.drill.service.image;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.job.*;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportPackageDO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;

public interface ImageImportJobService {
    ImageImportJobRespVO createJob(ImageImportJobCreateReqVO reqVO);
    FilePresignedUrlRespVO presignPackage(Long packageId);
    void completePackageUpload(Long packageId, ImageImportUploadCompleteReqVO reqVO);
    void startJob(Long jobId);
    ImageImportJobRespVO getJob(Long jobId);
    ImageImportJobRespVO getJobByBatchId(Long batchId);
    PageResult<ImageImportPackageRespVO> getPackagePage(ImageImportPackagePageReqVO reqVO);
    void retryPackage(Long packageId);
    FilePresignedUrlRespVO resetPackageUpload(Long packageId);
    void finishPartial(Long jobId);
    void refreshJob(Long jobId);
    void cleanupArchive(ImageImportPackageDO packageDO);
}
