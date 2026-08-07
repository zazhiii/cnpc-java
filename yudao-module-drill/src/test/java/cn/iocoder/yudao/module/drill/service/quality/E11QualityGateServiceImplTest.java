package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.drill.config.E11QualityGateProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class E11QualityGateServiceImplTest {

    private final E11QualityGateServiceImpl service =
            new E11QualityGateServiceImpl(new E11QualityGateProperties(), new ObjectMapper());

    @Test
    void validateConfigShouldAcceptE11Parameters() {
        assertDoesNotThrow(() -> service.validateConfig(
                Map.of("confidence", 0.75, "minDetections", 1)));
    }

    @Test
    void validateConfigShouldRejectUnknownOrInvalidParameters() {
        assertThrows(ServiceException.class, () -> service.validateConfig(
                Map.of("confidence", 1.1, "minDetections", 1)));
        assertThrows(ServiceException.class, () -> service.validateConfig(
                Map.of("confidence", 0.75, "minDetections", 1, "unknown", true)));
        assertThrows(ServiceException.class, () -> service.validateConfig(
                Map.of("confidence", 0.75, "minDetections", 1.5)));
    }

}
