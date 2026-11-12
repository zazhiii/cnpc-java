package cn.iocoder.yudao.module.drill.dal.entity.collection;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 现场采集照片文件实体，对应 {@code drill_collection_photo_file}。
 */
@TableName("drill_collection_photo_file")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
public class CollectionTaskPhotoFileEntity extends BaseDO {

    private Long id;
    private Long taskId;
    private String captureId;
    private String photoType;
    private String photoState;
    private String fileUrl;
}
