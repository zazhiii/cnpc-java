package cn.iocoder.yudao.module.drill.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.collection.CollectionPhotoDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollectionPhotoMapper extends BaseMapperX<CollectionPhotoDO> {
    default CollectionPhotoDO selectByTaskAndCapture(Long taskId, String captureId) {
        return selectOne(new LambdaQueryWrapperX<CollectionPhotoDO>()
                .eq(CollectionPhotoDO::getTaskId, taskId)
                .eq(CollectionPhotoDO::getCaptureId, captureId));
    }

    default List<CollectionPhotoDO> selectListByTask(Long taskId) {
        return selectList(CollectionPhotoDO::getTaskId, taskId);
    }
}
