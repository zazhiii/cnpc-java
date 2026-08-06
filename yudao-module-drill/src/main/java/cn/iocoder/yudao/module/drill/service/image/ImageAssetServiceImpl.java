package cn.iocoder.yudao.module.drill.service.image;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageAssetPageReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageAssetUpdateMetadataReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageAssetMapper;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_ASSET_NOT_EXISTS;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_OBJECT_NOT_FOUND;
import static cn.iocoder.yudao.module.drill.enums.ImageImportConstants.METADATA_STATUS_PENDING;

/** 图片资产服务。导入流程由 {@link ImageImportService} 独立负责。 */
@Service
public class ImageAssetServiceImpl implements ImageAssetService {

    @Resource
    private ImageAssetMapper imageAssetMapper;
    @Resource
    private ImageBatchService imageBatchService;
    @Resource
    private FileService fileService;

    @Override
    public PageResult<ImageAssetDO> getAssetPage(ImageAssetPageReqVO pageReqVO) {
        return imageAssetMapper.selectPage(pageReqVO);
    }

    @Override
    public ImageAssetDO getAsset(Long id) {
        ImageAssetDO asset = imageAssetMapper.selectById(id);
        if (asset == null) {
            throw exception(IMAGE_ASSET_NOT_EXISTS);
        }
        return asset;
    }

    @Override
    public ImageAssetDO getAssetForUpdate(Long id) {
        ImageAssetDO asset = imageAssetMapper.selectOneForUpdate(ImageAssetDO::getId, id);
        if (asset == null) {
            throw exception(IMAGE_ASSET_NOT_EXISTS);
        }
        return asset;
    }

    @Override
    public List<ImageAssetDO> getAssets(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return List.of();
        }
        return imageAssetMapper.selectByIds(ids);
    }

    @Override
    public List<ImageAssetDO> getAssetsByBatchId(Long batchId) {
        return imageAssetMapper.selectByBatchId(batchId);
    }

    @Override
    public byte[] getAssetContent(Long id) {
        ImageAssetDO asset = getAsset(id);
        try {
            byte[] content = fileService.getFileContent(asset.getFileConfigId(), asset.getObjectKey());
            if (content == null || content.length == 0) {
                throw exception(IMAGE_OBJECT_NOT_FOUND);
            }
            return content;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(IMAGE_OBJECT_NOT_FOUND);
        }
    }

    @Override
    public void validateAssetsAvailable(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            throw exception(IMAGE_ASSET_NOT_EXISTS);
        }
        int expected = new HashSet<>(ids).size();
        if (getAssets(ids).size() != expected) {
            throw exception(IMAGE_ASSET_NOT_EXISTS);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAssetMetadata(ImageAssetUpdateMetadataReqVO updateReqVO) {
        ImageAssetDO asset = getAsset(updateReqVO.getId());
        BeanUtils.copyProperties(updateReqVO, asset);
        asset.setMetadataStatus(resolveMetadataStatus(asset));
        imageAssetMapper.updateById(asset);
        imageBatchService.refreshMetadataStatistics(asset.getBatchId());
    }

    private String resolveMetadataStatus(ImageAssetDO asset) {
        boolean complete = cn.hutool.core.util.StrUtil.isAllNotBlank(asset.getDrillCode(), asset.getPhotoType())
                && (!"BLADE".equalsIgnoreCase(asset.getPhotoType())
                || cn.hutool.core.util.StrUtil.isNotBlank(asset.getBladeNo()));
        return complete ? "COMPLETE" : METADATA_STATUS_PENDING;
    }

}
