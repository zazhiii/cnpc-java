package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityCheckReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.ImageQualityCheckRespVO;

public interface ImageQualityService {
    ImageQualityCheckRespVO check(ImageQualityCheckReqVO reqVO);
}
