package cn.iocoder.yudao.module.drill.dal.mysql.image;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageAssetPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImageAssetMapper extends BaseMapperX<ImageAssetDO> {

    default PageResult<ImageAssetDO> selectPage(ImageAssetPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ImageAssetDO>()
                .eqIfPresent(ImageAssetDO::getBatchId, reqVO.getBatchId())
                .eqIfPresent(ImageAssetDO::getDrillType, reqVO.getDrillType())
                .eqIfPresent(ImageAssetDO::getSceneType, reqVO.getSceneType())
                .eqIfPresent(ImageAssetDO::getPhotoType, reqVO.getPhotoType())
                .eqIfPresent(ImageAssetDO::getBladeNo, reqVO.getBladeNo())
                .eqIfPresent(ImageAssetDO::getMetadataStatus, reqVO.getMetadataStatus())
                .and(reqVO.getKeyword() != null, wrapper -> wrapper
                        .like(ImageAssetDO::getAssetNo, reqVO.getKeyword())
                        .or()
                        .like(ImageAssetDO::getDrillCode, reqVO.getKeyword()))
                .orderByDesc(ImageAssetDO::getId));
    }

    default ImageAssetDO selectBySha256(String sha256) {
        return selectOne(ImageAssetDO::getSha256, sha256);
    }

    default List<ImageAssetDO> selectByBatchId(Long batchId) {
        return selectList(new LambdaQueryWrapper<ImageAssetDO>()
                .eq(ImageAssetDO::getBatchId, batchId));
    }

    default void deleteByBatchId(Long batchId) {
        delete(new LambdaQueryWrapper<ImageAssetDO>()
                .eq(ImageAssetDO::getBatchId, batchId));
    }

}
