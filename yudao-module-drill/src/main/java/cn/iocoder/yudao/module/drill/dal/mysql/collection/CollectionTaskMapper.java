package cn.iocoder.yudao.module.drill.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionTaskVO;
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

    default Long selectCountByUserAndStatus(Long userId, String status) {
        return selectCount(new LambdaQueryWrapperX<CollectionTaskDO>()
                .eq(CollectionTaskDO::getCollectorUserId, userId)
                .eqIfPresent(CollectionTaskDO::getStatus, status));
    }

    default PageResult<CollectionTaskDO> selectPage(Long userId, AppCollectionTaskVO.PageReq req) {
        return selectPage(req, new LambdaQueryWrapperX<CollectionTaskDO>()
                .eq(CollectionTaskDO::getCollectorUserId, userId)
                .eqIfPresent(CollectionTaskDO::getStatus, req.getStatus())
                .and(req.getKeyword() != null && !req.getKeyword().isBlank(), query -> query
                        .like(CollectionTaskDO::getTaskNo, req.getKeyword())
                        .or().like(CollectionTaskDO::getDrillCode, req.getKeyword())
                        .or().like(CollectionTaskDO::getWellNumber, req.getKeyword()))
                .orderByDesc(CollectionTaskDO::getUpdateTime));
    }
}
