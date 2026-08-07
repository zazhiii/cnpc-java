package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;

import java.util.Map;

public interface ImageQualityModelExecutor {

    String getModelCode();

    void validateConfig(Map<String, Object> config);

    void checkAvailability();

    ImageQualityModelResult execute(QualityImageInput image, Map<String, Object> config);

}
