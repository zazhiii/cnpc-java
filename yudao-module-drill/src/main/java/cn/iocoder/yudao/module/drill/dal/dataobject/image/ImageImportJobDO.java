package cn.iocoder.yudao.module.drill.dal.dataobject.image;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("drill_image_import_job")
@KeySequence("drill_image_import_job_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageImportJobDO extends TenantBaseDO {
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
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorSummary;
}

