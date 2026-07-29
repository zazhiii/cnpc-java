package cn.iocoder.yudao.module.drill.service.enhance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceJobPageReqVO;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.EnhanceJobVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceItemDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceJobDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceProfileDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceStepLogDO;

import java.util.List;

public interface EnhanceJobService {
    Long create(EnhanceJobVO.CreateReq req, String idempotencyKey);
    Long createValidationJob(EnhanceProfileDO profile, List<Long> assetIds, String idempotencyKey);
    PageResult<EnhanceJobDO> getPage(EnhanceJobPageReqVO req);
    EnhanceJobDO get(Long id);
    List<EnhanceItemDO> getItems(Long jobId);
    List<EnhanceStepLogDO> getSteps(Long itemId);
    EnhanceJobVO.StatisticsResp getStatistics();
    void retryItem(Long id, EnhanceJobVO.RetryReq req);
    void retryFailed(Long jobId);
    void cancel(Long jobId);
    void refreshJob(Long jobId);
}
