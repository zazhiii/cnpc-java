package cn.iocoder.yudao.module.drill.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskFormEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CollectionTaskFormEntityMapper extends BaseMapperX<CollectionTaskFormEntity> {

    default CollectionTaskFormEntity selectByTaskId(Long taskId) {
        return selectOne(CollectionTaskFormEntity::getTaskId, taskId);
    }
}
