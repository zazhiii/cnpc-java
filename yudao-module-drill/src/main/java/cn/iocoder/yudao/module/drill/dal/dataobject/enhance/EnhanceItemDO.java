package cn.iocoder.yudao.module.drill.dal.dataobject.enhance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("drill_enhance_item")
@KeySequence("drill_enhance_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EnhanceItemDO extends TenantBaseDO {
    private Long id;
    private Long jobId;
    private Long assetId;
    private String status;
    private Integer progress;
    private String currentStep;
    private Integer retryCount;
    private String errorCode;
    private String errorMessage;
    private String manualOverridesJson;
    private String workerId;
    private LocalDateTime leaseUntil;
    private LocalDateTime heartbeatTime;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private Integer lockVersion;
}
