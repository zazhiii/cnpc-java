package cn.iocoder.yudao.module.drill.dal.entity.collection;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 现场采集任务表单实体，对应 {@code drill_collection_task_form}。
 */
@TableName("drill_collection_task_form")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionTaskFormEntity extends BaseDO {

    private Long id;
    private Long taskId;
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
}
