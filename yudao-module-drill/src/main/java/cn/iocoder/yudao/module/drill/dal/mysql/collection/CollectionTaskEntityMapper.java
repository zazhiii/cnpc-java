package cn.iocoder.yudao.module.drill.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CollectionTaskEntityMapper extends BaseMapperX<CollectionTaskEntity> {

    default CollectionTaskEntity selectByUserIdAndClientId(Long userId, String clientId) {
        return selectOne(new LambdaQueryWrapperX<CollectionTaskEntity>()
                .eq(CollectionTaskEntity::getUserId, userId)
                .eq(CollectionTaskEntity::getClientId, clientId));
    }

    default CollectionTaskEntity selectByUserIdAndClientIdForUpdate(Long userId, String clientId) {
        return selectOne(new LambdaQueryWrapperX<CollectionTaskEntity>()
                .eq(CollectionTaskEntity::getUserId, userId)
                .eq(CollectionTaskEntity::getClientId, clientId)
                .last("FOR UPDATE"));
    }
}
