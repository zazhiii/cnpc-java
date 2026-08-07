package cn.iocoder.yudao.module.drill.dal.mysql.quality;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityRecordPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.ImageQualityRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ImageQualityRecordMapper extends BaseMapperX<ImageQualityRecordDO> {

    default PageResult<ImageQualityRecordDO> selectPage(ImageQualityRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ImageQualityRecordDO>()
                .eqIfPresent(ImageQualityRecordDO::getAssetId, reqVO.getAssetId())
                .eqIfPresent(ImageQualityRecordDO::getProfileId, reqVO.getProfileId())
                .eqIfPresent(ImageQualityRecordDO::getModelCode, reqVO.getModelCode())
                .eqIfPresent(ImageQualityRecordDO::getExecutionStatus, reqVO.getExecutionStatus())
                .eqIfPresent(ImageQualityRecordDO::getPassed, reqVO.getPassed())
                .orderByDesc(ImageQualityRecordDO::getId));
    }

    default ImageQualityRecordDO selectLatestByAssetId(Long assetId) {
        return selectOne(new LambdaQueryWrapperX<ImageQualityRecordDO>()
                .eq(ImageQualityRecordDO::getAssetId, assetId)
                .orderByDesc(ImageQualityRecordDO::getId)
                .last("LIMIT 1"));
    }

    default long selectCountByProfileId(Long profileId) {
        return selectCount(ImageQualityRecordDO::getProfileId, profileId);
    }

}
