package cn.iocoder.yudao.module.drill.dal.dataobject.image;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("drill_image_import_package")
@KeySequence("drill_image_import_package_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageImportPackageDO extends TenantBaseDO {
    private Long id;
    private Long jobId;
    private Long batchId;
    private String clientPackageId;
    private String relativePath;
    private String wellType;
    private String originalName;
    private Long declaredSize;
    private Long lastModified;
    private Long archiveFileId;
    private Long fileConfigId;
    private String objectKey;
    private String originalUrl;
    private Long actualSize;
    private String sha256;
    private String status;
    private Integer totalImageCount;
    private Integer createdCount;
    private Integer duplicateCount;
    private Integer failedCount;
    private Integer ignoredJsonCount;
    private Integer warningCount;
    private Integer retryCount;
    private String workerId;
    private LocalDateTime leaseUntil;
    private LocalDateTime heartbeatTime;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorCode;
    private String errorMessage;
    private Boolean cleanupPending;
}

