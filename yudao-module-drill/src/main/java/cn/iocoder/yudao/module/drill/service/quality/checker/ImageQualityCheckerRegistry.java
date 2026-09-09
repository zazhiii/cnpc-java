package cn.iocoder.yudao.module.drill.service.quality.checker;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.QUALITY_CHECKER_UNSUPPORTED;

@Component
public class ImageQualityCheckerRegistry {

    private final Map<String, ImageQualityChecker> checkers;

    public ImageQualityCheckerRegistry(List<ImageQualityChecker> checkerList) {
        Map<String, ImageQualityChecker> checkerMap = new LinkedHashMap<>();
        for (ImageQualityChecker checker : checkerList) {
            String checkCode = normalize(checker.getCheckCode());
            if (checkerMap.putIfAbsent(checkCode, checker) != null) {
                throw new IllegalStateException("重复的图片质检 Checker：" + checkCode);
            }
        }
        this.checkers = Map.copyOf(checkerMap);
    }

    public ImageQualityChecker getRequired(String checkCode) {
        String normalizedCode = normalize(checkCode);
        ImageQualityChecker checker = checkers.get(normalizedCode);
        if (checker == null) {
            throw exception(QUALITY_CHECKER_UNSUPPORTED, normalizedCode);
        }
        return checker;
    }

    private static String normalize(String checkCode) {
        return checkCode == null ? "" : checkCode.trim().toUpperCase(Locale.ROOT);
    }

}
