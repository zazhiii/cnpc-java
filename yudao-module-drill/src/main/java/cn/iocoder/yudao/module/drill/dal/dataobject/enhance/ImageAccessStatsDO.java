package cn.iocoder.yudao.module.drill.dal.dataobject.enhance;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已发布增强图片概览统计。
 */
@Data
public class ImageAccessStatsDO {

    private Long publishedAssetCount;
    private Long publishedDefaultCount;
    private LocalDateTime latestPublishedAt;

}
