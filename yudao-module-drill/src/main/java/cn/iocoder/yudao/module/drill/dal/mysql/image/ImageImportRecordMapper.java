package cn.iocoder.yudao.module.drill.dal.mysql.image;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportRecordDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImageImportRecordMapper extends BaseMapperX<ImageImportRecordDO> {

    default void deleteByBatchId(Long batchId) {
        delete(new LambdaQueryWrapper<ImageImportRecordDO>()
                .eq(ImageImportRecordDO::getBatchId, batchId));
    }

}
