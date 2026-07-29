package cn.iocoder.yudao.module.drill.service.enhance.external;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认占位实现：明确报告不可用，避免仅打开配置就误执行未接通的算法。
 */
@Component
public class UnconfiguredImageAlgorithmClient implements ExternalImageAlgorithmClient {
    @Override public boolean supports(String stepCode) { return false; }
    @Override public SubjectResult detectSubject(byte[] image, String modelVersion, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("SUBJECT_CROP adapter is not configured");
    }
    @Override public PoseResult estimatePose(byte[] image, String modelVersion, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("ORIENTATION_NORMALIZE adapter is not configured");
    }
    @Override public byte[] restoreClarity(byte[] image, String modelVersion, Map<String, Object> parameters) {
        throw new UnsupportedOperationException("Restoration adapter is not configured");
    }
}
