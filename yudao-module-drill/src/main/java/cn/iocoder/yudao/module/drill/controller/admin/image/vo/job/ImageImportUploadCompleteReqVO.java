package cn.iocoder.yudao.module.drill.controller.admin.image.vo.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImageImportUploadCompleteReqVO {
    @NotNull
    private Long configId;
    @NotBlank
    private String path;
    @NotBlank
    private String url;
    @NotNull
    private Long size;
}

