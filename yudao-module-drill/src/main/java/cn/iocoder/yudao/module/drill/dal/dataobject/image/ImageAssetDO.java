package cn.iocoder.yudao.module.drill.dal.dataobject.image;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 钻头图片资产 DO。
 */
@TableName("drill_image_asset")
@KeySequence("drill_image_asset_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageAssetDO extends TenantBaseDO {

    private Long id;
    private String assetNo;
    private Long batchId;
    private Long projectId;
    private String drillCode;
    private String drillType;
    private String sceneType;
    private String wellType;
    private String photoType;
    private String bladeNo;
    private String sourceType;
    private String sourceBizId;
    private Long sourcePackageId;
    private String sourceEntryPath;
    private Long originalFileId;
    private Long fileConfigId;
    private String objectKey;
    private String originalUrl;
    private String originalName;
    private String mimeType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String sha256;
    private LocalDateTime captureTime;
    private String metadataStatus;
    private String archiveStatus;
    private String remark;

}
