-- 钻头图像增强模块：存量图片导入
-- 适用数据库：MySQL 8.x

CREATE TABLE IF NOT EXISTS `drill_image_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '批次编号',
  `batch_no` varchar(40) NOT NULL COMMENT '批次业务编号',
  `name` varchar(100) NOT NULL COMMENT '批次名称',
  `source_type` varchar(32) NOT NULL COMMENT '来源类型：LOCAL_UPLOAD、PRESIGNED_UPLOAD、MINIO_OBJECT 等',
  `project_id` bigint DEFAULT NULL COMMENT '项目编号',
  `scene_type` varchar(32) DEFAULT NULL COMMENT '场景：IN_HOLE、OUT_HOLE、HISTORY、OTHER',
  `status` varchar(32) NOT NULL COMMENT '导入状态：IMPORTING、READY、PARTIAL_READY、FAILED、ARCHIVED',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '文件总数',
  `success_count` int NOT NULL DEFAULT 0 COMMENT '成功导入数',
  `failed_count` int NOT NULL DEFAULT 0 COMMENT '导入失败数',
  `duplicate_count` int NOT NULL DEFAULT 0 COMMENT '重复文件数',
  `pending_metadata_count` int NOT NULL DEFAULT 0 COMMENT '待补录元数据数',
  `finished_time` datetime DEFAULT NULL COMMENT '导入封存时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_batch_no` (`tenant_id`, `batch_no`, `deleted`),
  KEY `idx_batch_status` (`tenant_id`, `status`, `create_time`),
  KEY `idx_batch_project` (`tenant_id`, `project_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钻头图片导入批次';

CREATE TABLE IF NOT EXISTS `drill_image_asset` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '图片资产编号',
  `asset_no` varchar(48) NOT NULL COMMENT '图片资产业务编号',
  `batch_id` bigint NOT NULL COMMENT '导入批次编号',
  `project_id` bigint DEFAULT NULL COMMENT '项目编号',
  `drill_code` varchar(80) DEFAULT NULL COMMENT '钻头编号',
  `drill_type` varchar(64) DEFAULT NULL COMMENT '钻头类型',
  `scene_type` varchar(32) DEFAULT NULL COMMENT '场景类型',
  `photo_type` varchar(32) DEFAULT NULL COMMENT '照片类型',
  `blade_no` varchar(32) DEFAULT NULL COMMENT '刀翼编号',
  `source_type` varchar(32) NOT NULL COMMENT '来源类型',
  `source_biz_id` varchar(80) DEFAULT NULL COMMENT '来源业务编号',
  `original_file_id` bigint NOT NULL COMMENT 'infra_file 文件编号',
  `file_config_id` bigint NOT NULL COMMENT '文件配置编号',
  `object_key` varchar(512) NOT NULL COMMENT 'MinIO/S3 对象键',
  `original_url` varchar(1024) NOT NULL COMMENT '原图访问地址（不含临时签名）',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `mime_type` varchar(100) NOT NULL COMMENT 'MIME 类型',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `width` int NOT NULL COMMENT '图片宽度',
  `height` int NOT NULL COMMENT '图片高度',
  `sha256` char(64) NOT NULL COMMENT '原始文件 SHA-256',
  `capture_time` datetime DEFAULT NULL COMMENT '拍摄时间',
  `metadata_status` varchar(24) NOT NULL COMMENT '元数据状态：COMPLETE、PENDING',
  `archive_status` varchar(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '归档状态',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_asset_no` (`tenant_id`, `asset_no`, `deleted`),
  UNIQUE KEY `uk_tenant_sha256` (`tenant_id`, `sha256`, `deleted`),
  KEY `idx_asset_batch` (`tenant_id`, `batch_id`, `create_time`),
  KEY `idx_asset_drill` (`tenant_id`, `drill_code`, `photo_type`),
  KEY `idx_asset_metadata` (`tenant_id`, `metadata_status`, `create_time`),
  KEY `idx_asset_file` (`original_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钻头图片资产';

CREATE TABLE IF NOT EXISTS `drill_image_import_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录编号',
  `batch_id` bigint NOT NULL COMMENT '导入批次编号',
  `client_file_id` varchar(80) DEFAULT NULL COMMENT '客户端文件标识/幂等标识',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `sha256` char(64) DEFAULT NULL COMMENT '文件摘要',
  `result` varchar(24) NOT NULL COMMENT '结果：CREATED、DUPLICATE、FAILED',
  `asset_id` bigint DEFAULT NULL COMMENT '创建的图片资产编号',
  `duplicate_asset_id` bigint DEFAULT NULL COMMENT '重复图片资产编号',
  `error_code` varchar(64) DEFAULT NULL COMMENT '错误码',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_import_batch` (`tenant_id`, `batch_id`, `create_time`),
  KEY `idx_import_result` (`tenant_id`, `result`, `create_time`),
  KEY `idx_import_sha256` (`tenant_id`, `sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钻头图片导入明细记录';

CREATE TABLE IF NOT EXISTS `drill_image_asset_reference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '引用编号',
  `asset_id` bigint NOT NULL COMMENT '图片资产编号',
  `biz_type` varchar(64) NOT NULL COMMENT '业务类型',
  `biz_id` bigint NOT NULL COMMENT '业务编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_business` (`tenant_id`, `asset_id`, `biz_type`, `biz_id`, `deleted`),
  KEY `idx_reference_business` (`tenant_id`, `biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图片资产业务引用';

-- 修复历史数据：图片元数据已经补录，但批次待补录数量和状态尚未同步
UPDATE `drill_image_batch` b
LEFT JOIN (
  SELECT `batch_id`, COUNT(*) AS `pending_count`
  FROM `drill_image_asset`
  WHERE `metadata_status` = 'PENDING'
    AND `deleted` = b'0'
  GROUP BY `batch_id`
) asset_stats ON asset_stats.`batch_id` = b.`id`
SET b.`pending_metadata_count` = COALESCE(asset_stats.`pending_count`, 0),
    b.`status` = CASE
      WHEN b.`status` = 'IMPORTING' THEN 'IMPORTING'
      WHEN b.`success_count` > 0 AND b.`failed_count` > 0 THEN 'PARTIAL_READY'
      WHEN b.`success_count` > 0 THEN 'READY'
      ELSE 'FAILED'
    END,
    b.`finished_time` = CASE WHEN b.`status` = 'IMPORTING' THEN NULL ELSE COALESCE(b.`finished_time`, CURRENT_TIMESTAMP) END,
    b.`update_time` = CURRENT_TIMESTAMP
WHERE b.`deleted` = b'0';
