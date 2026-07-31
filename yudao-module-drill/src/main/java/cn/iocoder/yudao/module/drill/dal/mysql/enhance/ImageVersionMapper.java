package cn.iocoder.yudao.module.drill.dal.mysql.enhance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageVersionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImageVersionMapper extends BaseMapperX<ImageVersionDO> {
    default ImageVersionDO selectByEnhanceItemId(Long enhanceItemId) {
        return selectOne(new LambdaQueryWrapper<ImageVersionDO>()
                .eq(ImageVersionDO::getEnhanceItemId, enhanceItemId)
                .orderByDesc(ImageVersionDO::getVersionNo)
                .last("LIMIT 1"));
    }

    default Integer selectNextVersionNo(Long assetId) {
        ImageVersionDO last = selectOne(new LambdaQueryWrapper<ImageVersionDO>()
                .eq(ImageVersionDO::getAssetId, assetId)
                .orderByDesc(ImageVersionDO::getVersionNo).last("LIMIT 1"));
        return last == null ? 1 : last.getVersionNo() + 1;
    }

    default ImageVersionDO selectOriginalByAssetId(Long assetId) {
        return selectOne(new LambdaQueryWrapper<ImageVersionDO>()
                .eq(ImageVersionDO::getAssetId, assetId)
                .eq(ImageVersionDO::getVersionType, "ORIGINAL")
                .orderByDesc(ImageVersionDO::getId)
                .last("LIMIT 1"));
    }

    default Long selectCountByAssetIds(java.util.Collection<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return 0L;
        }
        return selectCount(new LambdaQueryWrapper<ImageVersionDO>()
                .in(ImageVersionDO::getAssetId, assetIds));
    }

    default void clearDefault(Long assetId, String purpose) {
        ImageVersionDO update = new ImageVersionDO();
        update.setDefaultVersion(false);
        update(update, new LambdaUpdateWrapper<ImageVersionDO>()
                .eq(ImageVersionDO::getAssetId, assetId)
                .eq(ImageVersionDO::getPurpose, purpose)
                .eq(ImageVersionDO::getDefaultVersion, true));
    }
}
