-- ============================================================
-- 组织架构扩展：职位（岗位）层
-- 与角色分离：角色管权限（能做什么），职位管组织职务（部长/副部长等）
-- 审批流节点可按职位 / 本部门负责人解析审批人
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_position (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  position_code VARCHAR(64) NOT NULL,
  position_name VARCHAR(64) NOT NULL,
  position_level INT NOT NULL DEFAULT 0,
  is_leader TINYINT NOT NULL DEFAULT 0,
  sort_no INT DEFAULT 0,
  status TINYINT DEFAULT 1,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_position_code (position_code),
  KEY idx_position_level (position_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户挂职位
SET @col_pos := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'position_id'
);
SET @sql := IF(@col_pos = 0,
  'ALTER TABLE sys_user ADD COLUMN position_id BIGINT NULL AFTER dept_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 审批节点扩展：审批人类型 / 职位编码 / 部门范围
SET @col_type := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'approval_flow_node' AND COLUMN_NAME = 'approver_type'
);
SET @sql := IF(@col_type = 0,
  'ALTER TABLE approval_flow_node ADD COLUMN approver_type VARCHAR(32) NOT NULL DEFAULT ''ROLE'' AFTER assignee_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_pos_code := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'approval_flow_node' AND COLUMN_NAME = 'position_code'
);
SET @sql := IF(@col_pos_code = 0,
  'ALTER TABLE approval_flow_node ADD COLUMN position_code VARCHAR(64) NULL AFTER approver_type',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_scope := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'approval_flow_node' AND COLUMN_NAME = 'dept_scope'
);
SET @sql := IF(@col_scope = 0,
  'ALTER TABLE approval_flow_node ADD COLUMN dept_scope VARCHAR(16) NOT NULL DEFAULT ''ALL'' AFTER position_code',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========== 职位种子：职级越大越高 ==========
INSERT INTO sys_position (id, position_code, position_name, position_level, is_leader, sort_no, remark) VALUES
(1, 'GM',            '总经理',   100, 1, 10, '公司负责人'),
(2, 'DIR',           '总监',      90, 1, 20, '分管领导'),
(3, 'DEPT_HEAD',     '部长',      80, 1, 30, '部门正职'),
(4, 'DEPT_VICE',     '副部长',    70, 1, 40, '部门副职'),
(5, 'MANAGER',       '经理',      60, 1, 50, '科室/业务负责人'),
(6, 'SUPERVISOR',    '主管',      50, 1, 60, '业务主管'),
(7, 'TEAM_LEAD',     '组长',      40, 1, 70, '班组/小组长'),
(8, 'SPECIALIST',    '专员',      30, 0, 80, '专业岗'),
(9, 'STAFF',         '职员',      20, 0, 90, '普通员工')
ON DUPLICATE KEY UPDATE
  position_name = VALUES(position_name),
  position_level = VALUES(position_level),
  is_leader = VALUES(is_leader),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark);

-- ========== 审批流节点：部门负责人改为职位解析 ==========
-- 原 approver_role=DEPT_MANAGER 的「部门负责人审批」改为 DEPT_LEADER + 本部门
UPDATE approval_flow_node
SET approver_type = 'DEPT_LEADER',
    dept_scope = 'SELF',
    position_code = NULL,
    approver_role = NULL
WHERE approver_role = 'DEPT_MANAGER'
   OR node_name LIKE '%部门负责人%';

-- 明确指定职位的节点示例（副部长复核可在此扩展）
-- UPDATE approval_flow_node SET approver_type='POSITION', position_code='DEPT_VICE', dept_scope='SELF'
--  WHERE node_name LIKE '%副部长%';

-- ========== 职位管理权限 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(135, 100, 'sys:position', '职位管理', 'MENU', '/positions', 35),
(136, 135, 'sys:position:create', '新增职位', 'BUTTON', NULL, 1),
(137, 135, 'sys:position:update', '编辑职位', 'BUTTON', NULL, 2),
(138, 135, 'sys:position:delete', '删除职位', 'BUTTON', NULL, 3)
ON DUPLICATE KEY UPDATE perm_name = VALUES(perm_name), parent_id = VALUES(parent_id), path = VALUES(path);

-- ADMIN 拥有全部
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission WHERE perm_code LIKE 'sys:position%';

-- 字典：审批人类型 / 部门范围
INSERT INTO sys_dict_type (type_code, type_name, type_group, usage_remark, sort_no) VALUES
('APPROVER_TYPE', '审批人类型', 'BASE', '审批流节点', 370),
('DEPT_SCOPE', '部门范围', 'BASE', '审批流节点', 371)
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name), type_group = VALUES(type_group),
  usage_remark = VALUES(usage_remark), sort_no = VALUES(sort_no);

INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('APPROVER_TYPE', 'USER', '指定人员', 10),
('APPROVER_TYPE', 'ROLE', '按角色', 20),
('APPROVER_TYPE', 'POSITION', '按职位', 30),
('APPROVER_TYPE', 'DEPT_LEADER', '部门负责人', 40),
('DEPT_SCOPE', 'ALL', '不限部门', 10),
('DEPT_SCOPE', 'SELF', '本部门', 20),
('DEPT_SCOPE', 'PARENT', '上级部门', 30)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- 示例用户职位（幂等，便于演示）
-- 仓储部 wh01 任部长
UPDATE sys_user SET position_id = 3 WHERE username = 'wh01';
-- 采购部 pur01 任部长
UPDATE sys_user SET position_id = 3 WHERE username = 'pur01';
-- 研发部 rd01 任专员
UPDATE sys_user SET position_id = 8 WHERE username = 'rd01';
-- 财务 fin01 任副部长
UPDATE sys_user SET position_id = 4 WHERE username = 'fin01';
