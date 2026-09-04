package cn.iocoder.yudao.module.drill.dal.dataobject.image;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 钻头图片导入批次 DO。
 */
@TableName("drill_image_batch")
@KeySequence("drill_image_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageBatchDO extends TenantBaseDO {
    private Long id;
    private String batchNo;
    private String name;
    private String sourceType; // 来源类型（本地图片、本地ZIP）
//    private Long projectId;
//    private String sceneType;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer duplicateCount;
    private Integer pendingMetadataCount;
    private Integer sourcePackageCount;
    private Integer failedPackageCount;
    private LocalDateTime finishedTime;
    private String remark;
}
