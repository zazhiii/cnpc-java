package cn.iocoder.yudao.module.drill.controller.admin.enhance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.ImageAccessOverviewRespVO;
import cn.iocoder.yudao.module.drill.service.enhance.ImageAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 增强图片开放访问")
@RestController
@RequestMapping("/drill/image-access")
public class ImageAccessController {

    @Resource
    private ImageAccessService imageAccessService;

    @GetMapping("/overview")
    @Operation(summary = "获取增强图片开放接口概览")
    @PreAuthorize("@ss.hasPermission('drill:image-asset:query')")
    public CommonResult<ImageAccessOverviewRespVO> getOverview() {
        return success(imageAccessService.getOverview(currentRootUrl()));
    }

    @GetMapping("/version/{id}/content")
    @Operation(summary = "管理端预览或下载已发布增强图片")
    @PreAuthorize("@ss.hasPermission('drill:image-asset:query')")
    public void getVersionContent(@PathVariable("id") Long id,
                                  HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        writeContent(imageAccessService.getAdminContent(id), request, response);
    }

    private String currentRootUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private void writeContent(ImageAccessService.ImageContent content,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        String etag = "\"" + content.sha256() + "\"";
        if (etag.equals(request.getHeader("If-None-Match"))) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            response.setHeader("ETag", etag);
            return;
        }
        response.setContentType(content.mimeType());
        response.setContentLengthLong(content.fileSize());
        response.setHeader("ETag", etag);
        response.setHeader("Content-Disposition", ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8).build().toString());
        response.getOutputStream().write(content.bytes());
    }

}
