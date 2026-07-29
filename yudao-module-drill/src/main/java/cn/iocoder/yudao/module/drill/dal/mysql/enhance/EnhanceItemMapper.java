package cn.iocoder.yudao.module.drill.dal.mysql.enhance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceItemDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EnhanceItemMapper extends BaseMapperX<EnhanceItemDO> {
    default List<EnhanceItemDO> selectByJobId(Long jobId) {
        return selectList(new LambdaQueryWrapper<EnhanceItemDO>()
                .eq(EnhanceItemDO::getJobId, jobId).orderByAsc(EnhanceItemDO::getId));
    }

    default List<EnhanceItemDO> selectPendingCandidates(int limit) {
        return selectList(new LambdaQueryWrapper<EnhanceItemDO>()
                .eq(EnhanceItemDO::getStatus, "PENDING")
                .orderByAsc(EnhanceItemDO::getId).last("LIMIT " + limit));
    }

    default boolean claim(Long id, String workerId, LocalDateTime leaseUntil) {
        EnhanceItemDO update = new EnhanceItemDO();
        update.setStatus("PROCESSING");
        update.setWorkerId(workerId);
        update.setLeaseUntil(leaseUntil);
        update.setHeartbeatTime(LocalDateTime.now());
        update.setStartedAt(LocalDateTime.now());
        update.setProgress(1);
        return update(update, new LambdaUpdateWrapper<EnhanceItemDO>()
                .eq(EnhanceItemDO::getId, id)
                .eq(EnhanceItemDO::getStatus, "PENDING")) == 1;
    }

    default List<EnhanceItemDO> selectExpired(LocalDateTime now) {
        return selectList(new LambdaQueryWrapper<EnhanceItemDO>()
                .eq(EnhanceItemDO::getStatus, "PROCESSING")
                .lt(EnhanceItemDO::getLeaseUntil, now));
    }
}
