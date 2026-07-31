package cn.iocoder.yudao.module.drill.controller.admin.enhance.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ImageAccessOverviewRespVO {

    private String publicBaseUrl;
    private String tokenEndpoint;
    private String apiVersion;
    private String downloadMode;
    private List<String> requiredScopes;
    private Long publishedAssetCount;
    private Long publishedDefaultCount;
    private Map<String, Long> purposeCounts;
    private LocalDateTime latestPublishedAt;
    private List<RecentVersion> recentVersions;

    @Data
    public static class RecentVersion {
        private Long assetId;
        private String assetNo;
        private Long imageVersionId;
        private Integer versionNo;
        private String drillCode;
        private String photoType;
        private String purpose;
        private Integer width;
        private Integer height;
        private String mimeType;
        private Long fileSize;
        private String sha256;
        private LocalDateTime publishedAt;
    }

}
