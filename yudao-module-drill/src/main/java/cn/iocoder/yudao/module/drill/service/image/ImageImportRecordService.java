package cn.iocoder.yudao.module.drill.service.image;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.record.ImageImportRecordPageReqVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportRecordDO;

public interface ImageImportRecordService {

    PageResult<ImageImportRecordDO> getPage(ImageImportRecordPageReqVO reqVO);

}
