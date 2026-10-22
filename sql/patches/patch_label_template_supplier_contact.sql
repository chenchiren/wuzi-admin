-- ============================================================
-- 标签模板在线编辑 + 供应商联系人
-- 1) label_template：标签尺寸/显示字段/自定义 HTML
-- 2) supplier_contact：供应商多联系人（主联系人）
-- 3) 权限种子（id 1000+，避开 100-999 占用段）
-- ============================================================

-- ---------- 标签模板 ----------
CREATE TABLE IF NOT EXISTS label_template (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  template_code VARCHAR(64) NOT NULL,
  template_name VARCHAR(128) NOT NULL,
  width_mm DECIMAL(8,2) NOT NULL DEFAULT 50.00,
  height_mm DECIMAL(8,2) NOT NULL DEFAULT 30.00,
  dpi INT NOT NULL DEFAULT 203,
  title VARCHAR(128) DEFAULT '物资标签',
  qr_module_size INT NOT NULL DEFAULT 5,
  show_batch TINYINT NOT NULL DEFAULT 1,
  show_serial TINYINT NOT NULL DEFAULT 1,
  show_supplier TINYINT NOT NULL DEFAULT 0,
  show_price TINYINT NOT NULL DEFAULT 0,
  html_body TEXT NULL COMMENT '可选自定义 HTML 标签体，空则用内置布局',
  status TINYINT DEFAULT 1,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_label_template_code (template_code),
  KEY idx_label_template_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子：默认 50×30 / 60×40（幂等）
INSERT INTO label_template
  (id, template_code, template_name, width_mm, height_mm, dpi, title, qr_module_size,
   show_batch, show_serial, show_supplier, show_price, status)
VALUES
  (1, 'DEFAULT_50X30', '默认 50×30', 50, 30, 203, '物资标签', 5, 1, 1, 0, 0, 1),
  (2, 'DEFAULT_60X40', '默认 60×40', 60, 40, 203, '物资标签', 6, 1, 1, 1, 0, 1)
ON DUPLICATE KEY UPDATE
  template_name = VALUES(template_name),
  width_mm = VALUES(width_mm),
  height_mm = VALUES(height_mm),
  dpi = VALUES(dpi),
  title = VALUES(title),
  qr_module_size = VALUES(qr_module_size),
  show_batch = VALUES(show_batch),
  show_serial = VALUES(show_serial),
  show_supplier = VALUES(show_supplier),
  show_price = VALUES(show_price),
  status = VALUES(status);

-- ---------- 供应商联系人 ----------
CREATE TABLE IF NOT EXISTS supplier_contact (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  supplier_id BIGINT NOT NULL,
  contact_name VARCHAR(64) NOT NULL,
  phone VARCHAR(32),
  email VARCHAR(128),
  position VARCHAR(64),
  is_primary TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_supplier_contact_sid (supplier_id),
  KEY idx_supplier_contact_primary (supplier_id, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 权限：标签模板 ----------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(1000, 700,  'label:template',         '标签模板',   'MENU',   '/label-templates', 25),
(1001, 1000, 'label:template:create',  '新建模板',   'BUTTON', NULL, 1),
(1002, 1000, 'label:template:update',  '编辑模板',   'BUTTON', NULL, 2),
(1003, 1000, 'label:template:delete',  '删除模板',   'BUTTON', NULL, 3),
(1004, 1000, 'label:template:preview', '预览模板',   'BUTTON', NULL, 4)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_name = VALUES(perm_name),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  sort_no = VALUES(sort_no);

-- ---------- 权限：供应商联系人（挂在供应商 230 下） ----------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(1010, 230, 'base:supplier-contact:create',     '新增联系人',   'BUTTON', NULL, 7),
(1011, 230, 'base:supplier-contact:update',     '编辑联系人',   'BUTTON', NULL, 8),
(1012, 230, 'base:supplier-contact:delete',     '删除联系人',   'BUTTON', NULL, 9),
(1013, 230, 'base:supplier-contact:set-primary','设主联系人',   'BUTTON', NULL, 10)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_name = VALUES(perm_name),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  sort_no = VALUES(sort_no);

-- ADMIN 全量预授
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission
WHERE perm_code LIKE 'label:template%' OR perm_code LIKE 'base:supplier-contact%';

-- 仓管：标签模板
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 2, id FROM sys_permission WHERE perm_code LIKE 'label:template%';

-- 采购：供应商联系人
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 3, id FROM sys_permission WHERE perm_code LIKE 'base:supplier-contact%';
