package cn.iocoder.yudao.module.drill.dal.dataobject.image;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 钻头图片导入批次 DO。
 */
@TableName("drill_image_batch")
@KeySequence("drill_image_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageBatchDO extends TenantBaseDO {

    private Long id;
    private String batchNo;
    private String name;
    private String sourceType;
    private Long projectId;
    private String sceneType;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer duplicateCount;
    private Integer pendingMetadataCount;
    private String remark;

}
