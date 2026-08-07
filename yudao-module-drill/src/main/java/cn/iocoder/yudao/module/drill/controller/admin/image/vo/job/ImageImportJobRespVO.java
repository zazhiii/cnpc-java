package cn.iocoder.yudao.module.drill.controller.admin.image.vo.job;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ImageImportJobRespVO {
    private Long id;
    private String jobNo;
    private Long batchId;
    private String name;
    private String rootName;
    private String status;
    private Integer totalPackageCount;
    private Integer uploadedPackageCount;
    private Integer successPackageCount;
    private Integer failedPackageCount;
    private Integer totalImageCount;
    private Integer createdCount;
    private Integer duplicateCount;
    private Integer failedCount;
    private Long uploadedBytes;
    private Boolean canStart;
    private Boolean canRetry;
    private Boolean canFinishPartial;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorSummary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<ImageImportPackageRespVO> packages;
}
