package cn.iocoder.yudao.module.drill.service.quality.checker;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.module.drill.config.E11QualityGateProperties;
import cn.iocoder.yudao.module.drill.service.quality.E11GateReportParser;
import cn.iocoder.yudao.module.drill.service.quality.E11QualityGateHttpClient;
import cn.iocoder.yudao.module.drill.service.quality.ImageQualityCheckResult;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;

@Component
@Slf4j
public class E11ModelQualityChecker implements ImageQualityChecker {

    public static final String CHECK_CODE = "E11_MODEL";

    private final E11QualityGateProperties properties;
    private final E11QualityGateHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final E11GateReportParser reportParser;
    private final Semaphore concurrencyGuard;

    public E11ModelQualityChecker(E11QualityGateProperties properties,
                                  E11QualityGateHttpClient httpClient,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.reportParser = new E11GateReportParser(objectMapper);
        int maxConcurrent = properties.getMaxConcurrent() == null ? 1 : properties.getMaxConcurrent();
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("drill.e11-quality-gate.max-concurrent must be at least 1");
        }
        this.concurrencyGuard = new Semaphore(maxConcurrent, true);
    }

    @Override
    public String getCheckCode() {
        return CHECK_CODE;
    }

    @Override
    public void validateConfig(Map<String, Object> config) {
        if (config == null || !config.keySet().equals(Set.of("confidence", "minDetections"))) {
            throw exception(QUALITY_PROFILE_INVALID, "E11 参数必须且只能包含 confidence、minDetections");
        }
        double confidence = number(config.get("confidence"), "confidence").doubleValue();
        int minDetections = integer(config.get("minDetections"), "minDetections");
        if (!Double.isFinite(confidence) || confidence < 0D || confidence > 1D) {
            throw exception(QUALITY_PROFILE_INVALID, "confidence 必须在 [0,1] 范围内");
        }
        if (minDetections < 1 || minDetections > 300) {
            throw exception(QUALITY_PROFILE_INVALID, "minDetections 必须在 [1,300] 范围内");
        }
    }

    @Override
    public void checkAvailability(Map<String, Object> config) {
        validateConfig(config);
        checkAvailability();
    }

    /** 兼容旧模型执行器的无参数可用性检查。 */
    public void checkAvailability() {
        try {
            E11QualityGateHttpClient.Response response = httpClient.ready();
            if (response == null || response.statusCode() != 200 || !isReady(response.body())) {
                throw exception(E11_QUALITY_GATE_UNAVAILABLE);
            }
        } catch (RestClientException | IllegalArgumentException ex) {
            log.warn("E11 readiness check failed: {}", ex.getMessage());
            throw exception(E11_QUALITY_GATE_UNAVAILABLE);
        }
    }

    @Override
    public ImageQualityCheckResult check(QualityImageInput image, Map<String, Object> config) {
        long startedNanos = System.nanoTime();
        ImageQualityModelResult modelResult = executeModel(image, config);

        Map<String, Object> metrics = new LinkedHashMap<>();
        putIfNotNull(metrics, "modelVersion", modelResult.getModelVersion());
        putIfNotNull(metrics, "threshold", modelResult.getThreshold());
        putIfNotNull(metrics, "minDetections", modelResult.getMinDetections());
        putIfNotNull(metrics, "acceptedCount", modelResult.getAcceptedCount());
        putIfNotNull(metrics, "candidateCount", modelResult.getCandidateCount());
        putIfNotNull(metrics, "rejectionCounts", modelResult.getRejectionCounts());
        putIfNotNull(metrics, "confidenceSummary", modelResult.getConfidenceSummary());

        Map<String, Object> details = JsonUtils.convertObject(modelResult,
                new TypeReference<Map<String, Object>>() {
                });
        boolean passed = Boolean.TRUE.equals(modelResult.getPassed());
        return CheckerSupport.result(CHECK_CODE, passed,
                passed ? null : "MODEL_NO_DETECTION", modelResult.getReason(),
                metrics, details, startedNanos);
    }

    /**
     * 执行 E11 模型并返回原始模型结果。仅为旧 {@code E11QualityGateService} 兼容层保留，
     * 新的质检流程应调用 {@link #check(QualityImageInput, Map)}。
     */
    public ImageQualityModelResult executeModel(QualityImageInput image, Map<String, Object> config) {
        validateConfig(config);
        double confidence = number(config.get("confidence"), "confidence").doubleValue();
        int minDetections = integer(config.get("minDetections"), "minDetections");
        long startedAt = System.nanoTime();
        boolean acquired = false;
        try {
            acquired = concurrencyGuard.tryAcquire(
                    durationMillis(properties.getAcquireTimeout(), Duration.ofSeconds(5)), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw exception(E11_QUALITY_GATE_BUSY);
            }
            String requestId = TracerUtils.getTraceId();
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }
            E11QualityGateHttpClient.Response response = httpClient.predict(
                    image, confidence, minDetections, requestId);
            if (response == null) {
                throw exception(E11_QUALITY_GATE_INVALID_RESPONSE);
            }
            if (response.statusCode() != 200) {
                throw mapHttpError(response.statusCode(), image.mimeType());
            }
            try {
                ImageQualityModelResult result = reportParser.parse(response.body(), confidence, minDetections);
                result.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
                return result;
            } catch (IllegalArgumentException ex) {
                log.error("Invalid E11 response: {}", ex.getMessage());
                throw exception(E11_QUALITY_GATE_INVALID_RESPONSE);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw exception(E11_QUALITY_GATE_INTERRUPTED);
        } catch (E11QualityGateHttpClient.ResponseTooLargeException ex) {
            throw exception(E11_QUALITY_GATE_INVALID_RESPONSE);
        } catch (ResourceAccessException ex) {
            if (hasCause(ex, SocketTimeoutException.class)) {
                throw exception(E11_QUALITY_GATE_TIMEOUT,
                        durationMillis(properties.getTimeout(), Duration.ofSeconds(30)));
            }
            if (hasCause(ex, ConnectException.class)) {
                log.warn("E11 service connection failed: {}", ex.getMessage());
            }
            throw exception(E11_QUALITY_GATE_UNAVAILABLE);
        } catch (RestClientException ex) {
            log.warn("E11 service request failed: {}", ex.getMessage());
            throw exception(E11_QUALITY_GATE_UNAVAILABLE);
        } finally {
            if (acquired) {
                concurrencyGuard.release();
            }
        }
    }

    private boolean isReady(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return root != null && "ready".equals(root.path("status").asText())
                    && root.path("warmedUp").asBoolean(false)
                    && "E11".equals(root.path("modelVersion").asText())
                    && !root.path("modelSha256").asText().isBlank()
                    && "CPUExecutionProvider".equals(root.path("provider").asText());
        } catch (Exception ex) {
            return false;
        }
    }

    private RuntimeException mapHttpError(int status, String mimeType) {
        return switch (status) {
            case 400 -> exception(E11_QUALITY_GATE_ARGUMENT_ERROR);
            case 413 -> exception(IMAGE_FILE_TOO_LARGE, 50);
            case 415 -> exception(IMAGE_FORMAT_UNSUPPORTED, mimeType);
            case 422 -> exception(IMAGE_DECODE_FAILED);
            case 429 -> exception(E11_QUALITY_GATE_BUSY);
            case 500 -> exception(E11_QUALITY_GATE_MODEL_ERROR);
            case 503 -> exception(E11_QUALITY_GATE_UNAVAILABLE);
            default -> exception(E11_QUALITY_GATE_PROCESS_ERROR, status);
        };
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Number number(Object value, String name) {
        if (!(value instanceof Number number)) {
            throw exception(QUALITY_PROFILE_INVALID, name + " 必须是数值");
        }
        return number;
    }

    private int integer(Object value, String name) {
        Number number = number(value, name);
        double raw = number.doubleValue();
        if (!Double.isFinite(raw) || raw != Math.rint(raw)) {
            throw exception(QUALITY_PROFILE_INVALID, name + " 必须是整数");
        }
        return number.intValue();
    }

    private long durationMillis(Duration configured, Duration defaultValue) {
        Duration value = configured == null ? defaultValue : configured;
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("E11 quality gate duration must be positive");
        }
        return value.toMillis();
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
