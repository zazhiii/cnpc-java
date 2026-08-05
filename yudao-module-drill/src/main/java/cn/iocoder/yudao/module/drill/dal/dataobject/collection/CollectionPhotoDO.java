package cn.iocoder.yudao.module.drill.dal.dataobject.collection;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("drill_collection_photo")
@KeySequence("drill_collection_photo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionPhotoDO extends TenantBaseDO {
    private Long id;
    private Long taskId;
    private String captureId;
    private String photoType;
    private String photoState;
    private String fileUrl;
    private String auditRegionJson;
    private String auditReportJson;
}
