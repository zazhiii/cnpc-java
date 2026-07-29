package cn.iocoder.yudao.module.drill.controller.admin.enhance.vo;

import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.ManualOverrides;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class EnhanceJobVO {
    private EnhanceJobVO() {
    }

    @Data
    public static class CreateReq {
        private List<Long> assetIds;
        private Long batchId;
        private Long profileId;
        @Min(1) @Max(10) private Integer priority = 5;
        private Boolean autoReview = false;
        private Boolean autoPublish = false;
        private String remark;
    }

    @Data
    public static class RetryReq {
        @Valid private ManualOverrides manualOverrides;
    }

    @Data
    public static class Resp {
        private Long id;
        private String jobNo;
        private Long batchId;
        private String source;
        private Long profileId;
        private String profileName;
        private String profileVersion;
        private String status;
        private Integer priority;
        private Integer progress;
        private Integer totalCount;
        private Integer successCount;
        private Integer failedCount;
        private Integer manualCount;
        private Long submittedBy;
        private LocalDateTime createTime;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long durationMs;
        private Boolean validationJob;
        private String errorSummary;
        private String remark;
    }

    @Data
    public static class ItemResp {
        private Long id;
        private Long jobId;
        private Long assetId;
        private String assetNo;
        private String originalName;
        private String originalUrl;
        private String status;
        private Integer progress;
        private String currentStep;
        private Integer retryCount;
        private String errorCode;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long durationMs;
    }

    @Data
    public static class StepResp {
        private Long id;
        private Long itemId;
        private String stepCode;
        private Integer stepOrder;
        private String algorithmName;
        private String algorithmVersion;
        private String parametersJson;
        private String metricsJson;
        private String status;
        private String errorCode;
        private String errorMessage;
        private Long durationMs;
    }

    @Data
    public static class StatisticsResp {
        private Long pendingCount;
        private Long processingCount;
        private Long manualCount;
        private Double todaySuccessRate;
        private Long averageDurationMs;
    }
}
