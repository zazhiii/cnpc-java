package cn.iocoder.yudao.module.drill.service.enhance;

import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.ImageAccessOverviewRespVO;
import cn.iocoder.yudao.module.drill.controller.open.enhance.vo.OpenImageCursorRespVO;
import cn.iocoder.yudao.module.drill.controller.open.enhance.vo.OpenImageListReqVO;
import cn.iocoder.yudao.module.drill.controller.open.enhance.vo.OpenImageVersionRespVO;

public interface ImageAccessService {

    ImageAccessOverviewRespVO getOverview(String requestRootUrl);

    OpenImageCursorRespVO getPublishedImages(Long tenantId, OpenImageListReqVO reqVO);

    OpenImageVersionRespVO getPublishedVersion(Long tenantId, Long versionId, String purpose,
                                               String requestRootUrl);

    ImageContent getOpenContent(Long tenantId, Long versionId, String purpose);

    ImageContent getAdminContent(Long versionId);

    String resolvePublicApiBaseUrl(String requestRootUrl);

    record ImageContent(byte[] bytes, String mimeType, Long fileSize, String sha256, String filename) {
    }

}
