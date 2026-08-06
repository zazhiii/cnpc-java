package cn.iocoder.yudao.module.drill.controller.admin.image;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.record.ImageImportRecordPageReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.record.ImageImportRecordRespVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportRecordDO;
import cn.iocoder.yudao.module.drill.service.image.ImageImportRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 图片导入明细")
@RestController
@RequestMapping("/drill/image-import-record")
@Validated
public class ImageImportRecordController {

    @Resource
    private ImageImportRecordService imageImportRecordService;

    @GetMapping("/page")
    @Operation(summary = "获得图片导入明细分页")
    @PreAuthorize("@ss.hasPermission('drill:image-batch:query')")
    public CommonResult<PageResult<ImageImportRecordRespVO>> getPage(
            @Valid ImageImportRecordPageReqVO reqVO) {
        PageResult<ImageImportRecordDO> page = imageImportRecordService.getPage(reqVO);
        return success(BeanUtils.toBean(page, ImageImportRecordRespVO.class));
    }

}
