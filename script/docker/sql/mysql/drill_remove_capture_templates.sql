-- 已部署过模板版采集任务结构时执行一次。
-- 清单现在由 Android 端决定，后端只保存任务拍摄项快照。
SET NAMES utf8mb4;

ALTER TABLE `drill_collection_capture`
  DROP COLUMN `template_item_id`;

ALTER TABLE `drill_collection_task`
  DROP COLUMN `capture_template_code`,
  DROP COLUMN `capture_template_version`;

DROP TABLE IF EXISTS `drill_capture_template_item`;
DROP TABLE IF EXISTS `drill_capture_template`;
