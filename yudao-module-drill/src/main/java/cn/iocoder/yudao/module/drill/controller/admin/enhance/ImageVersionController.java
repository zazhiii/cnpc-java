package cn.iocoder.yudao.module.drill.controller.admin.enhance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.ImageVersionVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageVersionDO;
import cn.iocoder.yudao.module.drill.service.enhance.ImageVersionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/drill/image-version")
public class ImageVersionController {
    @Resource private ImageVersionService service;

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('drill:image-asset:query')")
    public CommonResult<ImageVersionDO> get(@RequestParam Long id) { return success(service.get(id)); }

    @PostMapping("/{id}/review")
    @PreAuthorize("@ss.hasPermission('drill:image-version:review')")
    public CommonResult<Boolean> review(@PathVariable Long id, @Valid @RequestBody ImageVersionVO.ReviewReq req) {
        service.review(id, req); return success(true);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('drill:image-version:publish')")
    public CommonResult<Boolean> publish(@PathVariable Long id, @Valid @RequestBody ImageVersionVO.PublishReq req) {
        service.publish(id, req); return success(true);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@ss.hasPermission('drill:image-version:publish')")
    public CommonResult<Boolean> withdraw(@PathVariable Long id, @Valid @RequestBody ImageVersionVO.WithdrawReq req) {
        service.withdraw(id, req); return success(true);
    }

    @GetMapping("/{id}/access-url")
    @PreAuthorize("@ss.hasPermission('drill:image-asset:query')")
    public CommonResult<Map<String, Object>> accessUrl(@PathVariable Long id) {
        ImageVersionDO version = service.get(id);
        return success(Map.of("url", service.getAccessUrl(id), "sha256", version.getSha256(),
                "mimeType", version.getMimeType(), "expiresInSeconds", 3600));
    }
}
