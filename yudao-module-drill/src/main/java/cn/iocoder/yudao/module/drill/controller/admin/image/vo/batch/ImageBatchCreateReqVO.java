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

    @Schema(description = "来源类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "LOCAL_UPLOAD")
    @NotBlank(message = "来源类型不能为空")
    private String sourceType;

    @Schema(description = "项目编号", example = "1001")
    private Long projectId;

    @Schema(description = "场景类型", example = "HISTORY")
    private String sceneType;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

}
