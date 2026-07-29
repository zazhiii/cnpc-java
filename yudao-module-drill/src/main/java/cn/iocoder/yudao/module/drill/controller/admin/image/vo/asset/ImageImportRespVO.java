package cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 图片导入结果 Response VO")
@Data
public class ImageImportRespVO {

    private Integer totalCount = 0;
    private Integer createdCount = 0;
    private Integer duplicateCount = 0;
    private Integer failedCount = 0;
    private List<Item> items = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String clientFileId;
        private String originalName;
        private Long assetId;
        private Long duplicateAssetId;
        private String result;
        private String errorCode;
        private String errorMessage;
    }

}
