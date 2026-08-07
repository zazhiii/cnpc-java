package cn.iocoder.yudao.module.drill.service.enhance;

import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.ImageVersionVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageReviewDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceItemDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageVersionDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageVersionPurposeDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.EnhanceItemMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.ImageReviewMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.ImageVersionMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.ImageVersionPurposeMapper;
import cn.iocoder.yudao.module.drill.service.image.ImageAssetReferenceService;
import cn.iocoder.yudao.module.drill.service.image.ImageAssetService;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.*;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.drill.enums.ImageImportConstants.REFERENCE_BIZ_ENHANCE_VERSION;

@Service
public class ImageVersionServiceImpl implements ImageVersionService {
    @Resource private ImageVersionMapper versionMapper;
    @Resource private ImageReviewMapper reviewMapper;
    @Resource private ImageVersionPurposeMapper purposeMapper;
    @Resource private EnhanceItemMapper itemMapper;
    @Resource private FileService fileService;
    @Resource private ImageAssetService imageAssetService;
    @Resource private ImageAssetReferenceService imageAssetReferenceService;
    @Resource private EnhanceJobService jobService;

    @Override
    public ImageVersionDO get(Long id) {
        ImageVersionDO version = versionMapper.selectById(id);
        if (version == null) throw exception(IMAGE_VERSION_NOT_EXISTS);
        return version;
    }

    @Override
    public ImageVersionDO getByEnhanceItemId(Long enhanceItemId) {
        ImageVersionDO version = versionMapper.selectByEnhanceItemId(enhanceItemId);
        if (version == null) throw exception(IMAGE_VERSION_NOT_EXISTS);
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, ImageVersionVO.ReviewReq req) {
        ImageVersionDO version = get(id);
        if (!PUBLISH_UNPUBLISHED.equals(version.getPublishStatus())
                || !Set.of(REVIEW_PENDING, REVIEW_REJECTED).contains(version.getReviewStatus())) {
            throw exception(IMAGE_VERSION_STATE_INVALID);
        }
        if (!Set.of("PASS", "REJECT").contains(req.getAction())) throw exception(IMAGE_VERSION_STATE_INVALID);
        ImageReviewDO review = new ImageReviewDO();
        review.setImageVersionId(id); review.setAction(req.getAction()); review.setReasonCode(req.getReasonCode());
        review.setComment(req.getComment()); review.setReviewerId(getLoginUserId()); review.setReviewedAt(LocalDateTime.now());
        reviewMapper.insert(review);
        ImageVersionDO update = new ImageVersionDO();
        update.setId(id);
        update.setReviewStatus("PASS".equals(req.getAction()) ? REVIEW_PASSED : REVIEW_REJECTED);
        versionMapper.updateById(update);
        EnhanceItemDO item = itemMapper.selectById(version.getEnhanceItemId());
        if (item != null && "PASS".equals(req.getAction())) {
            EnhanceItemDO itemUpdate = new EnhanceItemDO();
            itemUpdate.setId(item.getId());
            itemUpdate.setStatus(ITEM_REVIEW_PASSED);
            itemMapper.updateById(itemUpdate);
            jobService.refreshJob(item.getJobId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, ImageVersionVO.PublishReq req) {
        ImageVersionDO version = get(id);
        if (!Set.of(REVIEW_PASSED, REVIEW_AUTO_PASSED).contains(version.getReviewStatus())
                || !Set.of(PUBLISH_UNPUBLISHED, PUBLISH_WITHDRAWN).contains(version.getPublishStatus())) {
            throw exception(IMAGE_VERSION_STATE_INVALID);
        }
        imageAssetService.getAssetForUpdate(version.getAssetId());
        for (String purpose : req.getPurposes().stream().distinct().toList()) {
            if (Boolean.TRUE.equals(req.getSetAsDefault())) purposeMapper.clearDefault(version.getAssetId(), purpose);
            ImageVersionPurposeDO relation = purposeMapper.selectByVersionAndPurpose(id, purpose);
            if (relation == null) relation = new ImageVersionPurposeDO();
            relation.setImageVersionId(id); relation.setAssetId(version.getAssetId()); relation.setPurpose(purpose);
            relation.setPublishStatus(PUBLISH_PUBLISHED);
            relation.setDefaultVersion(Boolean.TRUE.equals(req.getSetAsDefault()));
            if (relation.getId() == null) purposeMapper.insert(relation); else purposeMapper.updateById(relation);
        }
        ImageVersionDO update = new ImageVersionDO();
        update.setId(id); update.setPublishStatus(PUBLISH_PUBLISHED);
        update.setPurpose(String.join(",", req.getPurposes().stream().distinct().toList()));
        update.setDefaultVersion(Boolean.TRUE.equals(req.getSetAsDefault())); update.setWithdrawReason(null);
        versionMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishOriginal(Long assetId, ImageVersionVO.PublishReq req) {
        ImageAssetDO asset = imageAssetService.getAssetForUpdate(assetId);
        ImageVersionDO version = versionMapper.selectOriginalByAssetId(assetId);
        if (version == null) {
            version = new ImageVersionDO();
            version.setAssetId(assetId);
            version.setVersionNo(versionMapper.selectNextVersionNo(assetId));
            version.setVersionType("ORIGINAL");
            // 原图不对应真实增强任务；使用资产编号的负数作为稳定且不冲突的虚拟处理项编号，
            // 兼容现有数据库 enhance_item_id 的非空和唯一约束。
            version.setEnhanceItemId(-assetId);
            version.setFileId(asset.getOriginalFileId());
            version.setFileConfigId(asset.getFileConfigId());
            version.setObjectKey(asset.getObjectKey());
            version.setSha256(asset.getSha256());
            version.setMimeType(asset.getMimeType());
            version.setFileSize(asset.getFileSize());
            version.setWidth(asset.getWidth());
            version.setHeight(asset.getHeight());
            version.setProfileCode("ORIGINAL");
            version.setProfileVersion("1.0.0");
            version.setAlgorithmVersionsJson("{}");
            version.setParametersJson("{}");
            version.setReviewStatus(REVIEW_AUTO_PASSED);
            version.setPublishStatus(PUBLISH_UNPUBLISHED);
            version.setDefaultVersion(false);
            versionMapper.insert(version);
        }
//        imageAssetReferenceService.bindAssets(Set.of(assetId), REFERENCE_BIZ_ENHANCE_VERSION, version.getId());
        publish(version.getId(), req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id, ImageVersionVO.WithdrawReq req) {
        ImageVersionDO version = get(id);
        if (!PUBLISH_PUBLISHED.equals(version.getPublishStatus())) throw exception(IMAGE_VERSION_STATE_INVALID);
        ImageVersionDO update = new ImageVersionDO();
        update.setId(id); update.setPublishStatus(PUBLISH_WITHDRAWN); update.setDefaultVersion(false);
        update.setWithdrawReason(req.getReason());
        versionMapper.updateById(update);
        purposeMapper.withdrawByVersionId(id);
    }

    @Override
    public String getAccessUrl(Long id) {
        ImageVersionDO version = get(id);
        return fileService.presignGetUrl(version.getFileConfigId(), version.getObjectKey(), 3600);
    }
}
