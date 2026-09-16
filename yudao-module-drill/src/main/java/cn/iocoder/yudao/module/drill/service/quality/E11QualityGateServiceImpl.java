package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.module.drill.service.quality.checker.E11ModelQualityChecker;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * E11 旧模型执行器兼容层。
 *
 * @deprecated 具体质检实现已迁移至 {@link E11ModelQualityChecker}；新代码应通过 Checker 调用。
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class E11QualityGateServiceImpl implements E11QualityGateService {

    private final E11ModelQualityChecker checker;

    @Override
    public String getModelCode() {
        return "E11";
    }

    @Override
    public void validateConfig(Map<String, Object> config) {
        checker.validateConfig(config);
    }

    @Override
    public void checkAvailability() {
        checker.checkAvailability();
    }

    @Override
    public ImageQualityModelResult execute(QualityImageInput image, Map<String, Object> config) {
        return checker.executeModel(image, config);
    }

}
