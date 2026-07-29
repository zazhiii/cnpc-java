package cn.iocoder.yudao.module.drill.dal.dataobject.enhance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("drill_enhance_profile")
@KeySequence("drill_enhance_profile_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EnhanceProfileDO extends TenantBaseDO {
    private Long id;
    private String profileCode;
    private String name;
    private String version;
    private String applicableDrillTypes;
    private String applicablePhotoTypes;
    private String pipelineJson;
    private String outputSpecJson;
    private String reviewPolicy;
    private String status;
    private Long validationJobId;
    private LocalDateTime publishedAt;
    private String remark;
}
