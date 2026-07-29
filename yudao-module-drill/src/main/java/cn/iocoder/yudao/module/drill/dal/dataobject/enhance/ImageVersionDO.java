package cn.iocoder.yudao.module.drill.dal.dataobject.enhance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("drill_image_version")
@KeySequence("drill_image_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageVersionDO extends TenantBaseDO {
    private Long id;
    private Long assetId;
    private Integer versionNo;
    private String versionType;
    private Long enhanceItemId;
    private Long fileId;
    private Long fileConfigId;
    private String objectKey;
    private String sha256;
    private String mimeType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String profileCode;
    private String profileVersion;
    private String algorithmVersionsJson;
    private String parametersJson;
    private String metricsJson;
    private String transformMatrixJson;
    private Long maskFileId;
    private String reviewStatus;
    private String publishStatus;
    private String purpose;
    private Boolean defaultVersion;
    private String withdrawReason;
}
