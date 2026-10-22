package cn.iocoder.yudao.module.drill.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoItemEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollectionTaskPhotoItemEntityMapper extends BaseMapperX<CollectionTaskPhotoItemEntity> {

    default List<CollectionTaskPhotoItemEntity> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<CollectionTaskPhotoItemEntity>()
                .eq(CollectionTaskPhotoItemEntity::getTaskId, taskId)
                .orderByAsc(CollectionTaskPhotoItemEntity::getSortNo));
    }
}
