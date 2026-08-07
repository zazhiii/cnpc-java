package cn.iocoder.yudao.module.drill.controller.admin.image.vo.job;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ImageImportJobCreateReqVO {
    @NotBlank
    @Size(max = 100)
    private String name;
    @NotBlank
    @Size(max = 255)
    private String rootName;
    @Valid
    @NotEmpty
    private List<PackageItem> packages;

    @Data
    public static class PackageItem {
        @NotBlank
        @Size(max = 96)
        private String clientPackageId;
        @NotBlank
        @Size(max = 512)
        private String relativePath;
        @NotBlank
        @Size(max = 255)
        private String fileName;
        @NotNull
        private Long size;
        private Long lastModified;
    }
}

