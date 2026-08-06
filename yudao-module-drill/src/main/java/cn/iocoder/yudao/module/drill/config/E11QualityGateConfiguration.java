package cn.iocoder.yudao.module.drill.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(E11QualityGateProperties.class)
public class E11QualityGateConfiguration {
}
