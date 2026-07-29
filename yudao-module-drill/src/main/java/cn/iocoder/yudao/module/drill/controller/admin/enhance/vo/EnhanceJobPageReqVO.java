package cn.iocoder.yudao.module.drill.controller.admin.enhance.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EnhanceJobPageReqVO extends PageParam {
    private String keyword;
    private String status;
    private Long profileId;
    private Long batchId;
}
