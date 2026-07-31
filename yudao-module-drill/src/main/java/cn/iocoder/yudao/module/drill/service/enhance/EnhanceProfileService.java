package cn.iocoder.yudao.module.drill.service.enhance;

import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceProfileVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceProfileDO;

import java.util.List;

public interface EnhanceProfileService {
    List<EnhanceProfileDO> getList(String status, String photoType, String drillType);
    EnhanceProfileDO get(Long id);
    Long create(EnhanceProfileVO.SaveReq req);
    void update(EnhanceProfileVO.SaveReq req);
    Long copy(EnhanceProfileVO.CopyReq req);
    Long validateProfile(EnhanceProfileVO.ValidateReq req, String idempotencyKey);
    void publish(Long id);
    void enable(Long id);
    void disable(Long id);
    void delete(Long id);
}
