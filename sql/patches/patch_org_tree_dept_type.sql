-- 组织层级：公司 / 部门 / 小组
-- 用户挂最细节点（小组），部长/副部长挂部门节点，小组长挂小组节点

SET @c_type := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dept' AND COLUMN_NAME = 'dept_type'
);
SET @sql := IF(@c_type = 0,
  'ALTER TABLE sys_dept ADD COLUMN dept_type VARCHAR(16) NOT NULL DEFAULT ''DEPT'' AFTER dept_name',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 按树深推断默认类型：根=公司，二级=部门，更下=小组
UPDATE sys_dept d
LEFT JOIN sys_dept p ON p.id = d.parent_id
SET d.dept_type = CASE
  WHEN d.parent_id IS NULL OR d.parent_id = 0 THEN 'COMPANY'
  WHEN p.parent_id IS NULL OR p.parent_id = 0 THEN 'DEPT'
  ELSE 'GROUP'
END;

-- 示例：仓储部下建仓储一组（若尚无 GROUP）
INSERT INTO sys_dept (parent_id, dept_code, dept_name, dept_type, sort_no, status, is_deleted)
SELECT 2, 'WH-G1', '仓储一组', 'GROUP', 10, 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dept WHERE dept_code = 'WH-G1'
);

-- 审批人类型 / 部门范围字典
INSERT INTO sys_dict_type (type_code, type_name, type_group, usage_remark, sort_no) VALUES
('DEPT_TYPE', '组织节点类型', 'BASE', '部门/小组管理', 372)
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name);

INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('DEPT_TYPE', 'COMPANY', '公司', 10),
('DEPT_TYPE', 'DEPT', '部门', 20),
('DEPT_TYPE', 'GROUP', '小组', 30),
('APPROVER_TYPE', 'TEAM_LEADER', '小组长', 50),
('DEPT_SCOPE', 'DEPT', '本部门含兄弟组', 25)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- 可选：把示例组员挂到小组（wh01 仍是仓储部部长，留在部门节点）
-- UPDATE sys_user SET dept_id = (SELECT id FROM sys_dept WHERE dept_code='WH-G1') WHERE username='rd01';
