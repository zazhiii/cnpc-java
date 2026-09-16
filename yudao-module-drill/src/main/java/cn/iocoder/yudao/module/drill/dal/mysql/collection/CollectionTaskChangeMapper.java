package cn.iocoder.yudao.module.drill.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionTaskChangeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollectionTaskChangeMapper extends BaseMapperX<CollectionTaskChangeDO> {
    default List<CollectionTaskChangeDO> selectChanges(Long userId, long cursor, int limit) {
        return selectList(new LambdaQueryWrapperX<CollectionTaskChangeDO>()
                .eq(CollectionTaskChangeDO::getCollectorUserId, userId)
                .gt(CollectionTaskChangeDO::getId, cursor)
                .orderByAsc(CollectionTaskChangeDO::getId)
                .last("LIMIT " + limit));
    }

    default CollectionTaskChangeDO selectLatest(Long userId, Long taskId) {
        return selectOne(new LambdaQueryWrapperX<CollectionTaskChangeDO>()
                .eq(CollectionTaskChangeDO::getCollectorUserId, userId)
                .eq(CollectionTaskChangeDO::getTaskId, taskId)
                .orderByDesc(CollectionTaskChangeDO::getId)
                .last("LIMIT 1"));
    }
}
