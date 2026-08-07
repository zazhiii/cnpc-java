package cn.iocoder.yudao.module.drill.service.quality;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.QUALITY_MODEL_UNSUPPORTED;

@Component
public class ImageQualityModelRegistry {

    private final Map<String, ImageQualityModelExecutor> executors;

    public ImageQualityModelRegistry(List<ImageQualityModelExecutor> executorList) {
        Map<String, ImageQualityModelExecutor> registered = new LinkedHashMap<>();
        for (ImageQualityModelExecutor executor : executorList) {
            String code = normalize(executor.getModelCode());
            if (registered.putIfAbsent(code, executor) != null) {
                throw new IllegalStateException("Duplicate image quality model executor: " + code);
            }
        }
        this.executors = Map.copyOf(registered);
    }

    public ImageQualityModelExecutor getRequired(String modelCode) {
        ImageQualityModelExecutor executor = executors.get(normalize(modelCode));
        if (executor == null) {
            throw exception(QUALITY_MODEL_UNSUPPORTED, modelCode);
        }
        return executor;
    }

    private String normalize(String modelCode) {
        return modelCode == null ? "" : modelCode.trim().toUpperCase(Locale.ROOT);
    }

}
