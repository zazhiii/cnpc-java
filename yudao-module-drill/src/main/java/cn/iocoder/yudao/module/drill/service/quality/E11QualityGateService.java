package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.E11QualityGateRespVO;
import org.springframework.web.multipart.MultipartFile;

public interface E11QualityGateService {

    E11QualityGateRespVO check(MultipartFile file, double confidence, int minDetections);

}
