package cn.iocoder.yudao.module.drill.controller.android.enums;

import lombok.Getter;

@Getter
public enum TaskStatus {
    DRAFT("DRAFT", "草稿");

    private String code;
    private String label;

    TaskStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
