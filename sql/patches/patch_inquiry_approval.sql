-- 询价独立审批：purchase_inquiry 补审批人/审批时间 + 按钮权限种子
-- 幂等：字段按 information_schema 判断；权限按 perm_code upsert

SET @c_ab := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry' AND COLUMN_NAME = 'approved_by'
);
SET @sql := IF(@c_ab = 0,
  'ALTER TABLE purchase_inquiry ADD COLUMN approved_by BIGINT NULL AFTER selected_supplier_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_at := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry' AND COLUMN_NAME = 'approved_at'
);
SET @sql := IF(@c_at = 0,
  'ALTER TABLE purchase_inquiry ADD COLUMN approved_at DATETIME NULL AFTER approved_by',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 实体字段补齐（历史库缺列）
SET @c_it := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry' AND COLUMN_NAME = 'inquire_time'
);
SET @sql := IF(@c_it = 0,
  'ALTER TABLE purchase_inquiry ADD COLUMN inquire_time DATETIME NULL AFTER title',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_ps := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry_item' AND COLUMN_NAME = 'price_source'
);
SET @sql := IF(@c_ps = 0,
  'ALTER TABLE purchase_inquiry_item ADD COLUMN price_source VARCHAR(32) NULL AFTER is_selected',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_rm := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry_item' AND COLUMN_NAME = 'remark'
);
SET @sql := IF(@c_rm = 0,
  'ALTER TABLE purchase_inquiry_item ADD COLUMN remark VARCHAR(255) NULL AFTER price_source',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 询价按钮权限：提交/作废/审批/驳回/完成
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(324, 320, 'po:inquiry:submit', '提交询价', 'BUTTON', NULL, 4),
(325, 320, 'po:inquiry:cancel', '作废询价', 'BUTTON', NULL, 5),
(326, 320, 'po:inquiry:approve', '审批通过', 'BUTTON', NULL, 6),
(327, 320, 'po:inquiry:reject', '审批驳回', 'BUTTON', NULL, 7),
(328, 320, 'po:inquiry:complete', '完成询价', 'BUTTON', NULL, 8)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_name = VALUES(perm_name),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  sort_no = VALUES(sort_no);

-- ADMIN 角色授予新按钮
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission
WHERE perm_code IN ('po:inquiry:submit','po:inquiry:cancel','po:inquiry:approve','po:inquiry:reject','po:inquiry:complete');

-- 询价状态标签：APPROVED 语义改为「已通过」（选定供应商后仍为 APPROVED，完成置 COMPLETED）
UPDATE sys_dict SET dict_label = '已通过'
WHERE dict_type = 'PURCHASE_INQUIRY_STATUS' AND dict_key = 'APPROVED';
