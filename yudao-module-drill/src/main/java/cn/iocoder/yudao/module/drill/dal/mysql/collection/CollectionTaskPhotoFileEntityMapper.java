package cn.iocoder.yudao.module.drill.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskPhotoFileEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollectionTaskPhotoFileEntityMapper extends BaseMapperX<CollectionTaskPhotoFileEntity> {

    default CollectionTaskPhotoFileEntity selectByTaskAndCapture(Long taskId, String captureId) {
        return selectOne(new LambdaQueryWrapperX<CollectionTaskPhotoFileEntity>()
                .eq(CollectionTaskPhotoFileEntity::getTaskId, taskId)
                .eq(CollectionTaskPhotoFileEntity::getCaptureId, captureId));
    }

    default List<CollectionTaskPhotoFileEntity> selectListByTaskId(Long taskId) {
        return selectList(CollectionTaskPhotoFileEntity::getTaskId, taskId);
    }
}
