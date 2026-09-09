package cn.iocoder.yudao.module.drill.service.quality;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.QualityProfileVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileItemDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileDO;
import cn.iocoder.yudao.module.drill.dal.mysql.quality.ImageQualityRecordMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.quality.QualityProfileItemMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.quality.QualityProfileMapper;
import cn.iocoder.yudao.module.drill.service.quality.checker.ImageQualityChecker;
import cn.iocoder.yudao.module.drill.service.quality.checker.ImageQualityCheckerRegistry;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
    private QualityProfileItemMapper profileItemMapper;
    @Resource
    private ImageQualityCheckerRegistry checkerRegistry;

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
    public List<QualityProfileVO.CheckItem> getChecks(Long profileId) {
        List<QualityProfileItemDO> items = profileItemMapper.selectListByProfileId(profileId);
        if (!items.isEmpty()) {
            return items.stream().map(this::toCheckItem).toList();
        }
        QualityProfileDO profile = get(profileId);
        if (MODEL_E11.equals(profile.getModelCode()) && StrUtil.isNotBlank(profile.getModelConfigJson())) {
            QualityProfileVO.CheckItem legacy = new QualityProfileVO.CheckItem();
            legacy.setCheckCode("E11_MODEL");
            Map<String, Object> config = JsonUtils.parseMap(profile.getModelConfigJson());
            if (config == null) {
                throw exception(QUALITY_PROFILE_INVALID, "E11 参数不是有效 JSON 对象");
            }
            legacy.setConfig(config);
            legacy.setSortNo(10);
            return List.of(legacy);
        }
        return List.of();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QualityProfileVO.SaveReq req) {
        normalizeAndValidate(req);
        ensureUnique(req.getProfileCode(), req.getVersion(), null);

        QualityProfileDO profile = toDO(req);
        profile.setStatus(PROFILE_DRAFT);
        profile.setDefaultProfile(false);
        profileMapper.insert(profile);
        saveProfileItems(profile.getId(), req.getChecks());
        return profile.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        profileItemMapper.deleteByProfileId(req.getId());
        saveProfileItems(req.getId(), req.getChecks());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        target.setShortCircuit(source.getShortCircuit());
        target.setStatus(PROFILE_DRAFT);
        target.setDefaultProfile(false);
        target.setRemark(source.getRemark());
        profileMapper.insert(target);
        List<QualityProfileItemDO> sourceItems = profileItemMapper.selectListByProfileId(source.getId());
        if (sourceItems.isEmpty()) {
            throw exception(QUALITY_PROFILE_INVALID, "被复制的方案没有质检条件");
        }
        List<QualityProfileVO.CheckItem> targetItems = sourceItems.stream().map(item -> {
            QualityProfileVO.CheckItem targetItem = new QualityProfileVO.CheckItem();
            targetItem.setCheckCode(item.getCheckCode());
            targetItem.setConfig(requiredConfig(item));
            targetItem.setSortNo(item.getSortNo());
            return targetItem;
        }).toList();
        saveProfileItems(target.getId(), targetItems);
        return target.getId();
    }

    @Override
    public void enable(Long id) {
        QualityProfileDO profile = get(id);
        if (!Set.of(PROFILE_DRAFT, PROFILE_DISABLED).contains(profile.getStatus())) {
            throw exception(QUALITY_PROFILE_STATE_INVALID);
        }
        List<QualityProfileItemDO> items = profileItemMapper.selectListByProfileId(id);
        if (items.isEmpty()) {
            throw exception(QUALITY_PROFILE_INVALID, "方案没有质检条件");
        }
        for (QualityProfileItemDO item : items) {
            ImageQualityChecker checker = checkerRegistry.getRequired(item.getCheckCode());
            Map<String, Object> config = requiredConfig(item);
            checker.validateConfig(config);
            checker.checkAvailability(config);
        }
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
        profileItemMapper.deleteByProfileId(id);
        profileMapper.deleteById(id);
    }

    private void normalizeAndValidate(QualityProfileVO.SaveReq req) {
        if (req == null || StrUtil.isBlank(req.getProfileCode()) || StrUtil.isBlank(req.getName())
                || StrUtil.isBlank(req.getVersion())) {
            throw exception(QUALITY_PROFILE_INVALID, "方案编码、名称和版本不能为空");
        }
        req.setProfileCode(req.getProfileCode().trim());
        req.setName(req.getName().trim());
        req.setVersion(normalizeVersion(req.getVersion()));
        if (!req.getVersion().matches("\\d+\\.\\d+\\.\\d+")) {
            throw exception(QUALITY_PROFILE_INVALID, "版本必须符合 x.y.z 格式");
        }
        if (req.getChecks() == null || req.getChecks().isEmpty()) {
            throw exception(QUALITY_PROFILE_INVALID, "至少选择一个质检条件");
        }
        req.setShortCircuit(req.getShortCircuit() == null || req.getShortCircuit());

        Set<String> checkCodes = new HashSet<>();
        List<QualityProfileVO.CheckItem> normalizedChecks = new ArrayList<>(req.getChecks().size());
        for (int index = 0; index < req.getChecks().size(); index++) {
            QualityProfileVO.CheckItem item = req.getChecks().get(index);
            if (item == null || StrUtil.isBlank(item.getCheckCode())) {
                throw exception(QUALITY_PROFILE_INVALID, "质检项编码不能为空");
            }
            String checkCode = item.getCheckCode().trim().toUpperCase(Locale.ROOT);
            if (!checkCodes.add(checkCode)) {
                throw exception(QUALITY_PROFILE_INVALID, "质检项不能重复：" + checkCode);
            }
            if (item.getConfig() == null) {
                throw exception(QUALITY_PROFILE_INVALID, checkCode + " 配置不能为空");
            }
            int sortNo = item.getSortNo() == null ? (index + 1) * 10 : item.getSortNo();
            if (sortNo < 0) {
                throw exception(QUALITY_PROFILE_INVALID, checkCode + " 的 sortNo 不能小于 0");
            }
            checkerRegistry.getRequired(checkCode).validateConfig(item.getConfig());
            item.setCheckCode(checkCode);
            item.setSortNo(sortNo);
            normalizedChecks.add(item);
        }
        normalizedChecks.sort(Comparator.comparing(QualityProfileVO.CheckItem::getSortNo)
                .thenComparing(QualityProfileVO.CheckItem::getCheckCode));
        req.setChecks(normalizedChecks);
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
        profile.setShortCircuit(req.getShortCircuit());
        profile.setRemark(req.getRemark());
        return profile;
    }

    private void saveProfileItems(Long profileId, List<QualityProfileVO.CheckItem> checks) {
        List<QualityProfileItemDO> items = checks.stream().map(check -> {
            QualityProfileItemDO item = new QualityProfileItemDO();
            item.setProfileId(profileId);
            item.setCheckCode(check.getCheckCode());
            item.setConfigJson(JsonUtils.toJsonString(check.getConfig()));
            item.setSortNo(check.getSortNo());
            return item;
        }).toList();
        profileItemMapper.insertBatch(items);
    }

    private Map<String, Object> requiredConfig(QualityProfileItemDO item) {
        Map<String, Object> config = JsonUtils.parseMap(item.getConfigJson());
        if (config == null) {
            throw exception(QUALITY_PROFILE_INVALID, item.getCheckCode() + " 参数不是有效 JSON 对象");
        }
        return config;
    }

    private QualityProfileVO.CheckItem toCheckItem(QualityProfileItemDO item) {
        QualityProfileVO.CheckItem check = new QualityProfileVO.CheckItem();
        check.setCheckCode(item.getCheckCode());
        check.setConfig(requiredConfig(item));
        check.setSortNo(item.getSortNo());
        return check;
    }

    private String normalizeVersion(String version) {
        return StrUtil.removePrefix(version.trim(), "v");
    }

    private String normalizeModelCodeNullable(String modelCode) {
        return StrUtil.isBlank(modelCode) ? null : modelCode.trim().toUpperCase(Locale.ROOT);
    }

}
