package cn.iocoder.yudao.module.drill.dal.dataobject.quality;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("drill_image_quality_record")
@KeySequence("drill_image_quality_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageQualityRecordDO extends TenantBaseDO {

    private Long id;
    private Long assetId;
    private Long profileId;
    private String profileCode;
    private String profileVersion;
    private String modelCode;
    private String modelVersion;
    private String effectiveConfigJson;
    private String executionStatus;
    private Boolean passed;
    private String reason;
    private String resultJson;
    private Integer errorCode;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

}
