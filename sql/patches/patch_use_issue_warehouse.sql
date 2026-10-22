-- 领用申请记录发料仓库，便于列表反查库存扣减来源
USE wuzi_test;
SET NAMES utf8mb4;

SET @c := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material_use_apply' AND COLUMN_NAME = 'issue_warehouse_id'
);
SET @sql := IF(@c = 0,
  'ALTER TABLE material_use_apply ADD COLUMN issue_warehouse_id BIGINT NULL AFTER status',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
