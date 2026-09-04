package cn.iocoder.yudao.module.drill.dal.mysql.image;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch.ImageBatchPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageBatchDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ImageBatchMapper extends BaseMapperX<ImageBatchDO> {

    default PageResult<ImageBatchDO> selectPage(ImageBatchPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ImageBatchDO>()
                .eqIfPresent(ImageBatchDO::getSourceType, reqVO.getSourceType())
                .eqIfPresent(ImageBatchDO::getStatus, reqVO.getStatus())
//                .eqIfPresent(ImageBatchDO::getProjectId, reqVO.getProjectId())
                .betweenIfPresent(ImageBatchDO::getCreateTime, reqVO.getCreateTime())
                .and(reqVO.getKeyword() != null, wrapper -> wrapper
                        .like(ImageBatchDO::getBatchNo, reqVO.getKeyword())
                        .or()
                        .like(ImageBatchDO::getName, reqVO.getKeyword()))
                .orderByDesc(ImageBatchDO::getId));
    }

    @Update("""
            UPDATE drill_image_batch
            SET total_count = total_count + #{total},
                success_count = success_count + #{success},
                failed_count = failed_count + #{failed},
                duplicate_count = duplicate_count + #{duplicate},
                pending_metadata_count = pending_metadata_count + #{pendingMetadata},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'IMPORTING' AND deleted = 0
            """)
    int updateImportStatistics(@Param("id") Long id,
                               @Param("total") int total,
                               @Param("success") int success,
                               @Param("failed") int failed,
                               @Param("duplicate") int duplicate,
                               @Param("pendingMetadata") int pendingMetadata);

    @Update("""
            UPDATE drill_image_batch b
            LEFT JOIN (
                SELECT batch_id, COUNT(*) AS pending_count
                FROM drill_image_asset
                WHERE batch_id = #{id}
                  AND metadata_status = 'PENDING'
                  AND deleted = 0
                GROUP BY batch_id
            ) asset_stats ON asset_stats.batch_id = b.id
            SET b.pending_metadata_count = COALESCE(asset_stats.pending_count, 0),
                b.update_time = CURRENT_TIMESTAMP
            WHERE b.id = #{id}
              AND b.deleted = 0
            """)
    int refreshMetadataStatistics(@Param("id") Long id);

    @Update("""
            UPDATE drill_image_batch
            SET status = CASE
                    WHEN success_count > 0 AND (failed_count > 0 OR failed_package_count > 0
                         OR #{sourcePartial} = TRUE) THEN 'PARTIAL_READY'
                    WHEN success_count > 0 THEN 'READY'
                    ELSE 'FAILED'
                END,
                finished_time = CURRENT_TIMESTAMP,
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND status = 'IMPORTING'
              AND total_count > 0
              AND deleted = 0
            """)
    int finishImport(@Param("id") Long id, @Param("sourcePartial") boolean sourcePartial);

    @Update("""
            UPDATE drill_image_batch
            SET total_count = #{total}, success_count = #{success}, failed_count = #{failed},
                duplicate_count = #{duplicate}, pending_metadata_count = #{pendingMetadata},
                failed_package_count = #{failedPackages}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'IMPORTING' AND deleted = 0
            """)
    int replaceImportStatistics(@Param("id") Long id, @Param("total") int total,
                                @Param("success") int success, @Param("failed") int failed,
                                @Param("duplicate") int duplicate,
                                @Param("pendingMetadata") int pendingMetadata,
                                @Param("failedPackages") int failedPackages);

    @Update("""
            UPDATE drill_image_batch
            SET status = 'FAILED', finished_time = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'IMPORTING' AND deleted = 0
            """)
    int cancelImport(@Param("id") Long id);

}
