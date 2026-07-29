package cn.iocoder.yudao.module.drill.service.enhance.pipeline;

import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.StepConfig;

public interface EnhanceStepProcessor {
    String code();
    void process(OpenCvEnhancePipeline.Context context, StepConfig config);
}
