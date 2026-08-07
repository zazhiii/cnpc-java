package cn.iocoder.yudao.module.drill.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 钻头业务错误码。
 *
 * drill 模块使用 1-050-000-000 段。
 */
public interface ErrorCodeConstants {

    ErrorCode IMAGE_BATCH_NOT_EXISTS = new ErrorCode(1_050_000_001, "图片导入批次不存在");
    ErrorCode IMAGE_BATCH_STATUS_INVALID = new ErrorCode(1_050_000_002, "当前批次状态不允许执行该操作");
    ErrorCode IMAGE_FILE_EMPTY = new ErrorCode(1_050_000_003, "导入图片不能为空");
    ErrorCode IMAGE_FILE_TOO_LARGE = new ErrorCode(1_050_000_004, "图片大小不能超过 {} MB");
    ErrorCode IMAGE_FORMAT_UNSUPPORTED = new ErrorCode(1_050_000_005, "不支持的图片格式：{}");
    ErrorCode IMAGE_DECODE_FAILED = new ErrorCode(1_050_000_006, "图片无法解码或已经损坏");
    ErrorCode IMAGE_DIMENSION_EXCEEDED = new ErrorCode(1_050_000_007, "图片像素总数不能超过 {}");
    ErrorCode IMAGE_ASSET_NOT_EXISTS = new ErrorCode(1_050_000_008, "图片资产不存在");
    ErrorCode IMAGE_FILE_RECORD_MISSING = new ErrorCode(1_050_000_009, "图片已上传，但文件记录创建失败");
    ErrorCode IMAGE_HASH_MISMATCH = new ErrorCode(1_050_000_010, "图片摘要校验失败");
    ErrorCode IMAGE_SIZE_MISMATCH = new ErrorCode(1_050_000_011, "图片大小校验失败");
    ErrorCode IMAGE_OBJECT_NOT_FOUND = new ErrorCode(1_050_000_012, "对象存储中的图片不存在");
    ErrorCode IMAGE_METADATA_INCOMPLETE = new ErrorCode(1_050_000_013, "图片关键业务信息不完整");
    ErrorCode IMAGE_BATCH_HAS_ENHANCE_JOBS = new ErrorCode(1_050_000_014,
            "该批次已有增强任务或发布版本，不能删除");
    ErrorCode IMAGE_BATCH_EMPTY = new ErrorCode(1_050_000_015, "空批次不能完成导入");
    ErrorCode IMAGE_BATCH_NOT_READY = new ErrorCode(1_050_000_016, "图片批次尚未完成导入");
    ErrorCode IMAGE_BATCH_REFERENCED = new ErrorCode(1_050_000_017, "图片批次已被业务任务引用，不能删除");
    ErrorCode ENHANCE_PROFILE_NOT_EXISTS = new ErrorCode(1_050_000_020, "增强方案不存在");
    ErrorCode ENHANCE_PROFILE_DUPLICATE = new ErrorCode(1_050_000_021, "增强方案编码和版本已存在");
    ErrorCode ENHANCE_PROFILE_STATE_INVALID = new ErrorCode(1_050_000_022, "当前增强方案状态不允许该操作");
    ErrorCode ENHANCE_PROFILE_INVALID = new ErrorCode(1_050_000_023, "增强方案配置不合法：{}");
    ErrorCode ENHANCE_ALGORITHM_UNAVAILABLE = new ErrorCode(1_050_000_024, "增强方案引用的算法当前不可用：{}");
    ErrorCode ENHANCE_PROFILE_IN_USE = new ErrorCode(1_050_000_025, "增强方案已被任务引用，不能删除");
    ErrorCode ENHANCE_JOB_NOT_EXISTS = new ErrorCode(1_050_000_030, "增强任务不存在");
    ErrorCode ENHANCE_ITEM_NOT_EXISTS = new ErrorCode(1_050_000_031, "增强处理项不存在");
    ErrorCode ENHANCE_JOB_STATE_INVALID = new ErrorCode(1_050_000_032, "当前增强任务状态不允许该操作");
    ErrorCode ENHANCE_IDEMPOTENCY_CONFLICT = new ErrorCode(1_050_000_033, "幂等键已被不同请求使用");
    ErrorCode IMAGE_VERSION_NOT_EXISTS = new ErrorCode(1_050_000_040, "图片增强版本不存在");
    ErrorCode IMAGE_VERSION_STATE_INVALID = new ErrorCode(1_050_000_041, "当前图片版本状态不允许该操作");
    ErrorCode IMAGE_VERSION_STORAGE_ERROR = new ErrorCode(1_050_000_042, "增强图片存储失败");
    ErrorCode INVALID_MANUAL_OVERRIDE = new ErrorCode(1_050_000_043, "人工修正参数不合法：{}");
    ErrorCode IMAGE_ACCESS_INVALID_PURPOSE = new ErrorCode(1_050_000_044, "不支持的增强图片用途：{}");
    ErrorCode IMAGE_ACCESS_CURSOR_INVALID = new ErrorCode(1_050_000_045, "增强图片查询游标不合法");
    ErrorCode IMAGE_ACCESS_NOT_PUBLISHED = new ErrorCode(1_050_000_046, "图片版本未针对指定用途发布或已撤回");
    ErrorCode IMAGE_ACCESS_CONTENT_MISSING = new ErrorCode(1_050_000_047, "增强图片文件不存在");

