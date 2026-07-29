package cn.iocoder.yudao.module.drill.service.enhance;

import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.ImageVersionVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageVersionDO;

public interface ImageVersionService {
    ImageVersionDO get(Long id);
    void review(Long id, ImageVersionVO.ReviewReq req);
    void publish(Long id, ImageVersionVO.PublishReq req);
    void withdraw(Long id, ImageVersionVO.WithdrawReq req);
    String getAccessUrl(Long id);
}
