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

    /** 模型包虚拟环境中的 Python 可执行文件。 */
    private String pythonExecutable = "../e11_quality_gate_windows_20260804/.venv/Scripts/python.exe";
    /** 模型入口脚本。 */
    private String scriptPath = "../e11_quality_gate_windows_20260804/e11_quality_gate.py";
    /** 固定的 E11 ONNX 模型。 */
    private String modelPath = "../e11_quality_gate_windows_20260804/e11.onnx";
    private String device = "cpu";
    private Duration timeout = Duration.ofMinutes(2);
    /** 防止并发加载多个 ONNX 模型耗尽内存。 */
    private Integer maxConcurrent = 1;
    private Duration acquireTimeout = Duration.ofSeconds(5);

}
