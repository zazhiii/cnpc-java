package cn.iocoder.yudao.module.drill.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("drill_collection_capture")
@KeySequence("drill_collection_capture_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionCaptureDO extends TenantBaseDO {
    private Long id;
    private Long taskId;
    private String captureCode;
    private String title;
    private String description;
    private Boolean requiredFlag;
    private Integer bladeNo;
    private Integer sortNo;
    private String state;
}
