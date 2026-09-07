package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.module.drill.controller.admin.quality.vo.QualityProfileVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.quality.QualityProfileDO;

import java.util.List;

public interface QualityProfileService {
    List<QualityProfileDO> getList(String status, String modelCode);
    QualityProfileDO get(Long id);
    QualityProfileDO getEnabled(Long id);
    QualityProfileDO getDefaultEnabled();
    List<QualityProfileVO.CheckItem> getChecks(Long profileId);

    /**
     * 创建质检方案
     * @param req
     * @return
     */
    Long create(QualityProfileVO.SaveReq req);
    void update(QualityProfileVO.SaveReq req);
    Long copy(QualityProfileVO.CopyReq req);
    void enable(Long id);
    void disable(Long id);
    void setDefault(Long id);
    void delete(Long id);
}
