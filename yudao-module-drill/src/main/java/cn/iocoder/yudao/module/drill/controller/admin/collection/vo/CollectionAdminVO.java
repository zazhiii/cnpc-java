package cn.iocoder.yudao.module.drill.controller.admin.collection.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CollectionAdminVO {

    private CollectionAdminVO() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TaskPageReq extends PageParam {
        @Size(max = 80)
        private String keyword;
        @Size(max = 32)
        private String taskType;
        @Size(max = 32)
        private String status;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate updatedFrom;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate updatedTo;
    }

    @Data
    public static class TaskSummary {
        private Long id;
        private String taskNo;
        private String taskType;
        private String status;
        private Long userId;
        private String collectorName;
        private String drillCode;
        private String drillModel;
        private String wellNumber;
        private Integer requiredPhotoCount;
        private Integer uploadedRequiredPhotoCount;
        private LocalDateTime updateTime;
    }

    public record TaskDetail(TaskSummary task, TaskForm form, List<Photo> photos) {
    }

    @Data
    public static class TaskForm {
        private String drillCode;
        private String drillType;
        private String drillModel;
        private String drillSize;
        private String manufacturer;
        private String productionBatch;
        private String wellNumber;
        private String wellSection;
        private String formation;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate operationTime;
        private BigDecimal footage;
        private BigDecimal drillingHours;
        private String location;
        private String notes;
    }

    public record Photo(String captureId, String title, boolean required, boolean blade,
                        String status, Long photoId, String fileUrl) {
    }

    @Data
    public static class PredictionSummary {
        private Long id;
        private String modelName;
        private String modelVersion;
        private String status;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
    }

    public record PredictionDetail(PredictionSummary prediction, List<Tooth> teeth) {
    }

    @Data
    public static class Tooth {
        private Integer toothIndex;
        private String wearType;
        private String wearLevel;
        private BigDecimal detectionConfidence;
        private BigDecimal typeConfidence;
        private BigDecimal levelConfidence;
    }
}
