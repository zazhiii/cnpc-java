package cn.iocoder.yudao.module.drill.dal.mysql.enhance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageVersionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImageVersionMapper extends BaseMapperX<ImageVersionDO> {
    default Integer selectNextVersionNo(Long assetId) {
        ImageVersionDO last = selectOne(new LambdaQueryWrapper<ImageVersionDO>()
                .eq(ImageVersionDO::getAssetId, assetId)
                .orderByDesc(ImageVersionDO::getVersionNo).last("LIMIT 1"));
        return last == null ? 1 : last.getVersionNo() + 1;
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
