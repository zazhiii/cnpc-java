package cn.iocoder.yudao.module.drill.service.image;

import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetReferenceDO;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageAssetReferenceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Service
public class ImageAssetReferenceServiceImpl implements ImageAssetReferenceService {

    @Resource
    private ImageAssetReferenceMapper referenceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindAssets(Collection<Long> assetIds, String bizType, Long bizId) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        Set<Long> existingAssetIds = new HashSet<>();
        referenceMapper.selectByBusiness(bizType, bizId)
                .forEach(reference -> existingAssetIds.add(reference.getAssetId()));
        assetIds.stream().filter(existingAssetIds::add).forEach(assetId ->
                referenceMapper.insert(new ImageAssetReferenceDO()
                        .setAssetId(assetId)
                        .setBizType(bizType)
                        .setBizId(bizId)));
    }

    @Override
    public boolean hasReferences(Collection<Long> assetIds) {
        return referenceMapper.existsByAssetIds(assetIds);
    }

}
