package cn.iocoder.yudao.module.drill.dal.mysql.image;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.job.ImageImportPackagePageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportPackageDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ImageImportPackageMapper extends BaseMapperX<ImageImportPackageDO> {
    default PageResult<ImageImportPackageDO> selectPage(ImageImportPackagePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ImageImportPackageDO>()
                .eq(ImageImportPackageDO::getJobId, reqVO.getJobId())
                .eqIfPresent(ImageImportPackageDO::getStatus, reqVO.getStatus())
                .and(reqVO.getKeyword() != null, wrapper -> wrapper
                        .like(ImageImportPackageDO::getRelativePath, reqVO.getKeyword())
                        .or().like(ImageImportPackageDO::getOriginalName, reqVO.getKeyword()))
                .orderByAsc(ImageImportPackageDO::getId));
    }
    default List<ImageImportPackageDO> selectByJobId(Long jobId) {
        return selectList(new LambdaQueryWrapper<ImageImportPackageDO>()
                .eq(ImageImportPackageDO::getJobId, jobId).orderByAsc(ImageImportPackageDO::getId));
    }

    default List<ImageImportPackageDO> selectPendingCandidates(int limit) {
        return selectList(new LambdaQueryWrapper<ImageImportPackageDO>()
                .eq(ImageImportPackageDO::getStatus, "PENDING")
                .orderByAsc(ImageImportPackageDO::getId).last("LIMIT " + limit));
    }

    default boolean claim(Long id, String workerId, LocalDateTime leaseUntil) {
        ImageImportPackageDO update = new ImageImportPackageDO();
        update.setStatus("PROCESSING");
        update.setWorkerId(workerId);
        update.setLeaseUntil(leaseUntil);
        update.setHeartbeatTime(LocalDateTime.now());
        update.setStartedAt(LocalDateTime.now());
        return update(update, new LambdaUpdateWrapper<ImageImportPackageDO>()
                .eq(ImageImportPackageDO::getId, id)
                .eq(ImageImportPackageDO::getStatus, "PENDING")) == 1;
    }

    default List<ImageImportPackageDO> selectExpired(LocalDateTime now) {
        return selectList(new LambdaQueryWrapper<ImageImportPackageDO>()
                .eq(ImageImportPackageDO::getStatus, "PROCESSING")
                .lt(ImageImportPackageDO::getLeaseUntil, now));
    }

    default List<ImageImportPackageDO> selectCleanupPending(int limit) {
        return selectList(new LambdaQueryWrapper<ImageImportPackageDO>()
                .eq(ImageImportPackageDO::getCleanupPending, true)
                .orderByAsc(ImageImportPackageDO::getId).last("LIMIT " + limit));
    }

    default void deleteByJobId(Long jobId) {
        delete(new LambdaQueryWrapper<ImageImportPackageDO>()
                .eq(ImageImportPackageDO::getJobId, jobId));
    }

    default void requeue(Long id, int retryCount, String errorCode, String errorMessage) {
        update(null, new LambdaUpdateWrapper<ImageImportPackageDO>()
                .eq(ImageImportPackageDO::getId, id)
                .set(ImageImportPackageDO::getStatus, "PENDING")
                .set(ImageImportPackageDO::getRetryCount, retryCount)
                .set(ImageImportPackageDO::getWorkerId, null)
                .set(ImageImportPackageDO::getLeaseUntil, null)
                .set(ImageImportPackageDO::getFinishedAt, null)
                .set(ImageImportPackageDO::getErrorCode, errorCode)
                .set(ImageImportPackageDO::getErrorMessage, errorMessage));
    }

    default void clearProcessingState(Long id, boolean clearError) {
        LambdaUpdateWrapper<ImageImportPackageDO> wrapper = new LambdaUpdateWrapper<ImageImportPackageDO>()
                .eq(ImageImportPackageDO::getId, id)
                .set(ImageImportPackageDO::getWorkerId, null)
                .set(ImageImportPackageDO::getLeaseUntil, null);
        if (clearError) {
            wrapper.set(ImageImportPackageDO::getErrorCode, null)
                    .set(ImageImportPackageDO::getErrorMessage, null);
        }
        update(null, wrapper);
    }

    default void clearArchiveReferences(Long id) {
        update(null, new LambdaUpdateWrapper<ImageImportPackageDO>()
                .eq(ImageImportPackageDO::getId, id)
                .set(ImageImportPackageDO::getArchiveFileId, null)
                .set(ImageImportPackageDO::getFileConfigId, null)
                .set(ImageImportPackageDO::getObjectKey, null)
                .set(ImageImportPackageDO::getOriginalUrl, null)
                .set(ImageImportPackageDO::getCleanupPending, false));
    }

    default void resetForUpload(Long id, int retryCount) {
        update(null, new LambdaUpdateWrapper<ImageImportPackageDO>()
                .eq(ImageImportPackageDO::getId, id)
                .set(ImageImportPackageDO::getStatus, "UPLOADING")
                .set(ImageImportPackageDO::getArchiveFileId, null)
                .set(ImageImportPackageDO::getFileConfigId, null)
                .set(ImageImportPackageDO::getObjectKey, null)
                .set(ImageImportPackageDO::getOriginalUrl, null)
                .set(ImageImportPackageDO::getActualSize, null)
                .set(ImageImportPackageDO::getSha256, null)
                .set(ImageImportPackageDO::getWorkerId, null)
                .set(ImageImportPackageDO::getLeaseUntil, null)
                .set(ImageImportPackageDO::getFinishedAt, null)
                .set(ImageImportPackageDO::getErrorCode, null)
                .set(ImageImportPackageDO::getErrorMessage, null)
                .set(ImageImportPackageDO::getCleanupPending, false)
                .set(ImageImportPackageDO::getRetryCount, retryCount));
    }
}
