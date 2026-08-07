package cn.iocoder.yudao.module.drill.service.image.model;

import lombok.Data;

@Data
public class ArchiveImageImportRequest {
    private Long jobId;
    private Long packageId;
    private Long batchId;
    private String entryPath;
    private String originalName;
    private String wellType;
    private byte[] content;
}

