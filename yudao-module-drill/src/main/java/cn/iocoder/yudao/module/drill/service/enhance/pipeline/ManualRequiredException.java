package cn.iocoder.yudao.module.drill.service.enhance.pipeline;

import lombok.Getter;

@Getter
public class ManualRequiredException extends RuntimeException {
    private final String errorCode;
    private final String manualAction;

    public ManualRequiredException(String errorCode, String message, String manualAction) {
        super(message);
        this.errorCode = errorCode;
        this.manualAction = manualAction;
    }
}
