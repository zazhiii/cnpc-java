package cn.iocoder.yudao.module.drill.dal.dataobject.enhance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("drill_image_review")
@KeySequence("drill_image_review_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageReviewDO extends TenantBaseDO {
    private Long id;
    private Long imageVersionId;
    private String action;
    private String reasonCode;
    private String comment;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
}
