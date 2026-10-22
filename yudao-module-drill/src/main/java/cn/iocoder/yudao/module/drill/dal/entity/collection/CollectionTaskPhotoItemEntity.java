package cn.iocoder.yudao.module.drill.dal.entity.collection;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 现场采集任务照片项实体，对应 {@code drill_collection_task_photo_item}。
 */
@TableName("drill_collection_task_photo_item")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionTaskPhotoItemEntity extends BaseDO {

    private Long id;
    private Long taskId;
    private String photoType;
    private String title;
    private Boolean requiredFlag;
    private Integer bladeNo;
    private Integer sortNo;
    private String status;
}
