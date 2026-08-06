package cn.iocoder.yudao.module.drill.controller.admin.quality;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.E11QualityGateRespVO;
import cn.iocoder.yudao.module.drill.service.quality.E11QualityGateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "Android App - E11 图片质量检测")
@RestController
@RequestMapping("/drill/image-quality")
@PreAuthorize("isAuthenticated()")
@Validated
public class E11QualityGateController {

    @Resource
    private E11QualityGateService qualityGateService;

    @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "检测图片是否满足 E11 切削齿质量门禁",
            description = "passed=false 是正常业务结果；模型不可用、超时或返回非法数据时才返回业务错误。")
    public CommonResult<E11QualityGateRespVO> check(
            @Parameter(description = "待检测图片，支持 JPEG、PNG、WebP，最大 30 MB", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "检测置信度阈值")
            @RequestParam(value = "confidence", defaultValue = "0.75")
            @DecimalMin(value = "0.0", message = "置信度必须大于等于 0")
            @DecimalMax(value = "1.0", message = "置信度必须小于等于 1") Double confidence,
            @Parameter(description = "判定合格所需的最少切削齿数量")
            @RequestParam(value = "minDetections", defaultValue = "1")
            @Min(value = 1, message = "最少检测数量必须大于等于 1")
            @Max(value = 300, message = "最少检测数量不能超过 300") Integer minDetections) {
        return success(qualityGateService.check(file, confidence, minDetections));
    }

}
