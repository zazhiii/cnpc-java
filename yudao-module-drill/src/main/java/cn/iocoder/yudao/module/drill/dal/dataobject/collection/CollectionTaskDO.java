package cn.iocoder.yudao.module.drill.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;

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
    private String drillCode;
    private String drillType;
    private String drillModel;
    private String drillSize;
    private String manufacturer;
    private String productionBatch;
    private String wellNumber;
    private String wellSection;
    private String formation;
    private LocalDate operationTime;
    private BigDecimal footage;
    private BigDecimal drillingHours;
    private String location;
    private String notes;
    private Integer version;
    private String taskJson;
    private String resultJson;
    private Long collectorUserId;
    private LocalDateTime submitTime;
}
