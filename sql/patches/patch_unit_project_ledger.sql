-- ============================================================
-- 计量单位字典 + 项目/成本中心主数据 + 序列号/批次台账索引与字典
-- 表：material_unit / project（序列号、批次沿用 stock_serial / stock_batch / stock_record）
-- ============================================================

-- ========== 计量单位 ==========
CREATE TABLE IF NOT EXISTS material_unit (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  unit_code VARCHAR(64) NOT NULL,
  unit_name VARCHAR(64) NOT NULL,
  category VARCHAR(32) DEFAULT 'COUNT' COMMENT '长度/重量/数量/体积',
  convert_factor DECIMAL(18,6) NOT NULL DEFAULT 1 COMMENT '相对基准单位换算系数',
  base_unit_code VARCHAR(64) COMMENT '基准单位编码（自身为基准时填自身）',
  sort_no INT DEFAULT 0,
  status TINYINT DEFAULT 1,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_unit_code (unit_code),
  KEY idx_unit_category (category, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 项目 / 成本中心 ==========
CREATE TABLE IF NOT EXISTS project (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_code VARCHAR(64) NOT NULL,
  project_name VARCHAR(128) NOT NULL,
  project_type VARCHAR(32) NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT项目 / COST_CENTER成本中心',
  dept_id BIGINT,
  manager_name VARCHAR(64),
  start_date DATE,
  end_date DATE,
  status TINYINT DEFAULT 1,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_project_code (project_code),
  KEY idx_project_type (project_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 台账查询索引（stock_record / stock_serial）==========
SET @idx_serial := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_record' AND INDEX_NAME = 'idx_serial_no'
);
SET @sql := IF(@idx_serial = 0,
  'ALTER TABLE stock_record ADD KEY idx_serial_no (serial_no)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_rec_batch := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_record' AND INDEX_NAME = 'idx_batch_no'
);
SET @sql := IF(@idx_rec_batch = 0,
  'ALTER TABLE stock_record ADD KEY idx_batch_no (batch_no)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_serial_status := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_serial' AND INDEX_NAME = 'idx_serial_status'
);
SET @sql := IF(@idx_serial_status = 0,
  'ALTER TABLE stock_serial ADD KEY idx_serial_status (status, material_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========== 计量单位种子 ==========
INSERT INTO material_unit (id, unit_code, unit_name, category, convert_factor, base_unit_code, sort_no, remark) VALUES
(1,  'EA',   '个',   'COUNT',  1.000000, 'EA', 10, '基准数量单位'),
(2,  'PC',   '件',   'COUNT',  1.000000, 'EA', 20, NULL),
(3,  'BOX',  '盒',   'COUNT',  1.000000, 'EA', 30, NULL),
(4,  'PKG',  '包',   'COUNT',  1.000000, 'EA', 40, NULL),
(5,  'CASE', '箱',   'COUNT',  1.000000, 'EA', 50, '整箱，换算系数可按物资另行约定'),
(6,  'ROLL', '卷',   'COUNT',  1.000000, 'EA', 60, NULL),
(7,  'SET',  '套',   'COUNT',  1.000000, 'EA', 70, NULL),
(8,  'PAIR', '对',   'COUNT',  1.000000, 'EA', 80, NULL),
(9,  'M',    '米',   'LENGTH', 1.000000, 'M',  90, '基准长度单位'),
(10, 'CM',   '厘米', 'LENGTH', 0.010000, 'M',  100, NULL),
(11, 'MM',   '毫米', 'LENGTH', 0.001000, 'M',  110, NULL),
(12, 'KG',   '千克', 'WEIGHT', 1.000000, 'KG', 120, '基准重量单位'),
(13, 'G',    '克',   'WEIGHT', 0.001000, 'KG', 130, NULL),
(14, 'T',    '吨',   'WEIGHT', 1000.000000, 'KG', 140, NULL),
(15, 'L',    '升',   'VOLUME', 1.000000, 'L',  150, '基准体积单位'),
(16, 'ML',   '毫升', 'VOLUME', 0.001000, 'L',  160, NULL),
(17, 'M3',   '立方米', 'VOLUME', 1000.000000, 'L', 170, NULL)
ON DUPLICATE KEY UPDATE
  unit_name = VALUES(unit_name),
  category = VALUES(category),
  convert_factor = VALUES(convert_factor),
  base_unit_code = VALUES(base_unit_code),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark);

-- ========== 项目 / 成本中心种子 ==========
INSERT INTO project (id, project_code, project_name, project_type, dept_id, manager_name, start_date, end_date, status, remark) VALUES
(1, 'PRJ-2026-001', '智慧园区一期',   'PROJECT',     NULL, '张伟', '2026-01-01', '2026-12-31', 1, '演示项目'),
(2, 'PRJ-2026-002', '仓储自动化改造', 'PROJECT',     NULL, '李娜', '2026-03-01', '2026-11-30', 1, '演示项目'),
(3, 'PRJ-2026-003', '办公区装修',     'PROJECT',     NULL, '王强', '2026-02-15', '2026-08-31', 1, '演示项目'),
(4, 'CC-ADMIN',     '行政成本中心',   'COST_CENTER', NULL, '赵敏', NULL,         NULL,         1, '日常行政费用归集'),
(5, 'CC-WAREHOUSE', '仓储成本中心',   'COST_CENTER', NULL, '孙磊', NULL,         NULL,         1, '仓储作业费用归集')
ON DUPLICATE KEY UPDATE
  project_name = VALUES(project_name),
  project_type = VALUES(project_type),
  dept_id = VALUES(dept_id),
  manager_name = VALUES(manager_name),
  start_date = VALUES(start_date),
  end_date = VALUES(end_date),
  status = VALUES(status),
  remark = VALUES(remark);

-- ========== 字典：单位分类 / 项目类型 / 序列号状态 ==========
INSERT INTO sys_dict_type (type_code, type_name, type_group, usage_remark, sort_no) VALUES
('UNIT_CATEGORY', '计量单位分类', 'BASE', '计量单位管理分类列', 390),
('PROJECT_TYPE',  '项目类型',     'BASE', '项目/成本中心类型列', 391),
('SERIAL_STATUS', '序列号状态',   'BUSINESS', '序列号台账状态列', 392)
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name), type_group = VALUES(type_group),
  usage_remark = VALUES(usage_remark), sort_no = VALUES(sort_no);

INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('UNIT_CATEGORY', 'COUNT',  '数量', 10),
('UNIT_CATEGORY', 'LENGTH', '长度', 20),
('UNIT_CATEGORY', 'WEIGHT', '重量', 30),
('UNIT_CATEGORY', 'VOLUME', '体积', 40),
('PROJECT_TYPE', 'PROJECT',     '项目',     10),
('PROJECT_TYPE', 'COST_CENTER', '成本中心', 20),
('SERIAL_STATUS', 'IN_STOCK',   '在库',   10),
('SERIAL_STATUS', 'OUT_STOCK',  '已出库', 20),
('SERIAL_STATUS', 'IN_TRANSIT', '在途',   30),
('SERIAL_STATUS', 'LENT',       '借出',   40),
('SERIAL_STATUS', 'REPAIR',     '维修中', 50),
('SERIAL_STATUS', 'SCRAPPED',   '已报废', 60),
('SERIAL_STATUS', 'LOST',       '遗失',   70)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- ========== 权限码 ==========
-- 注意：id 避开既有占用段（100-170 / 200-250 / 300-364 / 400-463 / 500-551 / 600-613 /
-- 700-721 / 800-820 / 900-902 / 960-999 / 1000-1013），统一用 1020+
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(1020, 200, 'base:unit', '计量单位', 'MENU', '/units', 60),
(1021, 1020, 'base:unit:create', '新增单位', 'BUTTON', NULL, 1),
(1022, 1020, 'base:unit:update', '编辑单位', 'BUTTON', NULL, 2),
(1023, 1020, 'base:unit:delete', '删除单位', 'BUTTON', NULL, 3),
(1030, 200, 'base:project', '项目/成本中心', 'MENU', '/projects', 70),
(1031, 1030, 'base:project:create', '新增项目', 'BUTTON', NULL, 1),
(1032, 1030, 'base:project:update', '编辑项目', 'BUTTON', NULL, 2),
(1033, 1030, 'base:project:delete', '删除项目', 'BUTTON', NULL, 3),
(1040, 400, 'wh:serial', '序列号台账', 'MENU', '/stock-ledgers', 45),
(1041, 1040, 'wh:serial:trail', '查看流转', 'BUTTON', NULL, 1),
(1042, 400, 'wh:batch', '批次台账', 'MENU', '/stock-ledgers', 46),
(1043, 1042, 'wh:batch:trail', '查看流水', 'BUTTON', NULL, 1)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_name = VALUES(perm_name),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  sort_no = VALUES(sort_no);

-- ADMIN 拥有全部；WAREHOUSE 预授序列号/批次台账
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission
WHERE perm_code LIKE 'base:unit%' OR perm_code LIKE 'base:project%'
   OR perm_code LIKE 'wh:serial%' OR perm_code LIKE 'wh:batch%';

INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 2, id FROM sys_permission
WHERE perm_code LIKE 'wh:serial%' OR perm_code LIKE 'wh:batch%';
