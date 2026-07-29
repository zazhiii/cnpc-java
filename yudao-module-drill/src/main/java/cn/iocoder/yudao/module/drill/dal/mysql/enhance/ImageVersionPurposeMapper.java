package cn.iocoder.yudao.module.drill.dal.mysql.enhance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageVersionPurposeDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImageVersionPurposeMapper extends BaseMapperX<ImageVersionPurposeDO> {
    default void clearDefault(Long assetId, String purpose) {
        ImageVersionPurposeDO update = new ImageVersionPurposeDO();
        update.setDefaultVersion(false);
        update(update, new LambdaUpdateWrapper<ImageVersionPurposeDO>()
                .eq(ImageVersionPurposeDO::getAssetId, assetId)
                .eq(ImageVersionPurposeDO::getPurpose, purpose)
                .eq(ImageVersionPurposeDO::getDefaultVersion, true));
    }

    default List<ImageVersionPurposeDO> selectByVersionId(Long versionId) {
        return selectList(new LambdaQueryWrapper<ImageVersionPurposeDO>()
                .eq(ImageVersionPurposeDO::getImageVersionId, versionId));
    }

    default ImageVersionPurposeDO selectByVersionAndPurpose(Long versionId, String purpose) {
        return selectOne(ImageVersionPurposeDO::getImageVersionId, versionId,
                ImageVersionPurposeDO::getPurpose, purpose);
    }

    default void withdrawByVersionId(Long versionId) {
        ImageVersionPurposeDO update = new ImageVersionPurposeDO();
        update.setPublishStatus("WITHDRAWN");
        update.setDefaultVersion(false);
        update(update, new LambdaUpdateWrapper<ImageVersionPurposeDO>()
                .eq(ImageVersionPurposeDO::getImageVersionId, versionId));
    }
}
