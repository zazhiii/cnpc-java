package cn.iocoder.yudao.module.drill.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionTaskVO;
import cn.iocoder.yudao.module.drill.dal.entity.collection.CollectionTaskEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

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

    default CollectionTaskEntity selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<CollectionTaskEntity>()
                .eq(CollectionTaskEntity::getId, id)
                .last("FOR UPDATE"));
    }

    default List<CollectionTaskEntity> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<CollectionTaskEntity>()
                .eq(CollectionTaskEntity::getUserId, userId)
                .orderByDesc(CollectionTaskEntity::getUpdateTime));
    }

    default Long selectCountByUserIdAndStatus(Long userId, String status) {
        return selectCount(new LambdaQueryWrapperX<CollectionTaskEntity>()
                .eq(CollectionTaskEntity::getUserId, userId)
                .eqIfPresent(CollectionTaskEntity::getStatus, status));
    }

    default PageResult<CollectionTaskEntity> selectPageByUserId(
            Long userId, AppCollectionTaskVO.PageReq req) {
        String keyword = req.getKeyword();
        return selectPage(req, new LambdaQueryWrapperX<CollectionTaskEntity>()
                .eq(CollectionTaskEntity::getUserId, userId)
                .eqIfPresent(CollectionTaskEntity::getStatus, req.getStatus())
                .and(keyword != null && !keyword.isBlank(), query -> query
                        .like(CollectionTaskEntity::getTaskNo, keyword)
                        .or().apply("EXISTS (SELECT 1 FROM drill_collection_task_form f "
                                        + "WHERE f.task_id = drill_collection_task.id AND f.deleted = 0 "
                                        + "AND (f.drill_code LIKE CONCAT('%', {0}, '%') "
                                        + "OR f.well_number LIKE CONCAT('%', {0}, '%')))", keyword))
                .orderByDesc(CollectionTaskEntity::getUpdateTime));
    }
}
