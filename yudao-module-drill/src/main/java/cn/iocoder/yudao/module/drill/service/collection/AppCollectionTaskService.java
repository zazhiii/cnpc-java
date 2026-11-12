package cn.iocoder.yudao.module.drill.service.collection;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionTaskVO;
import cn.iocoder.yudao.module.drill.controller.android.vo.AppCollectionVO;

public interface AppCollectionTaskService {
    AppCollectionTaskVO.Resp create(Long userId, AppCollectionTaskVO.CreateReq req);
    AppCollectionTaskVO.Resp updateDraft(Long userId, Long id, AppCollectionTaskVO.UpdateReq req);
    AppCollectionTaskVO.Resp get(Long userId, Long id);
    PageResult<AppCollectionTaskVO.Resp> getPage(Long userId, AppCollectionTaskVO.PageReq req);
    void deleteDraft(Long userId, Long id, Integer version);
    AppCollectionVO.PhotoResp uploadPhoto(Long userId, Long id, String captureCode,
                                          AppCollectionVO.UploadReq req);
    AppCollectionTaskVO.Resp submit(Long userId, Long id);
}
