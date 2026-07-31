package cn.iocoder.yudao.module.drill.controller.admin.enhance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceProfileVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceProfileDO;
import cn.iocoder.yudao.module.drill.service.enhance.EnhanceProfileService;
import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.OutputSpec;
import cn.iocoder.yudao.module.drill.service.enhance.model.EnhanceModels.StepConfig;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 图像增强方案")
@RestController
@RequestMapping("/drill/enhance-profile")
@Validated
public class EnhanceProfileController {
    @Resource private EnhanceProfileService service;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('drill:enhance-profile:query')")
    public CommonResult<List<EnhanceProfileVO.Resp>> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) String photoType, @RequestParam(required = false) String drillType) {
        return success(service.getList(status, photoType, drillType).stream().map(this::convert).toList());
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('drill:enhance-profile:query')")
    public CommonResult<EnhanceProfileVO.Resp> get(@RequestParam Long id) {
        return success(convert(service.get(id)));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('drill:enhance-profile:manage')")
    public CommonResult<Long> create(@Valid @RequestBody EnhanceProfileVO.SaveReq req) {
        return success(service.create(req));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('drill:enhance-profile:manage')")
    public CommonResult<Boolean> update(@Valid @RequestBody EnhanceProfileVO.SaveReq req) {
        service.update(req); return success(true);
    }

    @PostMapping("/copy")
    @PreAuthorize("@ss.hasPermission('drill:enhance-profile:manage')")
    public CommonResult<Long> copy(@Valid @RequestBody EnhanceProfileVO.CopyReq req) {
        return success(service.copy(req));
    }

    @PostMapping("/validate")
    @PreAuthorize("@ss.hasPermission('drill:enhance-profile:manage')")
    public CommonResult<Long> validate(@Valid @RequestBody EnhanceProfileVO.ValidateReq req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return success(service.validateProfile(req, idempotencyKey));
    }

    @PutMapping("/publish")
    @PreAuthorize("@ss.hasPermission('drill:enhance-profile:manage')")
    public CommonResult<Boolean> publish(@RequestParam Long id) {
        service.publish(id); return success(true);
    }

    @PutMapping("/enable")
    @PreAuthorize("@ss.hasPermission('drill:enhance-profile:manage')")
    public CommonResult<Boolean> enable(@RequestParam Long id) {
        service.enable(id); return success(true);
    }

    @PutMapping("/disable")
    @PreAuthorize("@ss.hasPermission('drill:enhance-profile:manage')")
    public CommonResult<Boolean> disable(@RequestParam Long id) {
        service.disable(id); return success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('drill:enhance-profile:manage')")
    public CommonResult<Boolean> delete(@RequestParam Long id) {
        service.delete(id); return success(true);
    }

    private EnhanceProfileVO.Resp convert(EnhanceProfileDO profile) {
        EnhanceProfileVO.Resp resp = new EnhanceProfileVO.Resp();
        resp.setId(profile.getId()); resp.setProfileCode(profile.getProfileCode()); resp.setName(profile.getName());
        resp.setVersion(profile.getVersion());
        resp.setApplicableDrillTypes(JsonUtils.parseArray(profile.getApplicableDrillTypes(), String.class));
        resp.setApplicablePhotoTypes(JsonUtils.parseArray(profile.getApplicablePhotoTypes(), String.class));
        resp.setPipeline(JsonUtils.parseArray(profile.getPipelineJson(), StepConfig.class));
        resp.setOutputSpec(JsonUtils.parseObject(profile.getOutputSpecJson(), OutputSpec.class));
        resp.setReviewPolicy(profile.getReviewPolicy()); resp.setStatus(profile.getStatus());
        resp.setValidationJobId(profile.getValidationJobId()); resp.setPublishedAt(profile.getPublishedAt());
        resp.setRemark(profile.getRemark()); resp.setUpdateTime(profile.getUpdateTime());
        return resp;
    }
}
