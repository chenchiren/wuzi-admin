-- 采购申请关联回来源采购计划（计划行转申请）
-- 1) purchase_apply 补 plan_id / plan_no，便于申请列表反查计划
-- 2) 对 plan_id 建索引

USE wuzi_test;
SET NAMES utf8mb4;

SET @c_plan_id := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_apply' AND COLUMN_NAME = 'plan_id'
);
SET @sql := IF(@c_plan_id = 0,
  'ALTER TABLE purchase_apply ADD COLUMN plan_id BIGINT NULL AFTER id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_plan_no := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_apply' AND COLUMN_NAME = 'plan_no'
);
SET @sql := IF(@c_plan_no = 0,
  'ALTER TABLE purchase_apply ADD COLUMN plan_no VARCHAR(64) NULL AFTER apply_no',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_apply' AND INDEX_NAME = 'idx_plan_id'
);
SET @sql := IF(@c_idx = 0,
  'ALTER TABLE purchase_apply ADD KEY idx_plan_id (plan_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
