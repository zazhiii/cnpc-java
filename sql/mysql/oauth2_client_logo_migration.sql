-- OAuth2 客户端应用图标 URL 扩容
-- 适用于已经初始化过的 MySQL 数据库

ALTER TABLE `system_oauth2_client`
  MODIFY COLUMN `logo` varchar(2048)
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
  NOT NULL COMMENT '应用图标 URL';
