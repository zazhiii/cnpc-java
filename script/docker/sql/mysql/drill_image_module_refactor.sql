-- 图片导入模块解耦迁移（MySQL 8.x，一次性执行）

ALTER TABLE `drill_image_batch`
  ADD COLUMN `finished_time` datetime DEFAULT NULL COMMENT '导入封存时间' AFTER `pending_metadata_count`;

ALTER TABLE `drill_image_asset`
  DROP COLUMN `enhance_status`;

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

-- 为历史增强任务补建通用引用，保证图片批次删除安全。
INSERT IGNORE INTO `drill_image_asset_reference`
(`asset_id`, `biz_type`, `biz_id`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT i.`asset_id`, 'ENHANCE_JOB', i.`job_id`, i.`tenant_id`,
       COALESCE(i.`creator`, ''), COALESCE(i.`create_time`, CURRENT_TIMESTAMP),
       COALESCE(i.`updater`, ''), COALESCE(i.`update_time`, CURRENT_TIMESTAMP), b'0'
FROM `drill_enhance_item` i
WHERE i.`deleted` = b'0';

-- 为不属于增强任务的原图发布版本补建引用。
INSERT IGNORE INTO `drill_image_asset_reference`
(`asset_id`, `biz_type`, `biz_id`, `tenant_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT v.`asset_id`, 'ENHANCE_VERSION', v.`id`, v.`tenant_id`,
       COALESCE(v.`creator`, ''), COALESCE(v.`create_time`, CURRENT_TIMESTAMP),
       COALESCE(v.`updater`, ''), COALESCE(v.`update_time`, CURRENT_TIMESTAMP), b'0'
FROM `drill_image_version` v
WHERE v.`deleted` = b'0';

-- 将旧的下游处理状态收敛为纯导入状态。
UPDATE `drill_image_batch`
SET `status` = CASE
      WHEN `status` = 'IMPORTING' THEN 'IMPORTING'
      WHEN `success_count` > 0 AND `failed_count` > 0 THEN 'PARTIAL_READY'
      WHEN `success_count` > 0 THEN 'READY'
      ELSE 'FAILED'
    END,
    `finished_time` = CASE WHEN `status` = 'IMPORTING' THEN NULL ELSE CURRENT_TIMESTAMP END,
    `update_time` = CURRENT_TIMESTAMP
WHERE `deleted` = b'0';
