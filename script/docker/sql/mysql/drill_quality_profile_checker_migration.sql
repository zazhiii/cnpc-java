-- 旧版单模型质检方案升级为多 Checker 方案（仅执行一次）
-- 适用数据库：MySQL 8.x

ALTER TABLE `drill_quality_profile`
  MODIFY COLUMN `model_code` varchar(40) DEFAULT NULL COMMENT '兼容旧版单模型方案',
  MODIFY COLUMN `model_config_json` json DEFAULT NULL COMMENT '兼容旧版单模型方案',
  ADD COLUMN `short_circuit` bit(1) NOT NULL DEFAULT b'1'
    COMMENT '任一条件不通过时是否终止后续质检' AFTER `model_config_json`;

CREATE TABLE `drill_quality_profile_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `profile_id` bigint NOT NULL,
  `check_code` varchar(64) NOT NULL,
  `config_json` json NOT NULL,
  `sort_no` int NOT NULL DEFAULT 0,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `active_check_code` varchar(64) GENERATED ALWAYS AS
    (CASE WHEN `deleted` = b'0' THEN `check_code` ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quality_profile_item` (`tenant_id`,`profile_id`,`active_check_code`),
  KEY `idx_quality_profile_item_sort` (`tenant_id`,`profile_id`,`sort_no`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片质检方案检查项';

INSERT INTO `drill_quality_profile_item`
(`profile_id`,`check_code`,`config_json`,`sort_no`,`tenant_id`)
SELECT profile.`id`,'E11_MODEL',profile.`model_config_json`,10,profile.`tenant_id`
FROM `drill_quality_profile` profile
WHERE profile.`model_code` = 'E11' AND profile.`deleted` = b'0';
