package cn.iocoder.yudao.module.drill.service.enhance.external;

import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.CropBox;

import java.util.Map;

/**
 * 第三方视觉算法适配器契约。后续 HTTP/Python 服务实现此接口即可接入现有流水线。
 */
public interface ExternalImageAlgorithmClient {
    boolean supports(String stepCode);
    SubjectResult detectSubject(byte[] image, String modelVersion, Map<String, Object> parameters);
    PoseResult estimatePose(byte[] image, String modelVersion, Map<String, Object> parameters);
    byte[] restoreClarity(byte[] image, String modelVersion, Map<String, Object> parameters);

    record SubjectResult(CropBox cropBox, byte[] maskPng, double confidence,
                         double edgeCompleteRatio) {}
    record PoseResult(double rotationDegrees, double targetSubjectRatio,
                      double[] transformMatrix) {}
}
