package cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 创建图片导入批次 Request VO")
@Data
public class ImageBatchCreateReqVO {

    @Schema(description = "批次名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "川西区域历史图片")
    @NotBlank(message = "批次名称不能为空")
    @Size(max = 100, message = "批次名称不能超过 100 个字符")
    private String name;

}
