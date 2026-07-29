package cn.iocoder.yudao.module.drill.dal.dataobject.image;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 钻头图片导入明细 DO。
 */
@TableName("drill_image_import_record")
@KeySequence("drill_image_import_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageImportRecordDO extends TenantBaseDO {

    private Long id;
    private Long batchId;
    private String clientFileId;
    private String originalName;
    private Long fileSize;
    private String sha256;
    private String result;
    private Long assetId;
    private Long duplicateAssetId;
    private String errorCode;
    private String errorMessage;

}
