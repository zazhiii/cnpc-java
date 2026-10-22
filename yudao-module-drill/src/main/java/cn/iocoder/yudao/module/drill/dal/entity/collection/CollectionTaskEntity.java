package cn.iocoder.yudao.module.drill.dal.entity.collection;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 现场采集任务实体，对应 {@code drill_collection_task}。
 */
@TableName("drill_collection_task")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionTaskEntity extends BaseDO {

    private Long id;
    private String clientId;
    private Long userId;
    private String taskNo;
    private String taskType;
    private String status;
}
