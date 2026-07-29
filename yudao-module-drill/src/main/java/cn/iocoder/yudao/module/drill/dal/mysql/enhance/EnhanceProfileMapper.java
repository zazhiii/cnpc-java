package cn.iocoder.yudao.module.drill.dal.mysql.enhance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceProfileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EnhanceProfileMapper extends BaseMapperX<EnhanceProfileDO> {
    default EnhanceProfileDO selectByCodeAndVersion(String code, String version) {
        return selectOne(EnhanceProfileDO::getProfileCode, code, EnhanceProfileDO::getVersion, version);
    }

    default List<EnhanceProfileDO> selectList(String status, String photoType, String drillType) {
        return selectList(new LambdaQueryWrapperX<EnhanceProfileDO>()
                .eqIfPresent(EnhanceProfileDO::getStatus, status)
                .likeIfPresent(EnhanceProfileDO::getApplicablePhotoTypes, photoType)
                .likeIfPresent(EnhanceProfileDO::getApplicableDrillTypes, drillType)
                .orderByDesc(EnhanceProfileDO::getId));
    }
}
