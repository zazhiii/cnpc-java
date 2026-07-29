package cn.iocoder.yudao.module.drill.controller.admin.enhance.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

public final class ImageVersionVO {
    private ImageVersionVO() {
    }

    @Data
    public static class ReviewReq {
        @NotBlank private String action;
        private String reasonCode;
        private String comment;
    }

    @Data
    public static class PublishReq {
        @NotEmpty private List<String> purposes;
        private Boolean setAsDefault = true;
    }

    @Data
    public static class WithdrawReq {
        @NotBlank private String reason;
    }
}
