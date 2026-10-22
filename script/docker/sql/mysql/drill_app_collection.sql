-- 钻头磨损识别 Android App：采集任务与照片
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `cnpc_drill`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE `cnpc_drill`;

DROP TABLE IF EXISTS `drill_collection_task`;
CREATE TABLE `drill_collection_task` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '服务端任务编号',
    `client_id` varchar(64) NOT NULL COMMENT 'App 本地任务编号，用于幂等同步',
    `user_id` bigint NOT NULL COMMENT '采集用户id',
    `task_no` varchar(64) NOT NULL COMMENT '业务任务号',
    `task_type` varchar(32) NOT NULL COMMENT 'RUN_IN/PULL_OUT',
    `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '任务状态',
    `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_collection_client` (`user_id`,`client_id`,`deleted`),
    UNIQUE KEY `uk_collection_task_no` (`task_no`,`deleted`),
    KEY `idx_collection_status` (`user_id`,`status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 钻头采集任务';

-- 第一步：任务表单。一条任务始终只对应一份表单；修改时更新原记录，不重复插入。
DROP TABLE IF EXISTS `drill_collection_task_form`;
CREATE TABLE `drill_collection_task_form` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '表单编号',
    `task_id` bigint NOT NULL COMMENT '采集任务编号',
    `drill_code` varchar(80) DEFAULT NULL COMMENT '钻头编号',
    `drill_type` varchar(64) NOT NULL COMMENT '钻头类型',
    `drill_model` varchar(64) DEFAULT NULL COMMENT '钻头型号',
    `drill_size` varchar(32) DEFAULT NULL COMMENT '钻头尺寸',
    `manufacturer` varchar(100) DEFAULT NULL COMMENT '生产厂家',
    `production_batch` varchar(64) DEFAULT NULL COMMENT '生产批次',
    `well_number` varchar(80) DEFAULT NULL COMMENT '井号',
    `well_section` varchar(100) DEFAULT NULL COMMENT '井段',
    `formation` varchar(80) DEFAULT NULL COMMENT '地层',
    `operation_time` date DEFAULT NULL COMMENT '作业日期',
    `footage` decimal(12,2) DEFAULT NULL COMMENT '累计进尺（米）',
    `drilling_hours` decimal(12,2) DEFAULT NULL COMMENT '纯钻时间（小时）',
    `location` varchar(200) DEFAULT NULL COMMENT '作业地点',
    `notes` varchar(1000) DEFAULT NULL COMMENT '备注',
--     `task_json` longtext DEFAULT NULL COMMENT 'App 表单扩展字段快照 JSON（兼容原 task_json）',
    --
    `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 钻头采集任务表单';

-- 第二步：任务拍摄清单。一条记录描述一张需要采集的照片，不保存正式文件。
DROP TABLE IF EXISTS `drill_collection_task_photo_item`;
CREATE TABLE `drill_collection_task_photo_item` (
    `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT COMMENT '照片项编号',
    `task_id` bigint NOT NULL COMMENT '采集任务编号',
    `photo_type` varchar(64) NOT NULL COMMENT '照片类型，OVERALL | BLADE',
    `title` varchar(100) NOT NULL COMMENT '照片项标题',
--     `description` varchar(500) DEFAULT NULL COMMENT '拍摄要求',
    `required_flag` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否必拍',
    `blade_no` int DEFAULT NULL COMMENT '刀翼编号；非刀翼照片为空',
    `sort_no` int NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/UPLOADED/PASSED/WARNING/FAILED',
    `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 钻头采集任务照片项';

DROP TABLE IF EXISTS `drill_collection_photo_file`;
CREATE TABLE `drill_collection_photo_file` (
  `id` bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `capture_id` varchar(64) NOT NULL COMMENT '拍摄清单项编号',
  `photo_type` varchar(64) NOT NULL,
  `photo_state` varchar(32) NOT NULL,
  `file_url` varchar(1024) NOT NULL,
--
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 钻头采集照片';
