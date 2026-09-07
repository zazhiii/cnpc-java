package cn.iocoder.yudao.module.drill.controller.admin.quality;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.QualityProfileVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileDO;
import cn.iocoder.yudao.module.drill.service.quality.QualityProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "图像质检 - 质检方案")
@RestController
@RequestMapping("/drill/quality-profile")
@Validated
public class QualityProfileController {

    @Resource
    private QualityProfileService service;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('drill:quality-profile:query')")
    public CommonResult<List<QualityProfileVO.Resp>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String modelCode) {
        return success(service.getList(status, modelCode).stream().map(this::convert).toList());
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('drill:quality-profile:query')")
    public CommonResult<QualityProfileVO.Resp> get(@RequestParam Long id) {
        return success(convert(service.get(id)));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('drill:quality-profile:manage')")
    @Operation(summary = "创建质检方案")
    public CommonResult<Long> create(@Valid @RequestBody QualityProfileVO.SaveReq req) {
        return success(service.create(req));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('drill:quality-profile:manage')")
    @Operation(summary = "更新质检方案")
    public CommonResult<Boolean> update(@Valid @RequestBody QualityProfileVO.SaveReq req) {
        service.update(req);
        return success(true);
    }

    @PostMapping("/copy")
    @PreAuthorize("@ss.hasPermission('drill:quality-profile:manage')")
    @Operation(summary = "复制质检方案")
    public CommonResult<Long> copy(@Valid @RequestBody QualityProfileVO.CopyReq req) {
        return success(service.copy(req));
    }

    @PutMapping("/enable")
    @PreAuthorize("@ss.hasPermission('drill:quality-profile:manage')")
    @Operation(summary = "启用质检方案")
    public CommonResult<Boolean> enable(@RequestParam Long id) {
        service.enable(id);
        return success(true);
    }

    @PutMapping("/disable")
    @PreAuthorize("@ss.hasPermission('drill:quality-profile:manage')")
    @Operation(summary = "禁用质检方案")
    public CommonResult<Boolean> disable(@RequestParam Long id) {
        service.disable(id);
        return success(true);
    }

    @PutMapping("/set-default")
    @PreAuthorize("@ss.hasPermission('drill:quality-profile:manage')")
    @Operation(summary = "设置默认质检方案")
    public CommonResult<Boolean> setDefault(@RequestParam Long id) {
        service.setDefault(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('drill:quality-profile:manage')")
    @Operation(summary = "删除质检方案")
    public CommonResult<Boolean> delete(@RequestParam Long id) {
        service.delete(id);
        return success(true);
    }

    private QualityProfileVO.Resp convert(QualityProfileDO profile) {
        QualityProfileVO.Resp resp = new QualityProfileVO.Resp();
        resp.setId(profile.getId());
        resp.setProfileCode(profile.getProfileCode());
        resp.setName(profile.getName());
        resp.setVersion(profile.getVersion());
        resp.setChecks(service.getChecks(profile.getId()));
        resp.setShortCircuit(profile.getShortCircuit() == null || profile.getShortCircuit());
        resp.setStatus(profile.getStatus());
        resp.setDefaultProfile(profile.getDefaultProfile());
        resp.setEnabledAt(profile.getEnabledAt());
        resp.setRemark(profile.getRemark());
        resp.setUpdateTime(profile.getUpdateTime());
        return resp;
    }

}