    ErrorCode APP_COLLECTION_TASK_NOT_EXISTS = new ErrorCode(1_050_000_060, "采集任务不存在");
    ErrorCode APP_COLLECTION_PHOTO_EMPTY = new ErrorCode(1_050_000_061, "采集照片不能为空或无法读取");
    ErrorCode APP_COLLECTION_INCOMPLETE = new ErrorCode(1_050_000_062,
            "必拍照片未完成，当前 {} 张，至少需要 {} 张");

    ErrorCode E11_QUALITY_GATE_UNAVAILABLE = new ErrorCode(1_050_000_070,
            "E11 图片质量检测服务不可用，请检查模型配置");
    ErrorCode E11_QUALITY_GATE_TIMEOUT = new ErrorCode(1_050_000_071, "E11 图片质量检测超时（{} ms）");
    ErrorCode E11_QUALITY_GATE_MODEL_ERROR = new ErrorCode(1_050_000_072, "E11 模型推理失败，请检查服务日志");
    ErrorCode E11_QUALITY_GATE_ARGUMENT_ERROR = new ErrorCode(1_050_000_073, "E11 模型调用参数错误");
    ErrorCode E11_QUALITY_GATE_PROCESS_ERROR = new ErrorCode(1_050_000_074, "E11 模型进程异常退出：{}");
    ErrorCode E11_QUALITY_GATE_INVALID_RESPONSE = new ErrorCode(1_050_000_075, "E11 模型返回了无效结果");
    ErrorCode E11_QUALITY_GATE_BUSY = new ErrorCode(1_050_000_076, "E11 图片质量检测繁忙，请稍后重试");
    ErrorCode E11_QUALITY_GATE_INTERRUPTED = new ErrorCode(1_050_000_077, "E11 图片质量检测被中断");

    ErrorCode QUALITY_PROFILE_NOT_EXISTS = new ErrorCode(1_050_000_080, "图片质检方案不存在");
    ErrorCode QUALITY_PROFILE_DUPLICATE = new ErrorCode(1_050_000_081, "图片质检方案编码和版本已存在");
    ErrorCode QUALITY_PROFILE_STATE_INVALID = new ErrorCode(1_050_000_082, "当前图片质检方案状态不允许该操作");
    ErrorCode QUALITY_PROFILE_INVALID = new ErrorCode(1_050_000_083, "图片质检方案配置不合法：{}");
    ErrorCode QUALITY_MODEL_UNSUPPORTED = new ErrorCode(1_050_000_084, "不支持的图片质检模型：{}");
    ErrorCode QUALITY_DEFAULT_PROFILE_NOT_EXISTS = new ErrorCode(1_050_000_085, "当前租户未配置默认图片质检方案");
    ErrorCode QUALITY_DEFAULT_PROFILE_PROTECTED = new ErrorCode(1_050_000_086, "默认图片质检方案不能停用或删除");
    ErrorCode QUALITY_PROFILE_IN_USE = new ErrorCode(1_050_000_087, "图片质检方案已有质检记录，不能删除");
    ErrorCode QUALITY_CHECK_REQUEST_INVALID = new ErrorCode(1_050_000_088, "图片质检请求不合法：{}");
    ErrorCode QUALITY_RECORD_NOT_EXISTS = new ErrorCode(1_050_000_089, "图片质检记录不存在");
    ErrorCode QUALITY_MODEL_EXECUTION_FAILED = new ErrorCode(1_050_000_090, "图片质检模型执行失败");

}
