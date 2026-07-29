package cn.iocoder.yudao.module.drill.controller.admin.image.vo.batch;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 图片导入批次分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageBatchPageReqVO extends PageParam {

    @Schema(description = "批次编号或名称")
    private String keyword;

    @Schema(description = "来源类型")
    private String sourceType;

    @Schema(description = "批次状态")
    private String status;

    @Schema(description = "项目编号")
    private Long projectId;

    @Schema(description = "创建时间范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
