package cn.iocoder.yudao.module.drill.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionCaptureDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CollectionCaptureMapper extends BaseMapperX<CollectionCaptureDO> {
    default List<CollectionCaptureDO> selectByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<CollectionCaptureDO>()
                .eq(CollectionCaptureDO::getTaskId, taskId)
                .orderByAsc(CollectionCaptureDO::getSortNo));
    }

    @Delete("DELETE FROM drill_collection_capture WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
