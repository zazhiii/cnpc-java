package cn.iocoder.yudao.module.drill.controller.admin.enhance.vo;

import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.OutputSpec;
import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.StepConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class EnhanceProfileVO {
    private EnhanceProfileVO() {
    }

    @Data
    public static class SaveReq {
        private Long id;
        @NotBlank private String profileCode;
        @NotBlank private String name;
        @NotBlank @Pattern(regexp = "^v?\\d+\\.\\d+\\.\\d+$") private String version;
        private List<String> applicableDrillTypes = new ArrayList<>();
        private List<String> applicablePhotoTypes = new ArrayList<>();
        @NotEmpty private List<@Valid StepConfig> pipeline;
        @NotNull @Valid private OutputSpec outputSpec;
        @NotBlank private String reviewPolicy;
        private String remark;
    }

    @Data
    public static class CopyReq {
        @NotNull private Long sourceProfileId;
        @NotBlank @Pattern(regexp = "^v?\\d+\\.\\d+\\.\\d+$") private String newVersion;
        @NotBlank private String name;
    }

    @Data
    public static class ValidateReq {
        @NotNull private Long profileId;
        @NotEmpty @Size(max = 20) private List<Long> assetIds;
    }

    @Data
    @Schema(description = "增强方案响应")
    public static class Resp {
        private Long id;
        private String profileCode;
        private String name;
        private String version;
        private List<String> applicableDrillTypes;
        private List<String> applicablePhotoTypes;
        private List<StepConfig> pipeline;
        private OutputSpec outputSpec;
        private String reviewPolicy;
        private String status;
        private Long validationJobId;
        private LocalDateTime publishedAt;
        private String remark;
        private LocalDateTime updateTime;
    }
}
