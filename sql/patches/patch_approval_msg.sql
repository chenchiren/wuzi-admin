USE wuzi_test;
SET NAMES utf8mb4;

-- ============================================================
-- 1) 审批流定义 / 节点
-- ============================================================
CREATE TABLE IF NOT EXISTS approval_flow_def (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  flow_code VARCHAR(64) NOT NULL,
  flow_name VARCHAR(128) NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  status TINYINT DEFAULT 1,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_flow_code (flow_code),
  KEY idx_biz_type (biz_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS approval_flow_node (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  flow_id BIGINT NOT NULL,
  node_no VARCHAR(32) NOT NULL,
  node_name VARCHAR(64) NOT NULL,
  order_no INT NOT NULL DEFAULT 1,
  approver_role VARCHAR(64),
  assignee_id BIGINT,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_flow_node (flow_id, node_no),
  KEY idx_flow_order (flow_id, order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 2) approval_instance 扩展列 current_node_no / flow_id
--    （已存在同名列时自动跳过；若环境不允许 ALTER，请使用下方 approval_instance_ext）
-- ============================================================
SET @col_flow := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'approval_instance' AND COLUMN_NAME = 'flow_id'
);
SET @sql := IF(@col_flow = 0,
  'ALTER TABLE approval_instance ADD COLUMN flow_id BIGINT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_node := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'approval_instance' AND COLUMN_NAME = 'current_node_no'
);
SET @sql := IF(@col_node = 0,
  'ALTER TABLE approval_instance ADD COLUMN current_node_no VARCHAR(32) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 扩展表（Java 侧始终使用本表跟踪当前节点，兼容无法 ALTER 的环境）
CREATE TABLE IF NOT EXISTS approval_instance_ext (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  instance_id BIGINT NOT NULL,
  flow_id BIGINT,
  current_node_no VARCHAR(32),
  current_order_no INT DEFAULT 1,
  assignee_id BIGINT,
  extra_approver_ids VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3) 消息中心
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_message (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(128) NOT NULL,
  content VARCHAR(512),
  biz_type VARCHAR(32),
  biz_id BIGINT,
  receiver_id BIGINT NOT NULL,
  is_read TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_receiver (receiver_id, is_read),
  KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 4) 默认角色 / 种子审批流
--    PURCHASE_APPLY：部门负责人 -> 财务
--    PURCHASE_ORDER：单节点
-- ============================================================
INSERT INTO sys_role (id, role_code, role_name) VALUES
(6, 'DEPT_MANAGER', '部门负责人')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

INSERT INTO approval_flow_def (id, flow_code, flow_name, biz_type, status, remark) VALUES
(1, 'PURCHASE_APPLY', '采购申请审批', 'PURCHASE_APPLY', 1, '两节点：部门负责人→财务'),
(2, 'PURCHASE_ORDER', '采购订单审批', 'PURCHASE_ORDER', 1, '单节点')
ON DUPLICATE KEY UPDATE flow_name = VALUES(flow_name), biz_type = VALUES(biz_type), status = VALUES(status);

INSERT INTO approval_flow_node (id, flow_id, node_no, node_name, order_no, approver_role, assignee_id) VALUES
(1, 1, 'N1', '部门负责人审批', 1, 'DEPT_MANAGER', NULL),
(2, 1, 'N2', '财务审批', 2, 'FINANCE', NULL),
(3, 2, 'N1', '订单审批', 1, 'FINANCE', NULL)
ON DUPLICATE KEY UPDATE node_name = VALUES(node_name), order_no = VALUES(order_no),
  approver_role = VALUES(approver_role), assignee_id = VALUES(assignee_id);
