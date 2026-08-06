package cn.iocoder.yudao.module.drill.controller.admin.image.vo.record;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 图片导入明细分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageImportRecordPageReqVO extends PageParam {

    @NotNull(message = "批次编号不能为空")
    private Long batchId;
    private String result;
    private String keyword;

}
