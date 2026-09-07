package cn.iocoder.yudao.module.drill.service.quality.checker;

import cn.iocoder.yudao.module.drill.service.quality.ImageQualityCheckResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;

import java.util.Map;

// 统一质检项接口
public interface ImageQualityChecker {

    String getCheckCode();

    void validateConfig(Map<String, Object> config);

    void checkAvailability(Map<String, Object> config);

    ImageQualityCheckResult check(
            QualityImageInput image,
            Map<String, Object> config);
}