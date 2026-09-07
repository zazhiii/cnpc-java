package cn.iocoder.yudao.module.drill.controller.admin.quality.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QualityProfileVO {

    private QualityProfileVO() {
    }

    @Data
    public static class SaveReq {

        private Long id;

        @NotBlank
        private String profileCode;

        @NotBlank
        private String name;

        @NotBlank
        @Pattern(regexp = "^v?\\d+\\.\\d+\\.\\d+$")
        private String version;

        @NotEmpty
        @Valid
        private List<CheckItem> checks;

        private Boolean shortCircuit = true;

        private String remark;
    }

    @Data
    public static class CheckItem {
        @NotBlank
        private String checkCode;
        @NotNull
        private Map<String, Object> config;
        private Integer sortNo;
    }

    @Data
    public static class CopyReq {
        @NotNull
        private Long sourceProfileId;
        @NotBlank
        @Pattern(regexp = "^v?\\d+\\.\\d+\\.\\d+$")
        private String newVersion;
        @NotBlank
        private String name;
    }

    @Data
    @Schema(description = "图片质检方案")
    public static class Resp {
        private Long id;
        private String profileCode;
        private String name;
        private String version;
        private String modelCode;
        private Map<String, Object> modelConfig;
        private List<CheckItem> checks;
        private Boolean shortCircuit;
        private String status;
        private Boolean defaultProfile;
        private LocalDateTime enabledAt;
        private String remark;
        private LocalDateTime updateTime;
    }

}
