package cn.iocoder.yudao.module.drill.service.image;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.record.ImageImportRecordPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportRecordDO;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageImportRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class ImageImportRecordServiceImpl implements ImageImportRecordService {

    @Resource
    private ImageImportRecordMapper recordMapper;

    @Override
    public PageResult<ImageImportRecordDO> getPage(ImageImportRecordPageReqVO reqVO) {
        return recordMapper.selectPage(reqVO);
    }

}
