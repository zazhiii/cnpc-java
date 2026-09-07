package cn.iocoder.yudao.module.drill.controller.admin.quality;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityCheckReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityCheckRespVO;
import cn.iocoder.yudao.module.drill.service.quality.ImageQualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "图片资产质量检测")
@RestController
@RequestMapping("/drill/image-quality")
@PreAuthorize("isAuthenticated()")
@Validated
public class E11QualityGateController {

    @Resource
    private ImageQualityService imageQualityService;

//    @PostMapping(value = "/check", consumes = MediaType.APPLICATION_JSON_VALUE)
//    @Operation(summary = "按图片资产执行质量检测")
//    public CommonResult<ImageQualityCheckRespVO> check(
//            @Valid @RequestBody ImageQualityCheckReqVO reqVO) {
//        return success(imageQualityService.check(reqVO));
//    }

}
