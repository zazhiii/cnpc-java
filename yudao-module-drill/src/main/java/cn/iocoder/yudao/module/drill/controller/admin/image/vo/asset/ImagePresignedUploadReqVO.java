package cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImagePresignedUploadReqVO {

    @NotNull(message = "批次编号不能为空")
    private Long batchId;

    @NotBlank(message = "文件名不能为空")
    private String name;

}
