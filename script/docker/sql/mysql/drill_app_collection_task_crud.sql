-- Android 现场采集任务 CRUD、拍摄清单与增量同步（MySQL 8.x）

-- Windows MySQL 客户端可能默认使用 GBK，显式声明脚本内容为 UTF-8，避免中文入库乱码。
SET NAMES utf8mb4;

ALTER TABLE `drill_collection_task`
  ADD COLUMN `drill_code` varchar(80) DEFAULT NULL AFTER `status`,
  ADD COLUMN `drill_type` varchar(64) DEFAULT NULL AFTER `drill_code`,
  ADD COLUMN `drill_model` varchar(64) DEFAULT NULL AFTER `drill_type`,
  ADD COLUMN `drill_size` varchar(32) DEFAULT NULL AFTER `drill_model`,
  ADD COLUMN `manufacturer` varchar(100) DEFAULT NULL AFTER `drill_size`,
  ADD COLUMN `production_batch` varchar(64) DEFAULT NULL AFTER `manufacturer`,
  ADD COLUMN `well_number` varchar(80) DEFAULT NULL AFTER `production_batch`,
  ADD COLUMN `well_section` varchar(100) DEFAULT NULL AFTER `well_number`,
  ADD COLUMN `formation` varchar(80) DEFAULT NULL AFTER `well_section`,
  ADD COLUMN `operation_time` date DEFAULT NULL AFTER `formation`,
  ADD COLUMN `footage` decimal(12,2) DEFAULT NULL AFTER `operation_time`,
  ADD COLUMN `drilling_hours` decimal(12,2) DEFAULT NULL AFTER `footage`,
  ADD COLUMN `location` varchar(200) DEFAULT NULL AFTER `drilling_hours`,
  ADD COLUMN `notes` varchar(1000) DEFAULT NULL AFTER `location`,
  ADD COLUMN `version` int NOT NULL DEFAULT 1 AFTER `notes`,
  MODIFY COLUMN `task_json` longtext DEFAULT NULL,
  ADD UNIQUE KEY `uk_collection_task_no` (`tenant_id`,`task_no`,`deleted`),
  ADD KEY `idx_collection_drill` (`tenant_id`,`drill_code`,`update_time`),
  ADD KEY `idx_collection_well` (`tenant_id`,`well_number`,`update_time`);

CREATE TABLE IF NOT EXISTS `drill_collection_capture` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `capture_code` varchar(64) NOT NULL,
  `title` varchar(100) NOT NULL,
  `description` varchar(500) NOT NULL,
  `required_flag` bit(1) NOT NULL DEFAULT b'1',
  `blade_no` int DEFAULT NULL,
  `sort_no` int NOT NULL,
  `state` varchar(32) NOT NULL DEFAULT 'PENDING',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_capture` (`tenant_id`,`task_id`,`capture_code`,`deleted`),
  KEY `idx_collection_capture_sort` (`tenant_id`,`task_id`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务拍摄清单快照';

CREATE TABLE IF NOT EXISTS `drill_collection_task_change` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '同步修订号',
  `task_id` bigint NOT NULL,
  `collector_user_id` bigint NOT NULL,
  `client_id` varchar(64) NOT NULL,
  `operation` varchar(16) NOT NULL COMMENT 'UPSERT/DELETE',
  `task_version` int NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_collection_change_sync` (`tenant_id`,`collector_user_id`,`id`),
  KEY `idx_collection_change_task` (`tenant_id`,`task_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集任务增量同步日志';
