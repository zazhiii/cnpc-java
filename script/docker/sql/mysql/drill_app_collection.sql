-- 钻头磨损识别 Android App：采集任务与照片

CREATE DATABASE IF NOT EXISTS `cnpc_drill`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE `cnpc_drill`;

CREATE TABLE IF NOT EXISTS `drill_collection_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '服务端任务编号',
  `client_id` varchar(64) NOT NULL COMMENT 'App 本地任务编号，用于幂等同步',
  `task_no` varchar(64) NOT NULL COMMENT '业务任务号',
  `task_type` varchar(32) NOT NULL COMMENT 'RUN_IN/PULL_OUT',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '任务状态',
  `task_json` longtext NOT NULL COMMENT 'App 任务快照 JSON',
  `result_json` longtext DEFAULT NULL COMMENT '识别结果 JSON',
  `collector_user_id` bigint NOT NULL COMMENT '采集用户编号',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_client` (`tenant_id`,`collector_user_id`,`client_id`,`deleted`),
  KEY `idx_collection_status` (`tenant_id`,`collector_user_id`,`status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 钻头采集任务';

CREATE TABLE IF NOT EXISTS `drill_collection_photo` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `capture_id` varchar(64) NOT NULL COMMENT '拍摄清单项编号',
  `photo_type` varchar(64) NOT NULL,
  `photo_state` varchar(32) NOT NULL,
  `file_url` varchar(1024) NOT NULL,
  `audit_region_json` text DEFAULT NULL,
  `audit_report_json` longtext DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_photo` (`tenant_id`,`task_id`,`capture_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 钻头采集照片';
