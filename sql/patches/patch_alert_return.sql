USE wuzi_test;
SET NAMES utf8mb4;

-- 退库单（领用退回完整流程：申请 → 仓管验收 → 确认入库）
CREATE TABLE IF NOT EXISTS return_store_order (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  return_no VARCHAR(64) NOT NULL,
  apply_id BIGINT,
  warehouse_id BIGINT NOT NULL,
  apply_user_id BIGINT,
  accept_user_id BIGINT,
  confirm_user_id BIGINT,
  status VARCHAR(32) DEFAULT 'APPLIED',
  apply_remark VARCHAR(255),
  accept_remark VARCHAR(255),
  confirm_remark VARCHAR(255),
  apply_time DATETIME,
  accept_time DATETIME,
  confirm_time DATETIME,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_return_no (return_no),
  KEY idx_wh_status (warehouse_id, status),
  KEY idx_apply (apply_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS return_store_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  return_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  batch_no VARCHAR(64),
  location_id BIGINT,
  apply_qty DECIMAL(18,3) NOT NULL DEFAULT 0,
  accept_qty DECIMAL(18,3),
  actual_qty DECIMAL(18,3),
  tax_incl_price DECIMAL(18,6) DEFAULT 0,
  tax_rate DECIMAL(5,2),
  condition_status VARCHAR(32) DEFAULT 'GOOD',
  remark VARCHAR(255),
  KEY idx_return (return_id),
  KEY idx_mat (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预警记录（临期 / 低库存 / 超库存）
CREATE TABLE IF NOT EXISTS alert_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  alert_type VARCHAR(32) NOT NULL,
  alert_level VARCHAR(16) DEFAULT 'WARN',
  title VARCHAR(128),
  content VARCHAR(500),
  material_id BIGINT,
  warehouse_id BIGINT,
  batch_no VARCHAR(64),
  qty DECIMAL(18,3),
  threshold_val DECIMAL(18,3),
  expire_date DATE,
  biz_no VARCHAR(64),
  status VARCHAR(16) DEFAULT 'OPEN',
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_type_time (alert_type, created_at),
  KEY idx_status (status),
  KEY idx_mat (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
