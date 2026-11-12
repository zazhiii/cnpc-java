package cn.iocoder.yudao.module.drill.controller.android.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class AppCollectionTaskVO {
    private AppCollectionTaskVO() {}

    @Data
    public static class DrillBit {
        @Size(max = 80) private String code;
        // 草稿允许尚未填写钻头类型；进入拍摄清单前由 App 执行必填校验。
        @Size(max = 64) private String type;
        @Size(max = 64) private String model;
        @Size(max = 32) private String size;
        @Size(max = 100) private String manufacturer;
        @Size(max = 64) private String productionBatch;
    }

    @Data
    public static class Operation {
        @Size(max = 80) private String wellNumber;
        @Size(max = 100) private String wellSection;
        @Size(max = 80) private String formation;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate operationTime;
        @DecimalMin("0") private BigDecimal footage;
        @DecimalMin("0") private BigDecimal drillingHours;
        @Size(max = 200) private String location;
        @Size(max = 1000) private String notes;
    }

    @Data
    public static class CreateReq {
        @NotBlank @Size(max = 64) private String clientId;
        @NotBlank() @Pattern(regexp = "RUN_IN|PULL_OUT") private String taskType;
        @Valid @NotNull private DrillBit drillBit;
        @Valid @NotNull private Operation operation;
        @NotNull @Min(1) @Max(64) private Integer bladeCount;
    }

    @Data
    public static class UpdateReq {
        @NotNull @Min(1) private Integer version;
        @Valid @NotNull private DrillBit drillBit;
        @Valid @NotNull private Operation operation;
        @NotNull @Min(1) @Max(64) private Integer bladeCount;
    }

    @Data
    public static class Capture {
        private Long id;
        private String code;
        private String title;
        private String description;
        private Boolean required;
        private Integer bladeNo;
        private Integer sortNo;
        private String state;
    }

    @Data
    public static class Resp {
        private Long id;
        private String clientId;
        // 任务编号
        private String taskNo;
        // 任务类型：入井、出井
        private String taskType;
        private String status;
        private Integer version;
        private DrillBit drillBit;
        private Operation operation;
        private List<Capture> captures = new ArrayList<>();
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime updatedAt;
        private Boolean deleted;
    }

    @Data
    public static class PageReq extends PageParam {
        private String status;
        private String keyword;
    }

}
