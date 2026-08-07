package cn.iocoder.yudao.module.drill.service.quality.model;

public record QualityImageInput(Long assetId, byte[] content, String originalName,
                                String mimeType, Integer width, Integer height) {
}
