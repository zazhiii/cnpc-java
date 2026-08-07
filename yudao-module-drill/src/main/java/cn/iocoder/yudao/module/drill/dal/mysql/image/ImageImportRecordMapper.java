package cn.iocoder.yudao.module.drill.dal.mysql.image;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.record.ImageImportRecordPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportRecordDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImageImportRecordMapper extends BaseMapperX<ImageImportRecordDO> {

    default PageResult<ImageImportRecordDO> selectPage(ImageImportRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ImageImportRecordDO>()
                .eq(ImageImportRecordDO::getBatchId, reqVO.getBatchId())
                .eqIfPresent(ImageImportRecordDO::getResult, reqVO.getResult())
                .and(reqVO.getKeyword() != null, wrapper -> wrapper
                        .like(ImageImportRecordDO::getOriginalName, reqVO.getKeyword())
                        .or()
                        .like(ImageImportRecordDO::getClientFileId, reqVO.getKeyword()))
                .orderByDesc(ImageImportRecordDO::getId));
    }

    default void deleteByBatchId(Long batchId) {
        delete(new LambdaQueryWrapper<ImageImportRecordDO>()
                .eq(ImageImportRecordDO::getBatchId, batchId));
    }

    default ImageImportRecordDO selectByPackageAndEntry(Long packageId, String entryPath) {
        return selectOne(new LambdaQueryWrapper<ImageImportRecordDO>()
                .eq(ImageImportRecordDO::getPackageId, packageId)
                .eq(ImageImportRecordDO::getSourceEntryPath, entryPath));
    }

    default List<ImageImportRecordDO> selectByPackageId(Long packageId) {
        return selectList(new LambdaQueryWrapper<ImageImportRecordDO>()
                .eq(ImageImportRecordDO::getPackageId, packageId));
    }

    default List<ImageImportRecordDO> selectByJobId(Long jobId) {
        return selectList(new LambdaQueryWrapper<ImageImportRecordDO>()
                .eq(ImageImportRecordDO::getJobId, jobId));
    }

}
