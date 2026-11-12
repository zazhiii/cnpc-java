package cn.iocoder.yudao.module.drill.service.collection;

import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionVO;
import cn.iocoder.yudao.module.drill.config.E11QualityGateProperties;
import cn.iocoder.yudao.module.drill.service.image.ImageFileValidator;
import cn.iocoder.yudao.module.drill.service.quality.checker.E11ModelQualityChecker;
import cn.iocoder.yudao.module.drill.service.quality.model.ImageQualityModelResult;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;

/** App 拍摄后质检：只调用 E11 模型，不创建图片资产或任务照片。 */
@Service
public class AppCollectionPhotoQualityService {

    private static final long APP_MAX_FILE_SIZE = 30L * 1024 * 1024;

    @Resource private ImageFileValidator imageFileValidator;
    @Resource private E11ModelQualityChecker modelChecker;
    @Resource private E11QualityGateProperties properties;

    public AppCollectionVO.PhotoQualityResp check(MultipartFile file) {
        // 图像不能为空
        if (file == null || file.isEmpty()) throw exception(IMAGE_FILE_EMPTY);
//        if (file.getSize() > APP_MAX_FILE_SIZE) throw exception(IMAGE_FILE_TOO_LARGE, 30);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw exception(IMAGE_DECODE_FAILED);
        }
        ImageFileValidator.ImageInfo imageInfo = imageFileValidator.validate(
                content, file.getOriginalFilename(), APP_MAX_FILE_SIZE,
                ImageFileValidator.MAX_PIXEL_COUNT);

        ImageQualityModelResult result = modelChecker.executeModel(
                new QualityImageInput(null, content, file.getOriginalFilename(),
                        imageInfo.getMimeType(), imageInfo.getWidth(), imageInfo.getHeight()),
                Map.of("confidence", properties.getAppConfidence(),
                        "minDetections", properties.getAppMinDetections()));
        AppCollectionVO.PhotoQualityResp response = new AppCollectionVO.PhotoQualityResp();
        response.setPassed(result.getPassed());
        response.setReason(result.getReason());
        response.setModelVersion(result.getModelVersion());
        response.setThreshold(result.getThreshold());
        response.setAcceptedCount(result.getAcceptedCount());
        response.setCandidateCount(result.getCandidateCount());
        response.setDurationMs(result.getDurationMs());
        return response;
    }
}
