package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.module.drill.config.E11QualityGateProperties;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class E11QualityGateServiceImpl implements E11QualityGateService {

    private static final int PASS_EXIT_CODE = 0;
    private static final int REJECT_EXIT_CODE = 10;
    private static final int ARGUMENT_EXIT_CODE = 2;
    private static final int MODEL_ERROR_EXIT_CODE = 20;
    private static final int MAX_STDOUT_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ERROR_BYTES = 32 * 1024;

    private final E11QualityGateProperties properties;
    private final E11GateReportParser reportParser;
    private final Semaphore concurrencyGuard;

    public E11QualityGateServiceImpl(E11QualityGateProperties properties,
                                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.reportParser = new E11GateReportParser(objectMapper);
        int maxConcurrent = properties.getMaxConcurrent() == null ? 1 : properties.getMaxConcurrent();
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("drill.e11-quality-gate.max-concurrent 必须大于等于 1");
        }
        this.concurrencyGuard = new Semaphore(maxConcurrent, true);
    }

    @Override
    public String getModelCode() {
        return "E11";
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
    public void checkAvailability() {
        requiredFile(properties.getPythonExecutable(), "Python 虚拟环境");
        requiredFile(properties.getScriptPath(), "模型脚本");
        requiredFile(properties.getModelPath(), "E11 模型");
        durationMillis(properties.getTimeout(), Duration.ofMinutes(2));
        durationMillis(properties.getAcquireTimeout(), Duration.ofSeconds(5));
    }

    @Override
    public ImageQualityModelResult execute(QualityImageInput image, Map<String, Object> config) {
        validateConfig(config);
        double confidence = number(config.get("confidence"), "confidence").doubleValue();
        int minDetections = integer(config.get("minDetections"), "minDetections");
        long startedAt = System.nanoTime();
        boolean acquired = false;
        try {
            // 并发控制
            acquired = concurrencyGuard.tryAcquire(durationMillis(properties.getAcquireTimeout(), Duration.ofSeconds(5)),
                    TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw exception(E11_QUALITY_GATE_BUSY);
            }

            // 执行质检
            ImageQualityModelResult result = executeProcess(image, confidence, minDetections);
            result.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw exception(E11_QUALITY_GATE_INTERRUPTED);
        } finally {
            if (acquired) {
                concurrencyGuard.release();
            }
        }
    }

    private ImageQualityModelResult executeProcess(QualityImageInput image, double confidence,
                                                   int minDetections) {
        Path python = requiredFile(properties.getPythonExecutable(), "Python 虚拟环境");
        Path script = requiredFile(properties.getScriptPath(), "模型脚本");
        Path model = requiredFile(properties.getModelPath(), "E11 模型");
        Path workDirectory = null;
        Process process = null;
        try {
            workDirectory = Files.createTempDirectory("e11-quality-gate-");
            Path input = workDirectory.resolve("input" + extensionForMimeType(image.mimeType()));
            Path stdout = workDirectory.resolve("stdout.json");
            Path stderr = workDirectory.resolve("stderr.log");
            Files.write(input, image.content());

            List<String> command = new ArrayList<>();
            command.add(python.toString());
            command.add(script.toString());
            command.add("--model");
            command.add(model.toString());
            command.add("--input");
            command.add(input.toString());
            command.add("--confidence");
            command.add(Double.toString(confidence));
            command.add("--min-detections");
            command.add(Integer.toString(minDetections));
            command.add("--device");
            command.add(properties.getDevice());

            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile());
            process = processBuilder.start();
            long timeoutMs = durationMillis(properties.getTimeout(), Duration.ofMinutes(2));
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                terminate(process);
                log.warn("E11 quality gate timed out after {} ms", timeoutMs);
                throw exception(E11_QUALITY_GATE_TIMEOUT, timeoutMs);
            }

            int exitCode = process.exitValue();
            // 通过 || 未通过
            if (exitCode == PASS_EXIT_CODE || exitCode == REJECT_EXIT_CODE) {
                String json = readAllLimited(stdout, MAX_STDOUT_BYTES);
                try {
                    return reportParser.parse(json, exitCode, confidence, minDetections);
                } catch (IllegalArgumentException ex) {
                    log.error("Invalid E11 response: {}", ex.getMessage());
                    throw exception(E11_QUALITY_GATE_INVALID_RESPONSE);
                }
            }

            String error = compact(readTail(stderr, MAX_ERROR_BYTES));
            log.error("E11 quality gate failed, exitCode={}, stderr={}", exitCode, error);
            if (exitCode == MODEL_ERROR_EXIT_CODE) {
                throw exception(E11_QUALITY_GATE_MODEL_ERROR);
            }
            if (exitCode == ARGUMENT_EXIT_CODE) {
                throw exception(E11_QUALITY_GATE_ARGUMENT_ERROR);
            }
            throw exception(E11_QUALITY_GATE_PROCESS_ERROR, exitCode);
        } catch (IOException ex) {
            log.error("Unable to run E11 quality gate", ex);
            throw exception(E11_QUALITY_GATE_UNAVAILABLE);
        } catch (InterruptedException ex) {
            if (process != null) {
                terminate(process);
            }
            Thread.currentThread().interrupt();
            throw exception(E11_QUALITY_GATE_INTERRUPTED);
        } finally {
            deleteTemporaryDirectory(workDirectory);
        }
    }

    private Path requiredFile(String configuredPath, String description) {
        if (configuredPath == null || configuredPath.isBlank()) {
            log.error("E11 {} is not configured", description);
            throw exception(E11_QUALITY_GATE_UNAVAILABLE);
        }
        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            log.error("E11 {} does not exist: {}", description, path);
            throw exception(E11_QUALITY_GATE_UNAVAILABLE);
        }
        return path;
    }

    private String extensionForMimeType(String mimeType) {
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw exception(IMAGE_FORMAT_UNSUPPORTED, mimeType);
        };
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

    private String readAllLimited(Path path, int limit) throws IOException {
        long size = Files.size(path);
        if (size > limit) {
            throw new IOException("模型标准输出超过 " + limit + " 字节");
        }
        return Files.readString(path, StandardCharsets.UTF_8).trim();
    }

    private String readTail(Path path, int limit) throws IOException {
        if (!Files.exists(path)) {
            return "";
        }
        long size = Files.size(path);
        try (InputStream input = Files.newInputStream(path)) {
            long skip = Math.max(0L, size - limit);
            if (skip > 0) {
                input.skipNBytes(skip);
            }
            return new String(input.readNBytes(limit), StandardCharsets.UTF_8);
        }
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private void terminate(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private void deleteTemporaryDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.warn("Unable to delete E11 temporary file: {}", path, ex);
                }
            });
        } catch (IOException ex) {
            log.warn("Unable to clean E11 temporary directory: {}", directory, ex);
        }
    }
}
