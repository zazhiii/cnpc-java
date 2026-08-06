package cn.iocoder.yudao.module.drill.dal.mysql.image;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetReferenceDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ImageAssetReferenceMapper extends BaseMapperX<ImageAssetReferenceDO> {

    default List<ImageAssetReferenceDO> selectByBusiness(String bizType, Long bizId) {
        return selectList(new LambdaQueryWrapper<ImageAssetReferenceDO>()
                .eq(ImageAssetReferenceDO::getBizType, bizType)
                .eq(ImageAssetReferenceDO::getBizId, bizId));
    }

    default boolean existsByAssetIds(Collection<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return false;
        }
        return selectCount(new LambdaQueryWrapper<ImageAssetReferenceDO>()
                .in(ImageAssetReferenceDO::getAssetId, assetIds)) > 0;
    }

}
