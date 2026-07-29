package cn.iocoder.yudao.module.drill.dal.dataobject.enhance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("drill_enhance_step_log")
@KeySequence("drill_enhance_step_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EnhanceStepLogDO extends TenantBaseDO {
    private Long id;
    private Long itemId;
    private String stepCode;
    private Integer stepOrder;
    private String algorithmName;
    private String algorithmVersion;
    private String parametersJson;
    private String metricsJson;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Long durationMs;
}
