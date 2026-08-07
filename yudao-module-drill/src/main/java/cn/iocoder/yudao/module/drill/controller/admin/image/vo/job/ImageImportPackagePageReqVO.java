package cn.iocoder.yudao.module.drill.controller.admin.image.vo.job;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImageImportPackagePageReqVO extends PageParam {
    @NotNull
    private Long jobId;
    private String status;
    private String keyword;
}

