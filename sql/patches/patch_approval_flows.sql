USE wuzi_test;
SET NAMES utf8mb4;

-- ============================================================
-- 已完成功能：多级审批流定义补齐
-- 依赖 patch_approval_msg.sql 中的 approval_flow_def / approval_flow_node
-- ============================================================

INSERT INTO sys_role (id, role_code, role_name) VALUES
(6, 'DEPT_MANAGER', '部门负责人')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- 财务演示账号（密码与 admin123 同哈希占位，实际以库中 seed 为准）
INSERT INTO sys_user (id, username, password, real_name, dept_id) VALUES
(5, 'fin01', '$2a$10$EqKcp1WFfvXhqhS6xFIeV.0n9nJq7x3Yq8mH8Yq8mH8Yq8mH8Yq8m', '钱财务', 2)
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(5, 5),  -- fin01 -> FINANCE
(5, 6),  -- fin01 -> DEPT_MANAGER
(2, 6)   -- wh01 也兼部门负责人，便于演示
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO approval_flow_def (id, flow_code, flow_name, biz_type, status, remark) VALUES
(1,  'PURCHASE_APPLY',   '采购申请审批', 'PURCHASE_APPLY',   1, '部门负责人→财务'),
(2,  'PURCHASE_ORDER',   '采购订单审批', 'PURCHASE_ORDER',   1, '财务审批'),
(3,  'PURCHASE_RETURN',  '采购退货审批', 'PURCHASE_RETURN',  1, '采购→财务'),
(4,  'MATERIAL_USE',     '领用申请审批', 'MATERIAL_USE',     1, '部门负责人→仓管'),
(5,  'BORROW',           '借用申请审批', 'BORROW',           1, '部门负责人'),
(6,  'SCRAP',            '报废审批',     'SCRAP',            1, '部门负责人→仓管'),
(7,  'TRANSFER',         '调拨审批',     'TRANSFER',         1, '仓管审批'),
(8,  'RETURN_STORE',     '退库审批',     'RETURN_STORE',     1, '部门负责人'),
(9,  'INVENTORY_CHECK',  '盘点审批',     'INVENTORY_CHECK',  1, '仓管审批'),
(10, 'PURCHASE_ARRIVAL', '到货验收审批', 'PURCHASE_ARRIVAL', 1, '仓管审批'),
(11, 'OUTBOUND',         '出库审批',     'OUTBOUND',         1, '仓管审批'),
(12, 'PURCHASE_INQUIRY', '询价审批',     'PURCHASE_INQUIRY', 1, '采购审批')
ON DUPLICATE KEY UPDATE flow_name = VALUES(flow_name),
  biz_type = VALUES(biz_type), status = VALUES(status), remark = VALUES(remark);

INSERT INTO approval_flow_node (id, flow_id, node_no, node_name, order_no, approver_role, assignee_id) VALUES
-- 1 采购申请
(1,  1,  'N1', '部门负责人审批', 1, 'DEPT_MANAGER', NULL),
(2,  1,  'N2', '财务审批',       2, 'FINANCE',      NULL),
-- 2 采购订单
(3,  2,  'N1', '订单审批',       1, 'FINANCE',      NULL),
-- 3 采购退货
(4,  3,  'N1', '采购复核',       1, 'PURCHASER',    NULL),
(5,  3,  'N2', '财务审批',       2, 'FINANCE',      NULL),
-- 4 领用
(6,  4,  'N1', '部门负责人审批', 1, 'DEPT_MANAGER', NULL),
(7,  4,  'N2', '仓管确认',       2, 'WAREHOUSE',    NULL),
-- 5 借用
(8,  5,  'N1', '部门负责人审批', 1, 'DEPT_MANAGER', NULL),
-- 6 报废
(9,  6,  'N1', '部门负责人审批', 1, 'DEPT_MANAGER', NULL),
(10, 6,  'N2', '仓管确认',       2, 'WAREHOUSE',    NULL),
-- 7 调拨
(11, 7,  'N1', '仓管审批',       1, 'WAREHOUSE',    NULL),
-- 8 退库
(12, 8,  'N1', '部门负责人审批', 1, 'DEPT_MANAGER', NULL),
-- 9 盘点
(13, 9,  'N1', '仓管审批',       1, 'WAREHOUSE',    NULL),
-- 10 到货
(14, 10, 'N1', '仓管验收审批',   1, 'WAREHOUSE',    NULL),
-- 11 出库
(15, 11, 'N1', '仓管审批',       1, 'WAREHOUSE',    NULL),
-- 12 询价
(16, 12, 'N1', '采购审批',       1, 'PURCHASER',    NULL)
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name),
  order_no = VALUES(order_no),
  approver_role = VALUES(approver_role),
  assignee_id = VALUES(assignee_id);
