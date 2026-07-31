package cn.iocoder.yudao.module.drill.dal.dataobject.enhance;

import lombok.Data;

/**
 * 已发布增强图片用途统计。
 */
@Data
public class ImageAccessPurposeCountDO {

    private String purpose;
    private Long count;

}
