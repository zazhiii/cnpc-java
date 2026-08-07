package cn.iocoder.yudao.module.drill.controller.admin.quality.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "图片资产质检请求")
@Data
public class ImageQualityCheckReqVO {

    private Long assetId;
//    @Size(max = 20, message = "一次最多质检 20 张图片")
    private List<Long> assetIds;
    private Long profileId;

    @DecimalMin(value = "0.0", message = "置信度必须大于等于 0")
    @DecimalMax(value = "1.0", message = "置信度必须小于等于 1")
    private Double confidence;

    @Min(value = 1, message = "最少检测数量必须大于等于 1")
    @Max(value = 300, message = "最少检测数量不能超过 300")
    private Integer minDetections;

}
