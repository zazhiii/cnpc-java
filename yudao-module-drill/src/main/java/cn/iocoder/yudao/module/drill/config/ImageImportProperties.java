package cn.iocoder.yudao.module.drill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "drill.image-import")
public class ImageImportProperties {
    private Executor executor = new Executor();
    private Duration pollInterval = Duration.ofSeconds(2);
    private Duration packageTimeout = Duration.ofMinutes(30);
    private Duration uploadTimeout = Duration.ofHours(24);
    private Integer maxRetries = 3;
    private Integer maxPackagesPerJob = 1000;
    private Long maxJobBytes = 10L * 1024 * 1024 * 1024;
    private Long maxArchiveBytes = 200L * 1024 * 1024;
    private Long maxExpandedBytes = 2L * 1024 * 1024 * 1024;
    private Integer maxEntries = 5000;
    private Integer maxCompressionRatio = 100;
    private Long maxImageBytes = 60L * 1024 * 1024;
    private Long maxPixelCount = 50_000_000L;

    @Data
    public static class Executor {
        private Integer coreSize = 2;
        private Integer queueCapacity = 20;
    }
}

