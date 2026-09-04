package cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 图片资产分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImageAssetPageReqVO extends PageParam {

    private Long batchId; // 批次ID
    private String keyword; // 关键词
    private String drillType; // 钻探类型
    private String sceneType; // 场景类型
    private String wellType; // 井类型
    private String photoType; // 照片类型
    private String bladeNo; // 刀片编号
    private String metadataStatus; // 元数据补录状态
}
