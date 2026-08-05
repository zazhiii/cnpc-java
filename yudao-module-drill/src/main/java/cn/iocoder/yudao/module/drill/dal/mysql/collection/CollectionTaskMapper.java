package cn.iocoder.yudao.module.drill.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollectionTaskMapper extends BaseMapperX<CollectionTaskDO> {
    default CollectionTaskDO selectByClientId(Long userId, String clientId) {
        return selectOne(new LambdaQueryWrapperX<CollectionTaskDO>()
                .eq(CollectionTaskDO::getCollectorUserId, userId)
                .eq(CollectionTaskDO::getClientId, clientId));
    }

    default List<CollectionTaskDO> selectListByUser(Long userId) {
        return selectList(new LambdaQueryWrapperX<CollectionTaskDO>()
                .eq(CollectionTaskDO::getCollectorUserId, userId)
                .orderByDesc(CollectionTaskDO::getUpdateTime));
    }
}
