-- 采购行税率：税码快照（字典选种 / 手填时 tax_code 为空）
-- 采购申请行补 tax_code

SET @c_apply := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_apply_item' AND COLUMN_NAME = 'tax_code'
);
SET @sql := IF(@c_apply = 0,
  'ALTER TABLE purchase_apply_item ADD COLUMN tax_code VARCHAR(32) NULL AFTER tax_rate',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 发票：记录票面税种编码（可空，手填税率时为空）
SET @c_inv := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'invoice' AND COLUMN_NAME = 'tax_code'
);
SET @sql := IF(@c_inv = 0,
  'ALTER TABLE invoice ADD COLUMN tax_code VARCHAR(32) NULL AFTER tax_rate',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 确保采购订单行有 tax_code（历史补丁已加则跳过）
SET @c_oi := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order_item' AND COLUMN_NAME = 'tax_code'
);
SET @sql := IF(@c_oi = 0,
  'ALTER TABLE purchase_order_item ADD COLUMN tax_code VARCHAR(32) NULL AFTER tax_rate',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 询价行 / 退货行 / 到货行
SET @c_ii := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry_item' AND COLUMN_NAME = 'tax_code'
);
SET @sql := IF(@c_ii = 0,
  'ALTER TABLE purchase_inquiry_item ADD COLUMN tax_code VARCHAR(32) NULL AFTER tax_rate',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_ri := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_return_item' AND COLUMN_NAME = 'tax_code'
);
SET @sql := IF(@c_ri = 0,
  'ALTER TABLE purchase_return_item ADD COLUMN tax_code VARCHAR(32) NULL AFTER tax_rate',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
