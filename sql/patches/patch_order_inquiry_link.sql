-- 采购订单关联回来源询价（询价选定供应商生成订单）
USE wuzi_test;
SET NAMES utf8mb4;

SET @c_inquiry_id := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'inquiry_id'
);
SET @sql := IF(@c_inquiry_id = 0,
  'ALTER TABLE purchase_order ADD COLUMN inquiry_id BIGINT NULL AFTER apply_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_inquiry_no := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'inquiry_no'
);
SET @sql := IF(@c_inquiry_no = 0,
  'ALTER TABLE purchase_order ADD COLUMN inquiry_no VARCHAR(64) NULL AFTER inquiry_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND INDEX_NAME = 'idx_inquiry_id'
);
SET @sql := IF(@c_idx = 0,
  'ALTER TABLE purchase_order ADD KEY idx_inquiry_id (inquiry_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
