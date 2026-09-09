package cn.iocoder.yudao.module.drill.dal.mysql.quality;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface QualityProfileItemMapper extends BaseMapperX<QualityProfileItemDO> {

    default List<QualityProfileItemDO> selectListByProfileId(Long profileId) {
        return selectList(new LambdaQueryWrapperX<QualityProfileItemDO>()
                .eq(QualityProfileItemDO::getProfileId, profileId)
                .orderByAsc(QualityProfileItemDO::getSortNo)
                .orderByAsc(QualityProfileItemDO::getId));
    }

    default void deleteByProfileId(Long profileId) {
        delete(QualityProfileItemDO::getProfileId, profileId);
    }

}
