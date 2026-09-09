package cn.iocoder.yudao.module.drill.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(E11QualityGateProperties.class)
public class E11QualityGateConfiguration {

    @Bean("e11QualityGateRestTemplate")
    public RestTemplate e11QualityGateRestTemplate(RestTemplateBuilder builder,
                                                   E11QualityGateProperties properties) {
        return builder
                .connectTimeout(positive(properties.getConnectTimeout(), Duration.ofSeconds(3)))
                .readTimeout(positive(properties.getTimeout(), Duration.ofSeconds(30)))
                .errorHandler(response -> false)
                .build();
    }

    @Bean("e11QualityGateHealthRestTemplate")
    public RestTemplate e11QualityGateHealthRestTemplate(RestTemplateBuilder builder,
                                                         E11QualityGateProperties properties) {
        return builder
                .connectTimeout(positive(properties.getConnectTimeout(), Duration.ofSeconds(3)))
                .readTimeout(positive(properties.getHealthTimeout(), Duration.ofSeconds(2)))
                .errorHandler(response -> false)
                .build();
    }

    private Duration positive(Duration value, Duration defaultValue) {
        Duration resolved = value == null ? defaultValue : value;
        if (resolved.isZero() || resolved.isNegative()) {
            throw new IllegalArgumentException("E11 HTTP timeout must be positive");
        }
        return resolved;
    }
}
