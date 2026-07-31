package cn.iocoder.yudao.module.drill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "drill.image-enhance")
public class ImageEnhanceProperties {

    private Executor executor = new Executor();
    private Duration pollInterval = Duration.ofSeconds(2);
    private Duration itemTimeout = Duration.ofMinutes(10);
    private Integer maxRetries = 3;
    private ExternalService subjectService = new ExternalService();
    private ExternalService keypointService = new ExternalService();
    private ExternalService restorationService = new ExternalService();
    private OpenApi openApi = new OpenApi();

    @Data
    public static class Executor {
        private Integer coreSize = 2;
        private Integer queueCapacity = 20;
    }

    @Data
    public static class ExternalService {
        private Boolean enabled = false;
        private String baseUrl;
        private String apiKey;
        private String modelVersion;
        private Duration timeout = Duration.ofSeconds(30);
    }

    @Data
    public static class OpenApi {
        /**
         * 对外暴露的服务根地址，例如 https://example.com。为空时根据当前请求生成。
         */
        private String publicBaseUrl;
    }
}
