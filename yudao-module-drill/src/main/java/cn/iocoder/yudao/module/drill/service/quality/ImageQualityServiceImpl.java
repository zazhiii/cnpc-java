package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityCheckReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityCheckRespVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.ImageQualityRecordDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileDO;
import cn.iocoder.yudao.module.drill.service.image.ImageAssetService;
import cn.iocoder.yudao.module.drill.service.image.ImageFileValidator;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.drill.enums.QualityConstants.MODEL_E11;
import static cn.iocoder.yudao.module.drill.enums.QualityConstants.RECORD_FAILED;
import static cn.iocoder.yudao.module.drill.enums.QualityConstants.RECORD_SUCCESS;

@Service
public class ImageQualityServiceImpl implements ImageQualityService {

    private static final int MAX_ASSET_COUNT = 20;

    @Resource
    private QualityProfileService profileService;
    @Resource
    private ImageQualityModelRegistry modelRegistry;
    @Resource
    private ImageQualityRecordService imageQualityRecordService;
    @Resource
    private ImageAssetService imageAssetService;
    @Resource
    private ImageFileValidator imageFileValidator;

    @Override
    public ImageQualityCheckRespVO check(ImageQualityCheckReqVO reqVO) {
        List<Long> assetIds = resolveAssetIds(reqVO);

        // 获取质检方案
        QualityProfileDO profile = reqVO.getProfileId() == null
                ? profileService.getDefaultEnabled() : profileService.getEnabled(reqVO.getProfileId());
        ImageQualityModelExecutor executor = modelRegistry.getRequired(profile.getModelCode());
        Map<String, Object> effectiveConfig = effectiveConfig(profile, reqVO);
        executor.validateConfig(effectiveConfig);
        executor.checkAvailability();

        // 获取图片
        Map<Long, ImageAssetDO> assets = imageAssetService.getAssets(assetIds).stream()
                .collect(Collectors.toMap(ImageAssetDO::getId, Function.identity()));
        if (assets.size() != assetIds.size()) {
            throw exception(IMAGE_ASSET_NOT_EXISTS);
        }

        // 质检并添加每一个质检结果
        List<ImageQualityCheckRespVO.Item> items = new ArrayList<>(assetIds.size());
        for (Long assetId : assetIds) {
            items.add(e11ModelCheckOne(assets.get(assetId), profile, executor, effectiveConfig));
        }

        // 构建最终结果
        ImageQualityCheckRespVO response = new ImageQualityCheckRespVO();
        response.setItems(items);
        response.setTotalCount(items.size());
        response.setSuccessCount((int) items.stream()
                .filter(item -> RECORD_SUCCESS.equals(item.getExecutionStatus())).count());
        response.setFailedCount(response.getTotalCount() - response.getSuccessCount());
        return response;
    }

    private ImageQualityCheckRespVO.Item e11ModelCheckOne(ImageAssetDO asset, QualityProfileDO profile,
                                                          ImageQualityModelExecutor executor,
                                                          Map<String, Object> effectiveConfig) {
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        try {
            byte[] content = imageAssetService.getAssetContent(asset.getId());
            ImageFileValidator.ImageInfo imageInfo = imageFileValidator.validate(content, asset.getOriginalName());
            QualityImageInput input = new QualityImageInput(asset.getId(), content, asset.getOriginalName(),
                    imageInfo.getMimeType(), imageInfo.getWidth(), imageInfo.getHeight());
            ImageQualityModelResult result = executor.execute(input, effectiveConfig);
            ImageQualityRecordDO record = imageQualityRecordService.saveSuccess(asset.getId(), profile,
                    effectiveConfig, result, startedAt);
            ImageQualityCheckRespVO.Item item = BeanUtils.toBean(result, ImageQualityCheckRespVO.Item.class);
            fillMetadata(item, record, profile, effectiveConfig);
            return item;
        } catch (RuntimeException ex) {
            long durationMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
            Integer errorCode = ex instanceof ServiceException serviceException
                    ? serviceException.getCode() : QUALITY_MODEL_EXECUTION_FAILED.getCode();
            ImageQualityRecordDO record = imageQualityRecordService.saveFailure(asset.getId(), profile, effectiveConfig,
                    errorCode, ex.getMessage(), durationMs, startedAt);
            ImageQualityCheckRespVO.Item item = new ImageQualityCheckRespVO.Item();
            fillMetadata(item, record, profile, effectiveConfig);
            item.setExecutionStatus(RECORD_FAILED);
            item.setErrorCode(errorCode);
            item.setErrorMessage(record.getErrorMessage());
            item.setDurationMs(durationMs);
            return item;
        }
    }

    private List<Long> resolveAssetIds(ImageQualityCheckReqVO reqVO) {
        boolean hasAssetId = reqVO.getAssetId() != null;
        boolean hasAssetIds = reqVO.getAssetIds() != null && !reqVO.getAssetIds().isEmpty();
        if (hasAssetId == hasAssetIds) {
            throw exception(QUALITY_CHECK_REQUEST_INVALID, "assetId 和 assetIds 必须且只能提供一个");
        }
        List<Long> ids = hasAssetId ? List.of(reqVO.getAssetId()) : reqVO.getAssetIds();
//        if (ids.size() > MAX_ASSET_COUNT) {
//            throw exception(QUALITY_CHECK_REQUEST_INVALID, "一次最多质检 20 张图片");
//        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw exception(QUALITY_CHECK_REQUEST_INVALID, "资产编号必须为正整数");
        }
        if (new LinkedHashSet<>(ids).size() != ids.size()) {
            throw exception(QUALITY_CHECK_REQUEST_INVALID, "assetIds 不能包含重复编号");
        }
        return List.copyOf(ids);
    }

    private Map<String, Object> effectiveConfig(QualityProfileDO profile, ImageQualityCheckReqVO reqVO) {
        Map<String, Object> stored = JsonUtils.parseMap(profile.getModelConfigJson());
        if (stored == null) {
            throw exception(QUALITY_PROFILE_INVALID, "模型参数不是有效 JSON 对象");
        }
        Map<String, Object> config = new LinkedHashMap<>(stored);
        if (reqVO.getConfidence() != null || reqVO.getMinDetections() != null) {
            if (!MODEL_E11.equals(profile.getModelCode())) {
                throw exception(QUALITY_CHECK_REQUEST_INVALID, "confidence 和 minDetections 仅适用于 E11 模型");
            }
            if (reqVO.getConfidence() != null) {
                config.put("confidence", reqVO.getConfidence());
            }
            if (reqVO.getMinDetections() != null) {
                config.put("minDetections", reqVO.getMinDetections());
            }
        }
        return Map.copyOf(config);
    }

    private void fillMetadata(ImageQualityCheckRespVO.Item item, ImageQualityRecordDO record,
                              QualityProfileDO profile, Map<String, Object> effectiveConfig) {
        item.setRecordId(record.getId());
        item.setAssetId(record.getAssetId());
        item.setProfileId(profile.getId());
        item.setProfileCode(profile.getProfileCode());
        item.setProfileVersion(profile.getVersion());
        item.setModelCode(profile.getModelCode());
        item.setExecutionStatus(record.getExecutionStatus());
        item.setEffectiveConfig(effectiveConfig);
    }

}
