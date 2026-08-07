package cn.iocoder.yudao.module.drill.controller.admin.quality.vo;

import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ImageQualityCheckRespVO {

    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private List<Item> items = new ArrayList<>();

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Item extends ImageQualityModelResult {
        private Long recordId;
        private Long assetId;
        private Long profileId;
        private String profileCode;
        private String profileVersion;
        private String modelCode;
        private String executionStatus;
        private Integer errorCode;
        private String errorMessage;
        private Map<String, Object> effectiveConfig;
    }

}
