package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.drill.config.E11QualityGateProperties;
import cn.iocoder.yudao.module.drill.service.quality.checker.E11ModelQualityChecker;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class E11QualityGateServiceImplTest {

    private final E11QualityGateProperties properties = new E11QualityGateProperties();
    private final E11QualityGateHttpClient client = mock(E11QualityGateHttpClient.class);
    private E11QualityGateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new E11QualityGateServiceImpl(
                new E11ModelQualityChecker(properties, client, new ObjectMapper()));
    }

    @Test
    void validateConfigShouldAcceptE11ParametersAndRejectInvalidOnes() {
        assertDoesNotThrow(() -> service.validateConfig(Map.of("confidence", 0.75, "minDetections", 1)));
        assertThrows(ServiceException.class,
                () -> service.validateConfig(Map.of("confidence", 1.1, "minDetections", 1)));
        assertThrows(ServiceException.class,
                () -> service.validateConfig(Map.of("confidence", 0.75, "minDetections", 1.5)));
    }

    @Test
    void shouldTreatPassedFalseAsSuccessfulExecution() {
        when(client.predict(any(), eq(0.8D), eq(1), anyString()))
                .thenReturn(new E11QualityGateHttpClient.Response(200, rejectedReport()));

        ImageQualityModelResult result = service.execute(image(),
                Map.of("confidence", 0.8D, "minDetections", 1));

        assertFalse(result.getPassed());
        assertEquals(0, result.getAcceptedCount());
        assertNotNull(result.getDurationMs());
    }

    @Test
    void shouldMapBusyResponse() {
        when(client.predict(any(), anyDouble(), anyInt(), anyString()))
                .thenReturn(new E11QualityGateHttpClient.Response(429,
                        "{\"status\":\"error\",\"error\":{\"code\":\"MODEL_BUSY\"}}"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.execute(image(), Map.of("confidence", 0.8D, "minDetections", 1)));

        assertEquals(E11_QUALITY_GATE_BUSY.getCode(), error.getCode());
    }

    @Test
    void shouldMapFastApiErrorsToExistingBusinessCodes() {
        Map<Integer, Integer> expectedCodes = Map.of(
                400, E11_QUALITY_GATE_ARGUMENT_ERROR.getCode(),
                413, IMAGE_FILE_TOO_LARGE.getCode(),
                415, IMAGE_FORMAT_UNSUPPORTED.getCode(),
                422, IMAGE_DECODE_FAILED.getCode(),
                500, E11_QUALITY_GATE_MODEL_ERROR.getCode(),
                503, E11_QUALITY_GATE_UNAVAILABLE.getCode());
        expectedCodes.forEach((status, expectedCode) -> {
            reset(client);
            when(client.predict(any(), anyDouble(), anyInt(), anyString()))
                    .thenReturn(new E11QualityGateHttpClient.Response(status, "{}"));
            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.execute(image(), Map.of("confidence", 0.8D, "minDetections", 1)));
            assertEquals(expectedCode, error.getCode());
        });
    }

    @Test
    void shouldRejectMalformedSuccessfulResponse() {
        when(client.predict(any(), anyDouble(), anyInt(), anyString()))
                .thenReturn(new E11QualityGateHttpClient.Response(200, "{\"passed\":false}"));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.execute(image(), Map.of("confidence", 0.8D, "minDetections", 1)));
        assertEquals(E11_QUALITY_GATE_INVALID_RESPONSE.getCode(), error.getCode());
    }

    @Test
    void readinessRequiresWarmModelAndCpuProvider() {
        when(client.ready()).thenReturn(new E11QualityGateHttpClient.Response(200, """
                {"status":"ready","modelVersion":"E11","modelSha256":"abc",
                 "device":"cpu","provider":"CPUExecutionProvider","warmedUp":true}
                """));
        assertDoesNotThrow(service::checkAvailability);
    }

    private QualityImageInput image() {
        return new QualityImageInput(1L, new byte[]{1, 2, 3}, "test.png", "image/png", 10, 10);
    }

    private String rejectedReport() {
        return """
                {
                  "schema_version":"1.0",
                  "model_version":"E11",
                  "task":"cutting_tooth_instance_segmentation_quality_gate",
                  "passed":false,
                  "reason":"insufficient_detections_above_threshold",
                  "threshold":0.8,
                  "min_detections":1,
                  "accepted_count":0,
                  "candidate_count":0,
                  "rejection_counts":{"below_threshold":0,"invalid_polygon":0,"unexpected_label":0},
                  "confidence_summary":{"minimum":null,"maximum":null,"mean":null},
                  "image":{"width":10,"height":10},
                  "detections":[],
                  "inference_speed_ms":{}
                }
                """;
    }
}
