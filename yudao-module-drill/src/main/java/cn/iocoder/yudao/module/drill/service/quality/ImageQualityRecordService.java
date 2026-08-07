package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityRecordPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.ImageQualityRecordDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileDO;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;

import java.time.LocalDateTime;
import java.util.Map;

public interface ImageQualityRecordService {
    PageResult<ImageQualityRecordDO> getPage(ImageQualityRecordPageReqVO reqVO);
    ImageQualityRecordDO get(Long id);
    ImageQualityRecordDO getLatest(Long assetId);
    ImageQualityRecordDO saveSuccess(Long assetId, QualityProfileDO profile, Map<String, Object> effectiveConfig,
                                     ImageQualityModelResult result, LocalDateTime startedAt);
    ImageQualityRecordDO saveFailure(Long assetId, QualityProfileDO profile, Map<String, Object> effectiveConfig,
                                     Integer errorCode, String errorMessage, long durationMs,
                                     LocalDateTime startedAt);
}
