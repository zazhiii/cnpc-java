package cn.iocoder.yudao.module.drill.controller.open.enhance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.drill.controller.open.enhance.vo.OpenImageCursorRespVO;
import cn.iocoder.yudao.module.drill.controller.open.enhance.vo.OpenImageListReqVO;
import cn.iocoder.yudao.module.drill.controller.open.enhance.vo.OpenImageVersionRespVO;
import cn.iocoder.yudao.module.drill.service.enhance.ImageAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;

@Tag(name = "开放接口 - 增强图片")
@RestController
@RequestMapping("/open-api/v1")
@Validated
public class OpenImageAccessController {

    @Resource
    private ImageAccessService imageAccessService;

    @GetMapping("/drill-images")
    @Operation(summary = "游标查询已发布的默认增强图片")
    @PreAuthorize("@ss.hasScope('drill:image:read')")
    public CommonResult<OpenImageCursorRespVO> getPublishedImages(@Valid OpenImageListReqVO reqVO) {
        return success(imageAccessService.getPublishedImages(currentTenantId(), reqVO));
    }

    @GetMapping("/drill-image-versions/{id}")
    @Operation(summary = "获取已发布增强图片版本详情")
    @PreAuthorize("@ss.hasScope('drill:image:read')")
    public CommonResult<OpenImageVersionRespVO> getPublishedVersion(
            @PathVariable("id") Long id,
            @RequestParam("purpose") String purpose) {
        return success(imageAccessService.getPublishedVersion(
                currentTenantId(), id, purpose, currentRootUrl()));
    }

    @GetMapping("/drill-image-versions/{id}/content")
    @Operation(summary = "下载已发布增强图片")
    @PreAuthorize("@ss.hasScope('drill:image:download')")
    public void getVersionContent(@PathVariable("id") Long id,
                                  @RequestParam("purpose") String purpose,
                                  HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        writeContent(imageAccessService.getOpenContent(currentTenantId(), id, purpose), request, response);
    }

    private Long currentTenantId() {
        LoginUser loginUser = getLoginUser();
        return loginUser == null ? null : loginUser.getTenantId();
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
