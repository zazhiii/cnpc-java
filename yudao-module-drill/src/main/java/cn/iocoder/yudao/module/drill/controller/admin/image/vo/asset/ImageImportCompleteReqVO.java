package cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ImageImportCompleteReqVO {

    @NotNull(message = "批次编号不能为空")
    private Long batchId;

    @Valid
    @NotEmpty(message = "导入文件不能为空")
    private List<FileItem> files;

    private String unknownMetadataPolicy = "PENDING";

    @Data
    public static class FileItem {

        private String clientFileId;
        @NotNull(message = "文件配置编号不能为空")
        private Long configId;
        @NotEmpty(message = "对象键不能为空")
        private String path;
        @NotEmpty(message = "文件地址不能为空")
        private String url;
        @NotEmpty(message = "文件名不能为空")
        private String name;
        @NotNull(message = "文件大小不能为空")
        private Long size;
        private String mimeType;
        @NotEmpty(message = "文件摘要不能为空")
        private String sha256;
        private String drillCode;
        private String drillType;
        private String sceneType;
        private String photoType;
        private String bladeNo;
        private String remark;
    }

}
