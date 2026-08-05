package cn.iocoder.yudao.module.drill.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("drill_collection_task")
@KeySequence("drill_collection_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionTaskDO extends TenantBaseDO {
    private Long id;
    private String clientId;
    private String taskNo;
    private String taskType;
    private String status;
    private String taskJson;
    private String resultJson;
    private Long collectorUserId;
    private LocalDateTime submitTime;
}
