-- 发票结算 / 附件 / 供应商评价 扩展表
USE wuzi_test;
SET NAMES utf8mb4;

-- 发票（关联采购订单，支撑三单匹配）
CREATE TABLE IF NOT EXISTS invoice (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  invoice_no VARCHAR(64) NOT NULL,
  invoice_code VARCHAR(32),
  invoice_type VARCHAR(32) DEFAULT 'VAT_SPECIAL',
  order_id BIGINT,
  supplier_id BIGINT,
  invoice_date DATE,
  tax_rate DECIMAL(5,2) DEFAULT 0,
  tax_inclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_exclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_amount DECIMAL(18,2) DEFAULT 0,
  status VARCHAR(32) DEFAULT 'REGISTERED',
  match_status VARCHAR(32) DEFAULT 'NONE',
  match_result TEXT,
  dept_id BIGINT,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_invoice_no (invoice_no),
  KEY idx_invoice_order (order_id),
  KEY idx_invoice_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 结算单（按订单行核销）
CREATE TABLE IF NOT EXISTS settle_order (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  settle_no VARCHAR(64) NOT NULL,
  order_id BIGINT NOT NULL,
  invoice_id BIGINT,
  supplier_id BIGINT,
  settle_date DATE,
  settle_amount DECIMAL(18,2) DEFAULT 0,
  tax_amount DECIMAL(18,2) DEFAULT 0,
  status VARCHAR(32) DEFAULT 'CONFIRMED',
  dept_id BIGINT,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_settle_no (settle_no),
  KEY idx_settle_order (order_id),
  KEY idx_settle_invoice (invoice_id),
  KEY idx_settle_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS settle_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  settle_id BIGINT NOT NULL,
  order_item_id BIGINT NOT NULL,
  material_id BIGINT,
  settle_qty DECIMAL(18,3) DEFAULT 0,
  settle_amount DECIMAL(18,2) DEFAULT 0,
  tax_inclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_amount DECIMAL(18,2) DEFAULT 0,
  remark VARCHAR(255),
  KEY idx_settle_item (settle_id),
  KEY idx_settle_order_item (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 附件（本地磁盘存储元数据）
CREATE TABLE IF NOT EXISTS attachment (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  biz_type VARCHAR(64) NOT NULL,
  biz_id BIGINT,
  file_name VARCHAR(255) NOT NULL,
  origin_name VARCHAR(255),
  file_path VARCHAR(512) NOT NULL,
  file_size BIGINT DEFAULT 0,
  content_type VARCHAR(128),
  md5 VARCHAR(64),
  uploaded_by BIGINT,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_att_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 供应商评价得分
CREATE TABLE IF NOT EXISTS supplier_eval_score (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  supplier_id BIGINT NOT NULL,
  eval_date DATE,
  period_start DATE,
  period_end DATE,
  on_time_rate DECIMAL(6,2) DEFAULT 0,
  return_rate DECIMAL(6,2) DEFAULT 0,
  price_score DECIMAL(6,2) DEFAULT 0,
  weight_on_time DECIMAL(5,2) DEFAULT 40,
  weight_return DECIMAL(5,2) DEFAULT 30,
  weight_price DECIMAL(5,2) DEFAULT 30,
  total_score DECIMAL(6,2) DEFAULT 0,
  order_count INT DEFAULT 0,
  return_count INT DEFAULT 0,
  detail_json TEXT,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_eval_supplier (supplier_id),
  KEY idx_eval_date (eval_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
