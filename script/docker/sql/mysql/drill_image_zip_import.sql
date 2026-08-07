-- 多目录、多 ZIP 图片异步导入（MySQL 8.x，增量迁移）

CREATE TABLE IF NOT EXISTS `drill_image_import_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '导入任务编号',
  `job_no` varchar(48) NOT NULL COMMENT '任务业务编号',
  `batch_id` bigint NOT NULL COMMENT '图片批次编号',
  `name` varchar(100) NOT NULL COMMENT '任务名称',
  `root_name` varchar(255) NOT NULL COMMENT '根目录名称',
  `status` varchar(32) NOT NULL COMMENT '任务状态',
  `total_package_count` int NOT NULL DEFAULT 0,
  `uploaded_package_count` int NOT NULL DEFAULT 0,
  `success_package_count` int NOT NULL DEFAULT 0,
  `failed_package_count` int NOT NULL DEFAULT 0,
  `total_image_count` int NOT NULL DEFAULT 0,
  `created_count` int NOT NULL DEFAULT 0,
  `duplicate_count` int NOT NULL DEFAULT 0,
  `failed_count` int NOT NULL DEFAULT 0,
  `uploaded_bytes` bigint NOT NULL DEFAULT 0,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `error_summary` varchar(500) DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zip_job_no` (`tenant_id`, `job_no`, `deleted`),
  UNIQUE KEY `uk_zip_job_batch` (`tenant_id`, `batch_id`, `deleted`),
  KEY `idx_zip_job_status` (`tenant_id`, `status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZIP 图片异步导入任务';

CREATE TABLE IF NOT EXISTS `drill_image_import_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ZIP 子任务编号',
  `job_id` bigint NOT NULL,
  `batch_id` bigint NOT NULL,
  `client_package_id` varchar(96) NOT NULL,
  `relative_path` varchar(512) NOT NULL,
  `well_type` varchar(64) NOT NULL,
  `original_name` varchar(255) NOT NULL,
  `declared_size` bigint NOT NULL DEFAULT 0,
  `last_modified` bigint DEFAULT NULL,
  `archive_file_id` bigint DEFAULT NULL,
  `file_config_id` bigint DEFAULT NULL,
  `object_key` varchar(512) DEFAULT NULL,
  `original_url` varchar(1024) DEFAULT NULL,
  `actual_size` bigint DEFAULT NULL,
  `sha256` char(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `total_image_count` int NOT NULL DEFAULT 0,
  `created_count` int NOT NULL DEFAULT 0,
  `duplicate_count` int NOT NULL DEFAULT 0,
  `failed_count` int NOT NULL DEFAULT 0,
  `ignored_json_count` int NOT NULL DEFAULT 0,
  `warning_count` int NOT NULL DEFAULT 0,
  `retry_count` int NOT NULL DEFAULT 0,
  `worker_id` varchar(64) DEFAULT NULL,
  `lease_until` datetime DEFAULT NULL,
  `heartbeat_time` datetime DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `cleanup_pending` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zip_package_path` (`tenant_id`, `job_id`, `relative_path`, `deleted`),
  KEY `idx_zip_package_status` (`tenant_id`, `status`, `id`),
  KEY `idx_zip_package_job` (`tenant_id`, `job_id`, `id`),
  KEY `idx_zip_package_cleanup` (`cleanup_pending`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ZIP 图片导入包';

ALTER TABLE `drill_image_batch`
  ADD COLUMN `source_package_count` int NOT NULL DEFAULT 0 AFTER `pending_metadata_count`,
  ADD COLUMN `failed_package_count` int NOT NULL DEFAULT 0 AFTER `source_package_count`;

ALTER TABLE `drill_image_asset`
  ADD COLUMN `well_type` varchar(64) DEFAULT NULL AFTER `scene_type`,
  ADD COLUMN `source_package_id` bigint DEFAULT NULL AFTER `source_biz_id`,
  ADD COLUMN `source_entry_path` varchar(512) DEFAULT NULL AFTER `source_package_id`,
  ADD KEY `idx_asset_well_type` (`tenant_id`, `well_type`, `create_time`),
  ADD KEY `idx_asset_source_package` (`tenant_id`, `source_package_id`);

ALTER TABLE `drill_image_import_record`
  ADD COLUMN `job_id` bigint DEFAULT NULL AFTER `batch_id`,
  ADD COLUMN `package_id` bigint DEFAULT NULL AFTER `job_id`,
  ADD COLUMN `source_entry_path` varchar(512) DEFAULT NULL AFTER `client_file_id`,
  ADD COLUMN `detected_mime_type` varchar(100) DEFAULT NULL AFTER `sha256`,
  ADD COLUMN `attempt_count` int NOT NULL DEFAULT 1 AFTER `detected_mime_type`,
  ADD COLUMN `warning_code` varchar(64) DEFAULT NULL AFTER `attempt_count`,
  ADD UNIQUE KEY `uk_import_package_entry` (`tenant_id`, `package_id`, `source_entry_path`, `deleted`),
  ADD KEY `idx_import_package` (`tenant_id`, `package_id`, `id`);

