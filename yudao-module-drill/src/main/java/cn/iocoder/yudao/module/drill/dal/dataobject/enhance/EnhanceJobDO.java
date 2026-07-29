package cn.iocoder.yudao.module.drill.dal.dataobject.enhance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("drill_enhance_job")
@KeySequence("drill_enhance_job_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EnhanceJobDO extends TenantBaseDO {
    private Long id;
    private String jobNo;
    private Long batchId;
    private Long profileId;
    private String profileVersion;
    private String status;
    private Integer priority;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer manualCount;
    private String idempotencyKey;
    private String requestHash;
    private Long submittedBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorSummary;
    private String parameterSnapshotJson;
    private Boolean validationJob;
    private Boolean autoReview;
    private Boolean autoPublish;
    private String remark;
}
