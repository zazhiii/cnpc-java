package cn.iocoder.yudao.module.drill.controller.open.enhance.vo;

import lombok.Data;

import java.util.List;

@Data
public class OpenImageCursorRespVO {

    private String nextCursor;
    private Boolean hasMore;
    private List<OpenImageItemRespVO> items;

}
