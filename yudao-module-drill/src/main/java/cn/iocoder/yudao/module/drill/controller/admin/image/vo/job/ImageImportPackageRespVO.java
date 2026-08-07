package cn.iocoder.yudao.module.drill.controller.admin.image.vo.job;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImageImportPackageRespVO {
    private Long id;
    private Long jobId;
    private String clientPackageId;
    private String relativePath;
    private String wellType;
    private String originalName;
    private Long declaredSize;
    private String status;
    private Integer totalImageCount;
    private Integer createdCount;
    private Integer duplicateCount;
    private Integer failedCount;
    private Integer ignoredJsonCount;
    private Integer warningCount;
    private Integer retryCount;
    private String errorCode;
    private String errorMessage;
    private Boolean canRetry;
    private Boolean canResetUpload;
    private LocalDateTime updateTime;
}

