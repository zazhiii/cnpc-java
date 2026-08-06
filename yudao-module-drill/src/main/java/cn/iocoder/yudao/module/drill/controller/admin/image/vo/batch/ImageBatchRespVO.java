package cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 图片导入批次 Response VO")
@Data
public class ImageBatchRespVO {

    private Long id;
    private String batchNo;
    private String name;
    private String sourceType;
    private Long projectId;
    private String sceneType;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer duplicateCount;
    private Integer pendingMetadataCount;
    private LocalDateTime finishedTime;
    private String remark;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
