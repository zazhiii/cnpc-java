package cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 图片资产分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageAssetPageReqVO extends PageParam {

    private Long batchId;
    private String keyword;
    private String drillType;
    private String sceneType;
    private String photoType;
    private String bladeNo;
    private String metadataStatus;
}
