package cn.iocoder.yudao.module.drill.controller.admin.collection.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

public final class AppCollectionVO {
    private AppCollectionVO() {}

    @Data
    public static class SyncReq {
        @NotBlank private String clientId;
        @NotBlank private String taskNo;
        @NotBlank private String taskType;
        @NotBlank private String status;
        @NotBlank private String taskJson;
    }

    @Data
    public static class TaskResp {
        private Long serverId;
        private String clientId;
        private String taskNo;
        private String taskType;
        private String status;
        private String taskJson;
        private String resultJson;
        private LocalDateTime updateTime;
        private List<PhotoResp> photos;
    }

    @Data
    public static class StatisticsResp {
        private Long totalTaskCount;
        private Long pendingUploadTaskCount;
        private Long completedTaskCount;
    }

    @Data
    public static class UploadReq {
        private MultipartFile file;
        @NotBlank private String photoType;
        @NotBlank private String photoState;
        private String auditRegionJson;
        private String auditReportJson;
    }

    @Data
    public static class PhotoResp {
        private String captureId;
        private String photoType;
        private String photoState;
        private String fileUrl;
        private String auditRegionJson;
        private String auditReportJson;
    }
}
