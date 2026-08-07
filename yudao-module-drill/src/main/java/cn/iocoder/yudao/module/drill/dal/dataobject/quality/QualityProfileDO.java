package cn.iocoder.yudao.module.drill.dal.dataobject.quality;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("drill_quality_profile")
@KeySequence("drill_quality_profile_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class QualityProfileDO extends TenantBaseDO {

    private Long id;
    private String profileCode;
    private String name;
    private String version;
    private String modelCode;
    private String modelConfigJson;
    private String status;
    private Boolean defaultProfile;
    private LocalDateTime enabledAt;
    private String remark;

}
