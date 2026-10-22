package cn.iocoder.yudao.module.drill.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

//@TableName("drill_collection_task_change")
@KeySequence("drill_collection_task_change_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionTaskChangeDO extends TenantBaseDO {
    private Long id;
    private Long taskId;
    private Long collectorUserId;
    private String clientId;
    private String operation;
    private Integer taskVersion;
}
