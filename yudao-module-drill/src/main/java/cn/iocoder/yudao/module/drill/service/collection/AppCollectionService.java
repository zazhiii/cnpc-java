package cn.iocoder.yudao.module.drill.service.collection;

import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.AppCollectionVO;

import java.util.List;

public interface AppCollectionService {
    AppCollectionVO.TaskResp sync(Long userId, AppCollectionVO.SyncReq req);
    List<AppCollectionVO.TaskResp> list(Long userId);
    AppCollectionVO.TaskResp get(Long userId, String clientId);
    AppCollectionVO.PhotoResp upload(Long userId, String clientId, String captureId,
                                     AppCollectionVO.UploadReq req);
    AppCollectionVO.TaskResp submit(Long userId, String clientId);
}
