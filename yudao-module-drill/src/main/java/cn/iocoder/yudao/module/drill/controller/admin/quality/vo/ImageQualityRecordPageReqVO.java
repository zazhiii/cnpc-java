package cn.iocoder.yudao.module.drill.controller.admin.quality.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImageQualityRecordPageReqVO extends PageParam {
    private Long assetId;
    private Long profileId;
    private String modelCode;
    private String executionStatus;
    private Boolean passed;
}
