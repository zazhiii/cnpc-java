package cn.iocoder.yudao.module.drill.dal.dataobject.quality;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("drill_quality_profile_item")
@KeySequence("drill_quality_profile_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class QualityProfileItemDO extends TenantBaseDO {

    private Long id;
    private Long profileId;
    private String checkCode;
    private String configJson;
    private Integer sortNo;

}
