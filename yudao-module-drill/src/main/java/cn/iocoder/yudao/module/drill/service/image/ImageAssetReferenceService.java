package cn.iocoder.yudao.module.drill.service.image;

import java.util.Collection;

/** 图片资产引用服务，用于在不反向依赖业务模块的前提下保护已使用图片。 */
public interface ImageAssetReferenceService {

    void bindAssets(Collection<Long> assetIds, String bizType, Long bizId);

    boolean hasReferences(Collection<Long> assetIds);

}
