package cn.iocoder.yudao.module.drill.controller.open.enhance.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class OpenImageListReqVO {

    @NotBlank(message = "图片用途不能为空")
    private String purpose;
    private Long batchId;
    private String drillCode;
    private String photoType;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime updatedAfter;
    private String cursor;
    @Min(value = 1, message = "limit 最小为 1")
    @Max(value = 500, message = "limit 最大为 500")
    private Integer limit = 100;

}
