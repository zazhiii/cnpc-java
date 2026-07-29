package cn.iocoder.yudao.module.drill.dal.mysql.enhance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceJobPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceJobDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EnhanceJobMapper extends BaseMapperX<EnhanceJobDO> {
    default PageResult<EnhanceJobDO> selectPage(EnhanceJobPageReqVO req) {
        return selectPage(req, new LambdaQueryWrapperX<EnhanceJobDO>()
                .likeIfPresent(EnhanceJobDO::getJobNo, req.getKeyword())
                .eqIfPresent(EnhanceJobDO::getStatus, req.getStatus())
                .eqIfPresent(EnhanceJobDO::getProfileId, req.getProfileId())
                .eqIfPresent(EnhanceJobDO::getBatchId, req.getBatchId())
                .orderByDesc(EnhanceJobDO::getId));
    }

    default EnhanceJobDO selectByIdempotencyKey(String key) {
        return selectOne(EnhanceJobDO::getIdempotencyKey, key);
    }
}
