package cn.iocoder.yudao.module.drill.dal.dataobject.image;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 下游业务对图片资产的通用引用。图片模块不感知具体业务表。 */
@TableName("drill_image_asset_reference")
@KeySequence("drill_image_asset_reference_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageAssetReferenceDO extends TenantBaseDO {

    private Long id;
    private Long assetId;
    private String bizType;
    private Long bizId;

}
