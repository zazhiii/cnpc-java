package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityRecordPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.ImageQualityRecordDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileDO;
import cn.iocoder.yudao.module.drill.dal.mysql.quality.ImageQualityRecordMapper;
import cn.iocoder.yudao.module.drill.service.image.ImageAssetReferenceService;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.QUALITY_RECORD_NOT_EXISTS;
import static cn.iocoder.yudao.module.drill.enums.QualityConstants.*;

@Service
public class ImageQualityRecordServiceImpl implements ImageQualityRecordService {

    @Resource
    private ImageQualityRecordMapper recordMapper;
    @Resource
    private ImageAssetReferenceService assetReferenceService;

    @Override
    public PageResult<ImageQualityRecordDO> getPage(ImageQualityRecordPageReqVO reqVO) {
        return recordMapper.selectPage(reqVO);
    }

    @Override
    public ImageQualityRecordDO get(Long id) {
        ImageQualityRecordDO record = recordMapper.selectById(id);
        if (record == null) {
            throw exception(QUALITY_RECORD_NOT_EXISTS);
        }
        return record;
    }

    @Override
    public ImageQualityRecordDO getLatest(Long assetId) {
        ImageQualityRecordDO record = recordMapper.selectLatestByAssetId(assetId);
        if (record == null) {
            throw exception(QUALITY_RECORD_NOT_EXISTS);
        }
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImageQualityRecordDO saveSuccess(Long assetId, QualityProfileDO profile,
                                            Map<String, Object> effectiveConfig,
                                            ImageQualityModelResult result, LocalDateTime startedAt) {
        ImageQualityRecordDO record = baseRecord(assetId, profile, effectiveConfig, startedAt);
        record.setExecutionStatus(RECORD_SUCCESS);
        record.setModelVersion(result.getModelVersion());
        record.setPassed(result.getPassed());
        record.setReason(result.getReason());
        record.setResultJson(JsonUtils.toJsonString(result));
        record.setDurationMs(result.getDurationMs());
        record.setFinishedAt(LocalDateTime.now());
        recordMapper.insert(record);
//        assetReferenceService.bindAssets(List.of(assetId), REFERENCE_BIZ_QUALITY_RECORD, record.getId());
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImageQualityRecordDO saveFailure(Long assetId, QualityProfileDO profile,
                                            Map<String, Object> effectiveConfig,
                                            Integer errorCode, String errorMessage, long durationMs,
                                            LocalDateTime startedAt) {
        ImageQualityRecordDO record = baseRecord(assetId, profile, effectiveConfig, startedAt);
        record.setExecutionStatus(RECORD_FAILED);
        record.setErrorCode(errorCode);
        record.setErrorMessage(truncate(errorMessage));
        record.setDurationMs(durationMs);
        record.setFinishedAt(LocalDateTime.now());
        recordMapper.insert(record);
//        assetReferenceService.bindAssets(List.of(assetId), REFERENCE_BIZ_QUALITY_RECORD, record.getId());
        return record;
    }

    private ImageQualityRecordDO baseRecord(Long assetId, QualityProfileDO profile,
                                            Map<String, Object> effectiveConfig,
                                            LocalDateTime startedAt) {
        ImageQualityRecordDO record = new ImageQualityRecordDO();
        record.setAssetId(assetId);
        record.setProfileId(profile.getId());
        record.setProfileCode(profile.getProfileCode());
        record.setProfileVersion(profile.getVersion());
//        record.setModelCode(profile.getModelCode());
        record.setEffectiveConfigJson(JsonUtils.toJsonString(effectiveConfig));
        record.setStartedAt(startedAt);
        return record;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(value.length(), 1000));
    }

}
