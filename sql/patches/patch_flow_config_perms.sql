-- ============================================================
-- 审批流配置按钮权限
-- 幂等：按 perm_code 更新；ADMIN 自动获得全部
-- ============================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(171, 170, 'flow:config:create', '新建流程/节点', 'BUTTON', NULL, 1),
(172, 170, 'flow:config:update', '编辑流程/节点', 'BUTTON', NULL, 2),
(173, 170, 'flow:config:delete', '删除流程/节点', 'BUTTON', NULL, 3)
ON DUPLICATE KEY UPDATE
  perm_name = VALUES(perm_name),
  parent_id = VALUES(parent_id),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  sort_no = VALUES(sort_no),
  is_deleted = 0;

INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission WHERE perm_code LIKE 'flow:config%';
