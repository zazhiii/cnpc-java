-- 钻头图片质检：方案与按资产执行记录
-- 适用数据库：MySQL 8.x

CREATE TABLE IF NOT EXISTS `drill_quality_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `profile_code` varchar(64) NOT NULL,
  `name` varchar(100) NOT NULL,
  `version` varchar(32) NOT NULL,
  `model_code` varchar(40) NOT NULL,
  `model_config_json` json NOT NULL,
  `status` varchar(24) NOT NULL,
  `default_profile` bit(1) NOT NULL DEFAULT b'0',
  `enabled_at` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `default_tenant_key` bigint GENERATED ALWAYS AS
    (CASE WHEN (`default_profile` = b'1' AND `deleted` = b'0') THEN `tenant_id` ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quality_profile_version` (`tenant_id`,`profile_code`,`version`,`deleted`),
  UNIQUE KEY `uk_quality_profile_default` (`default_tenant_key`),
  KEY `idx_quality_profile_status` (`tenant_id`,`status`,`model_code`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片质检方案';

CREATE TABLE IF NOT EXISTS `drill_image_quality_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_id` bigint NOT NULL,
  `profile_id` bigint NOT NULL,
  `profile_code` varchar(64) NOT NULL,
  `profile_version` varchar(32) NOT NULL,
  `model_code` varchar(40) NOT NULL,
  `model_version` varchar(64) DEFAULT NULL,
  `effective_config_json` json NOT NULL,
  `execution_status` varchar(24) NOT NULL,
  `passed` bit(1) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `error_code` int DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `started_at` datetime NOT NULL,
  `finished_at` datetime NOT NULL,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_quality_record_asset` (`tenant_id`,`asset_id`,`id`),
  KEY `idx_quality_record_profile` (`tenant_id`,`profile_id`,`id`),
  KEY `idx_quality_record_status` (`tenant_id`,`execution_status`,`passed`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片质检记录';

-- 为脚本执行时已存在的有效租户预置 E11 默认方案。
INSERT INTO `drill_quality_profile`
(`profile_code`,`name`,`version`,`model_code`,`model_config_json`,`status`,
 `default_profile`,`enabled_at`,`remark`,`tenant_id`)
SELECT 'E11_STANDARD','E11 标准图片质检','1.0.0','E11',
       JSON_OBJECT('confidence',0.75,'minDetections',1),'ENABLED',b'1',NOW(),
       '系统预置；模型运行路径由 drill.e11-quality-gate 配置',tenant.`id`
FROM `system_tenant` tenant
WHERE tenant.`deleted` = b'0' AND tenant.`status` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `drill_quality_profile` current_default
    WHERE current_default.`tenant_id` = tenant.`id`
      AND current_default.`default_profile` = b'1'
      AND current_default.`deleted` = b'0'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `drill_quality_profile` profile
    WHERE profile.`tenant_id` = tenant.`id`
      AND profile.`profile_code` = 'E11_STANDARD'
      AND profile.`version` = '1.0.0'
      AND profile.`deleted` = b'0'
  );

-- 图片质检管理端菜单。菜单使用动态 ID，避免与不同环境已有菜单编号冲突。
SET @quality_root_id := (
  SELECT `id` FROM `system_menu`
  WHERE `path` = '/drill/image-quality' AND `deleted` = b'0'
  ORDER BY `id` LIMIT 1
);
SET @quality_next_menu_id := (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `system_menu`);
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT @quality_next_menu_id,'图片质检','',1,35,0,'/drill/image-quality','ep:circle-check',NULL,NULL,
       0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE @quality_root_id IS NULL;
SET @quality_root_id := COALESCE(@quality_root_id, @quality_next_menu_id);

SET @quality_record_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @quality_root_id AND `path` = 'records' AND `deleted` = b'0'
  ORDER BY `id` LIMIT 1
);
SET @quality_next_menu_id := (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `system_menu`);
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT @quality_next_menu_id,'质检记录','drill:image-quality:query',2,1,@quality_root_id,'records',
       'ep:list','drill/imageQuality/records/index','DrillImageQualityRecords',
       0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE @quality_record_menu_id IS NULL;

SET @quality_profile_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @quality_root_id AND `path` = 'profiles' AND `deleted` = b'0'
  ORDER BY `id` LIMIT 1
);
SET @quality_next_menu_id := (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `system_menu`);
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT @quality_next_menu_id,'质检方案','drill:quality-profile:query',2,2,@quality_root_id,'profiles',
       'ep:setting','drill/imageQuality/profiles/index','DrillImageQualityProfiles',
       0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE @quality_profile_menu_id IS NULL;
SET @quality_profile_menu_id := COALESCE(@quality_profile_menu_id, @quality_next_menu_id);

SET @quality_manage_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @quality_profile_menu_id
    AND `permission` = 'drill:quality-profile:manage' AND `deleted` = b'0'
  ORDER BY `id` LIMIT 1
);
SET @quality_next_menu_id := (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `system_menu`);
INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
 `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT @quality_next_menu_id,'质检方案管理','drill:quality-profile:manage',3,1,
       @quality_profile_menu_id,'','','',NULL,0,b'1',b'1',b'1','admin',NOW(),'admin',NOW(),b'0'
WHERE @quality_manage_menu_id IS NULL;
