package cn.iocoder.yudao.module.drill.dal.mysql.image;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportJobDO;
import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ImageImportJobMapper extends BaseMapperX<ImageImportJobDO> {
    default ImageImportJobDO selectByBatchId(Long batchId) {
        return selectOne(ImageImportJobDO::getBatchId, batchId);
    }

    default List<ImageImportJobDO> selectUploadingBefore(LocalDateTime before) {
        return selectList(new LambdaQueryWrapper<ImageImportJobDO>()
                .eq(ImageImportJobDO::getStatus, "UPLOADING")
                .lt(ImageImportJobDO::getUpdateTime, before));
    }

    default boolean transitionStatus(Long id, String from, String to, LocalDateTime finishedAt) {
        ImageImportJobDO update = new ImageImportJobDO();
        update.setStatus(to);
        update.setFinishedAt(finishedAt);
        return update(update, new LambdaUpdateWrapper<ImageImportJobDO>()
                .eq(ImageImportJobDO::getId, id)
                .eq(ImageImportJobDO::getStatus, from)) == 1;
    }

    default void resumeProcessing(Long id) {
        update(null, new LambdaUpdateWrapper<ImageImportJobDO>()
                .eq(ImageImportJobDO::getId, id)
                .set(ImageImportJobDO::getStatus, "PROCESSING")
                .set(ImageImportJobDO::getFinishedAt, null));
    }
}
