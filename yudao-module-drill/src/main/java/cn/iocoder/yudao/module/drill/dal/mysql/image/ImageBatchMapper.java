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
                .eqIfPresent(ImageBatchDO::getProjectId, reqVO.getProjectId())
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
                status = CASE
                    WHEN pending_metadata_count + #{pendingMetadata} > 0 THEN 'PENDING_METADATA'
                    WHEN failed_count + #{failed} > 0 THEN 'PARTIAL_SUCCESS'
                    ELSE 'PENDING_PROCESS'
                END,
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = 0
            """)
    int updateImportStatistics(@Param("id") Long id,
                               @Param("total") int total,
                               @Param("success") int success,
                               @Param("failed") int failed,
                               @Param("duplicate") int duplicate,
                               @Param("pendingMetadata") int pendingMetadata);

}
