package cn.iocoder.yudao.module.drill.service.quality;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.QualityProfileVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileDO;
import cn.iocoder.yudao.module.drill.dal.mysql.quality.ImageQualityRecordMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.quality.QualityProfileMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.drill.enums.QualityConstants.*;

@Service
public class QualityProfileServiceImpl implements QualityProfileService {

    @Resource
    private QualityProfileMapper profileMapper;
    @Resource
    private ImageQualityRecordMapper recordMapper;
    @Resource
    private ImageQualityModelRegistry modelRegistry;

    @Override
    public List<QualityProfileDO> getList(String status, String modelCode) {
        return profileMapper.selectList(status, normalizeModelCodeNullable(modelCode));
    }

    @Override
    public QualityProfileDO get(Long id) {
        QualityProfileDO profile = profileMapper.selectById(id);
        if (profile == null) {
            throw exception(QUALITY_PROFILE_NOT_EXISTS);
        }
        return profile;
    }

    @Override
    public QualityProfileDO getEnabled(Long id) {
        QualityProfileDO profile = get(id);
        if (!PROFILE_ENABLED.equals(profile.getStatus())) {
            throw exception(QUALITY_PROFILE_STATE_INVALID);
        }
        return profile;
    }

    @Override
    public QualityProfileDO getDefaultEnabled() {
        QualityProfileDO profile = profileMapper.selectDefaultEnabled();
        if (profile == null) {
            throw exception(QUALITY_DEFAULT_PROFILE_NOT_EXISTS);
        }
        return profile;
    }

    @Override
    public Long create(QualityProfileVO.SaveReq req) {
        normalizeAndValidate(req);
        ensureUnique(req.getProfileCode(), req.getVersion(), null);
        //
        QualityProfileDO profile = toDO(req);
        profile.setStatus(PROFILE_DRAFT);
        profile.setDefaultProfile(false);
        profileMapper.insert(profile);
        return profile.getId();
    }

    @Override
    public void update(QualityProfileVO.SaveReq req) {
        if (req.getId() == null) {
            throw exception(QUALITY_PROFILE_INVALID, "更新时方案编号不能为空");
        }
        QualityProfileDO old = get(req.getId());
        if (!PROFILE_DRAFT.equals(old.getStatus())) {
            throw exception(QUALITY_PROFILE_STATE_INVALID);
        }
        normalizeAndValidate(req);
        ensureUnique(req.getProfileCode(), req.getVersion(), req.getId());
        QualityProfileDO update = toDO(req);
        update.setId(req.getId());
        profileMapper.updateById(update);
    }

    @Override
    public Long copy(QualityProfileVO.CopyReq req) {
        QualityProfileDO source = get(req.getSourceProfileId());
        String version = normalizeVersion(req.getNewVersion());
        ensureUnique(source.getProfileCode(), version, null);
        QualityProfileDO target = new QualityProfileDO();
        target.setProfileCode(source.getProfileCode());
        target.setName(req.getName());
        target.setVersion(version);
        target.setModelCode(source.getModelCode());
        target.setModelConfigJson(source.getModelConfigJson());
        target.setStatus(PROFILE_DRAFT);
        target.setDefaultProfile(false);
        target.setRemark(source.getRemark());
        profileMapper.insert(target);
        return target.getId();
    }

    @Override
    public void enable(Long id) {
        QualityProfileDO profile = get(id);
        if (!Set.of(PROFILE_DRAFT, PROFILE_DISABLED).contains(profile.getStatus())) {
            throw exception(QUALITY_PROFILE_STATE_INVALID);
        }
        ImageQualityModelExecutor executor = modelRegistry.getRequired(profile.getModelCode());
        executor.validateConfig(requiredConfig(profile));
        executor.checkAvailability();
        QualityProfileDO update = new QualityProfileDO();
        update.setId(id);
        update.setStatus(PROFILE_ENABLED);
        update.setEnabledAt(LocalDateTime.now());
        profileMapper.updateById(update);
    }

    @Override
    public void disable(Long id) {
        QualityProfileDO profile = get(id);
        if (!PROFILE_ENABLED.equals(profile.getStatus())) {
            throw exception(QUALITY_PROFILE_STATE_INVALID);
        }
        if (Boolean.TRUE.equals(profile.getDefaultProfile())) {
            throw exception(QUALITY_DEFAULT_PROFILE_PROTECTED);
        }
        QualityProfileDO update = new QualityProfileDO();
        update.setId(id);
        update.setStatus(PROFILE_DISABLED);
        profileMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        QualityProfileDO profile = getEnabled(id);
        if (Boolean.TRUE.equals(profile.getDefaultProfile())) {
            return;
        }
        profileMapper.clearDefault();
        QualityProfileDO update = new QualityProfileDO();
        update.setId(id);
        update.setDefaultProfile(true);
        profileMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        QualityProfileDO profile = get(id);
        if (Boolean.TRUE.equals(profile.getDefaultProfile())) {
            throw exception(QUALITY_DEFAULT_PROFILE_PROTECTED);
        }
        if (!Set.of(PROFILE_DRAFT, PROFILE_DISABLED).contains(profile.getStatus())) {
            throw exception(QUALITY_PROFILE_STATE_INVALID);
        }
        if (recordMapper.selectCountByProfileId(id) > 0) {
            throw exception(QUALITY_PROFILE_IN_USE);
        }
        profileMapper.deleteById(id);
    }

    private void normalizeAndValidate(QualityProfileVO.SaveReq req) {
        req.setProfileCode(req.getProfileCode().trim());
        req.setVersion(normalizeVersion(req.getVersion()));
        req.setModelCode(req.getModelCode().trim().toUpperCase(Locale.ROOT));
        ImageQualityModelExecutor executor = modelRegistry.getRequired(req.getModelCode());
        executor.validateConfig(req.getModelConfig());
    }

    private void ensureUnique(String profileCode, String version, Long currentId) {
        QualityProfileDO duplicate = profileMapper.selectByCodeAndVersion(profileCode, version);
        if (duplicate != null && !duplicate.getId().equals(currentId)) {
            throw exception(QUALITY_PROFILE_DUPLICATE);
        }
    }

    private QualityProfileDO toDO(QualityProfileVO.SaveReq req) {
        QualityProfileDO profile = new QualityProfileDO();
        profile.setProfileCode(req.getProfileCode());
        profile.setName(req.getName());
        profile.setVersion(req.getVersion());
        profile.setModelCode(req.getModelCode());
        profile.setModelConfigJson(JsonUtils.toJsonString(req.getModelConfig()));
        profile.setRemark(req.getRemark());
        return profile;
    }

    private Map<String, Object> requiredConfig(QualityProfileDO profile) {
        Map<String, Object> config = JsonUtils.parseMap(profile.getModelConfigJson());
        if (config == null) {
            throw exception(QUALITY_PROFILE_INVALID, "模型参数不是有效 JSON 对象");
        }
        return config;
    }

    private String normalizeVersion(String version) {
        return StrUtil.removePrefix(version.trim(), "v");
    }

    private String normalizeModelCodeNullable(String modelCode) {
        return StrUtil.isBlank(modelCode) ? null : modelCode.trim().toUpperCase(Locale.ROOT);
    }

}
