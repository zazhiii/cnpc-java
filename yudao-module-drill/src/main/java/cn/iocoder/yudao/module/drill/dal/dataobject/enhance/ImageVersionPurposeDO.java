package cn.iocoder.yudao.module.drill.dal.dataobject.enhance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("drill_image_version_purpose")
@KeySequence("drill_image_version_purpose_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageVersionPurposeDO extends TenantBaseDO {
    private Long id;
    private Long imageVersionId;
    private Long assetId;
    private String purpose;
    private String publishStatus;
    private Boolean defaultVersion;
}
