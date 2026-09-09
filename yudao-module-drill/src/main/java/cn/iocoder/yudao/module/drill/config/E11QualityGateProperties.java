package cn.iocoder.yudao.module.drill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * E11 图片质量门禁配置。
 */
@Data
@ConfigurationProperties(prefix = "drill.e11-quality-gate")
public class E11QualityGateProperties {

    /** E11 FastAPI 服务根地址。 */
    private String baseUrl = "http://127.0.0.1:18081";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration timeout = Duration.ofSeconds(30);
    private Duration healthTimeout = Duration.ofSeconds(2);
    /** Java 调用端 bulkhead；Python 服务仍是模型并发控制的最终边界。 */
    private Integer maxConcurrent = 1;
    private Duration acquireTimeout = Duration.ofSeconds(5);
    private Integer maxResponseBytes = 5 * 1024 * 1024;

}
