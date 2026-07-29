package cn.iocoder.yudao.module.drill.dal.mysql.enhance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.EnhanceStepLogDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EnhanceStepLogMapper extends BaseMapperX<EnhanceStepLogDO> {
    default List<EnhanceStepLogDO> selectByItemId(Long itemId) {
        return selectList(new LambdaQueryWrapper<EnhanceStepLogDO>()
                .eq(EnhanceStepLogDO::getItemId, itemId).orderByAsc(EnhanceStepLogDO::getStepOrder));
    }
}
