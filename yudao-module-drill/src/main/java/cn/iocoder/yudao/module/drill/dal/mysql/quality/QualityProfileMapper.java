package cn.iocoder.yudao.module.drill.dal.mysql.quality;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface QualityProfileMapper extends BaseMapperX<QualityProfileDO> {

    default QualityProfileDO selectByCodeAndVersion(String profileCode, String version) {
        return selectOne(QualityProfileDO::getProfileCode, profileCode,
                QualityProfileDO::getVersion, version);
    }

    default QualityProfileDO selectDefaultEnabled() {
        return selectOne(new LambdaQueryWrapperX<QualityProfileDO>()
                .eq(QualityProfileDO::getDefaultProfile, true)
                .eq(QualityProfileDO::getStatus, "ENABLED"));
    }

    default List<QualityProfileDO> selectList(String status, String modelCode) {
        return selectList(new LambdaQueryWrapperX<QualityProfileDO>()
                .eqIfPresent(QualityProfileDO::getStatus, status)
//                .eqIfPresent(QualityProfileDO::getModelCode, modelCode)
                .orderByDesc(QualityProfileDO::getDefaultProfile)
                .orderByDesc(QualityProfileDO::getId));
    }

    default void clearDefault() {
        QualityProfileDO update = new QualityProfileDO();
        update.setDefaultProfile(false);
        update(update, new LambdaUpdateWrapper<QualityProfileDO>()
                .eq(QualityProfileDO::getDefaultProfile, true));
    }

}
