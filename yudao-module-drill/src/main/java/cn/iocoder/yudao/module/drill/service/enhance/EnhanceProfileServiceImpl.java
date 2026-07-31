package cn.iocoder.yudao.module.drill.service.enhance;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceProfileVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceJobDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceProfileDO;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.EnhanceJobMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.EnhanceProfileMapper;
import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.OutputSpec;
import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.StepConfig;
import cn.iocoder.yudao.module.drill.service.enhance.external.ExternalImageAlgorithmClient;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.*;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;

@Service
public class EnhanceProfileServiceImpl implements EnhanceProfileService {

    @Resource private EnhanceProfileMapper profileMapper;
    @Resource private EnhanceJobMapper enhanceJobMapper;
    @Resource private ExternalImageAlgorithmClient externalAlgorithmClient;
    @Resource @Lazy private EnhanceJobService jobService;

    @Override
    public List<EnhanceProfileDO> getList(String status, String photoType, String drillType) {
        return profileMapper.selectList(status, photoType, drillType);
    }

    @Override
    public EnhanceProfileDO get(Long id) {
        EnhanceProfileDO profile = profileMapper.selectById(id);
        if (profile == null) throw exception(ENHANCE_PROFILE_NOT_EXISTS);
        return profile;
    }

    @Override
    public Long create(EnhanceProfileVO.SaveReq req) {
        normalizeAndValidate(req, false);
        if (profileMapper.selectByCodeAndVersion(req.getProfileCode(), req.getVersion()) != null) {
            throw exception(ENHANCE_PROFILE_DUPLICATE);
        }
        EnhanceProfileDO profile = toDO(req);
        profile.setStatus(PROFILE_DRAFT);
        profileMapper.insert(profile);
        return profile.getId();
    }

    @Override
    public void update(EnhanceProfileVO.SaveReq req) {
        EnhanceProfileDO old = get(req.getId());
        if (!PROFILE_DRAFT.equals(old.getStatus())) throw exception(ENHANCE_PROFILE_STATE_INVALID);
        normalizeAndValidate(req, false);
        EnhanceProfileDO duplicate = profileMapper.selectByCodeAndVersion(req.getProfileCode(), req.getVersion());
        if (duplicate != null && !duplicate.getId().equals(req.getId())) throw exception(ENHANCE_PROFILE_DUPLICATE);
        EnhanceProfileDO update = toDO(req);
        update.setId(req.getId());
        profileMapper.updateById(update);
    }

