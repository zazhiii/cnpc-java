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
    /** 兼容现有 E11 单模型接口和已落库方案。 */
    private String modelCode;
    /** 兼容现有 E11 单模型接口和已落库方案。 */
    private String modelConfigJson;
    private Boolean shortCircuit;
    private String status;
    private Boolean defaultProfile;
    private LocalDateTime enabledAt;
    private String remark;

}