    @Override
    public Long copy(EnhanceProfileVO.CopyReq req) {
        EnhanceProfileDO source = get(req.getSourceProfileId());
        if (profileMapper.selectByCodeAndVersion(source.getProfileCode(), normalizeVersion(req.getNewVersion())) != null) {
            throw exception(ENHANCE_PROFILE_DUPLICATE);
        }
        source.setId(null);
        source.setName(req.getName());
        source.setVersion(normalizeVersion(req.getNewVersion()));
        source.setStatus(PROFILE_DRAFT);
        source.setValidationJobId(null);
        source.setPublishedAt(null);
        profileMapper.insert(source);
        return source.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long validateProfile(EnhanceProfileVO.ValidateReq req, String idempotencyKey) {
        EnhanceProfileDO profile = get(req.getProfileId());
        if (!PROFILE_DRAFT.equals(profile.getStatus()) && !PROFILE_VALIDATING.equals(profile.getStatus())) {
            throw exception(ENHANCE_PROFILE_STATE_INVALID);
        }
        Long jobId = jobService.createValidationJob(profile, req.getAssetIds(), idempotencyKey);
        EnhanceProfileDO update = new EnhanceProfileDO();
        update.setId(profile.getId());
        update.setStatus(PROFILE_VALIDATING);
        update.setValidationJobId(jobId);
        profileMapper.updateById(update);
        return jobId;
    }

    @Override
    public void publish(Long id) {
        EnhanceProfileDO profile = get(id);
        if (!PROFILE_VALIDATING.equals(profile.getStatus()) || profile.getValidationJobId() == null) {
            throw exception(ENHANCE_PROFILE_STATE_INVALID);
        }
        EnhanceJobDO validationJob = enhanceJobMapper.selectById(profile.getValidationJobId());
        if (validationJob == null || !JOB_COMPLETED.equals(validationJob.getStatus())) {
            throw exception(ENHANCE_PROFILE_STATE_INVALID);
        }
        validateExternalAvailability(JsonUtils.parseArray(profile.getPipelineJson(), StepConfig.class));
        EnhanceProfileDO update = new EnhanceProfileDO();
        update.setId(id);
        update.setStatus(PROFILE_ENABLED);
        update.setPublishedAt(LocalDateTime.now());
        profileMapper.updateById(update);
    }

    @Override
    public void enable(Long id) {
        EnhanceProfileDO profile = get(id);
        if (!Set.of(PROFILE_DRAFT, PROFILE_DISABLED).contains(profile.getStatus())) {
            throw exception(ENHANCE_PROFILE_STATE_INVALID);
        }
        validateExternalAvailability(JsonUtils.parseArray(profile.getPipelineJson(), StepConfig.class));
        EnhanceProfileDO update = new EnhanceProfileDO();
        update.setId(id);
        update.setStatus(PROFILE_ENABLED);
        update.setPublishedAt(LocalDateTime.now());
        profileMapper.updateById(update);
    }

    @Override
    public void disable(Long id) {
        EnhanceProfileDO profile = get(id);
        if (!PROFILE_ENABLED.equals(profile.getStatus())) throw exception(ENHANCE_PROFILE_STATE_INVALID);
        EnhanceProfileDO update = new EnhanceProfileDO();
        update.setId(id);
        update.setStatus(PROFILE_DISABLED);
        profileMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        EnhanceProfileDO profile = get(id);
        if (!Set.of(PROFILE_DRAFT, PROFILE_DISABLED).contains(profile.getStatus())) {
            throw exception(ENHANCE_PROFILE_STATE_INVALID);
        }
        if (enhanceJobMapper.selectCountByProfileId(id) > 0) {
            throw exception(ENHANCE_PROFILE_IN_USE);
        }
        profileMapper.deleteById(id);
    }

    private void normalizeAndValidate(EnhanceProfileVO.SaveReq req, boolean requireAvailable) {
        req.setVersion(normalizeVersion(req.getVersion()));
        if (!REVIEW_POLICIES.contains(req.getReviewPolicy())) {
            throw exception(ENHANCE_PROFILE_INVALID, "审核策略无效");
        }
        List<StepConfig> steps = req.getPipeline();
        Set<String> seen = new HashSet<>();
        int previous = 0;
        for (StepConfig step : steps) {
            if (step.getCode() == null || !STEP_ORDER.contains(step.getCode()) || !seen.add(step.getCode())) {
                throw exception(ENHANCE_PROFILE_INVALID, "步骤编码无效或重复");
            }
            int expected = STEP_ORDER.indexOf(step.getCode()) + 1;
            if (step.getStepOrder() == null) step.setStepOrder(expected);
            if (step.getStepOrder() != expected || step.getStepOrder() <= previous) {
                throw exception(ENHANCE_PROFILE_INVALID, "步骤顺序必须遵循固定流水线");
            }
            previous = step.getStepOrder();
            validateParameters(step);
        }
        OutputSpec output = req.getOutputSpec();
        if (!"sRGB".equalsIgnoreCase(output.getColorSpace())
                || output.getQuality() == null || output.getQuality() < 70 || output.getQuality() > 100) {
            throw exception(ENHANCE_PROFILE_INVALID, "输出颜色空间或质量无效");
        }
        if (!Boolean.TRUE.equals(output.getKeepAspectRatio())
                && (!Set.of(1024, 1536).contains(output.getWidth()) || !Objects.equals(output.getWidth(), output.getHeight()))) {
            throw exception(ENHANCE_PROFILE_INVALID, "输出尺寸仅支持1024或1536方图");
        }
        if (requireAvailable) validateExternalAvailability(steps);
    }

    private void validateParameters(StepConfig step) {
        Map<String, Object> p = step.getParameters() == null ? Map.of() : step.getParameters();
        if ("EXPOSURE_ADJUST".equals(step.getCode())) {
            checkRange(p, "clipLimit", 1, 4);
            checkRange(p, "tileGridSize", 4, 16);
        } else if ("REFLECTION_SUPPRESSION".equals(step.getCode())) {
            checkRange(p, "strength", 0, 0.7);
        } else if ("BACKGROUND_WEAKEN".equals(step.getCode())) {
            checkRange(p, "saturationFactor", 0.2, 1);
            checkRange(p, "brightnessFactor", 0.4, 1);
        } else if ("CLARITY_ENHANCE".equals(step.getCode())) {
            checkRange(p, "amount", 0, 1.2);
        }
    }

    private void checkRange(Map<String, Object> p, String key, double min, double max) {
        Object value = p.get(key);
        if (value != null && (!(value instanceof Number) || ((Number) value).doubleValue() < min
                || ((Number) value).doubleValue() > max)) {
            throw exception(ENHANCE_PROFILE_INVALID, key + "超出安全范围");
        }
    }

    private void validateExternalAvailability(List<StepConfig> steps) {
        for (StepConfig step : steps) {
            if (!Boolean.TRUE.equals(step.getEnabled())) continue;
            if (EXTERNAL_STEPS.contains(step.getCode()) && !externalAlgorithmClient.supports(step.getCode())) {
                throw exception(ENHANCE_ALGORITHM_UNAVAILABLE, step.getCode());
            }
        }
    }

    private EnhanceProfileDO toDO(EnhanceProfileVO.SaveReq req) {
        EnhanceProfileDO profile = new EnhanceProfileDO();
        profile.setProfileCode(req.getProfileCode());
        profile.setName(req.getName());
        profile.setVersion(req.getVersion());
        profile.setApplicableDrillTypes(JsonUtils.toJsonString(req.getApplicableDrillTypes()));
        profile.setApplicablePhotoTypes(JsonUtils.toJsonString(req.getApplicablePhotoTypes()));
        profile.setPipelineJson(JsonUtils.toJsonString(req.getPipeline()));
        profile.setOutputSpecJson(JsonUtils.toJsonString(req.getOutputSpec()));
        profile.setReviewPolicy(req.getReviewPolicy());
        profile.setRemark(req.getRemark());
        return profile;
    }

    private String normalizeVersion(String version) {
        return StrUtil.removePrefix(version, "v");
    }
}
