USE wuzi_test;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS purchase_inquiry (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  inquiry_no VARCHAR(64) NOT NULL,
  apply_id BIGINT,
  title VARCHAR(128),
  status VARCHAR(32) DEFAULT 'DRAFT',
  selected_supplier_id BIGINT,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_inquiry_no (inquiry_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS purchase_inquiry_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  inquiry_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  qty DECIMAL(18,3) DEFAULT 0,
  unit VARCHAR(32) DEFAULT '个',
  tax_rate DECIMAL(5,2) DEFAULT 13.00,
  tax_inclusive_price DECIMAL(18,6) DEFAULT 0,
  tax_exclusive_price DECIMAL(18,6) DEFAULT 0,
  tax_unit_amount DECIMAL(18,6) DEFAULT 0,
  tax_inclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_exclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_amount DECIMAL(18,2) DEFAULT 0,
  supplier_id BIGINT,
  quote_remark VARCHAR(255),
  is_selected TINYINT DEFAULT 0,
  KEY idx_inquiry (inquiry_id),
  KEY idx_mat (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS transfer_order (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  transfer_no VARCHAR(64) NOT NULL,
  from_warehouse_id BIGINT NOT NULL,
  to_warehouse_id BIGINT NOT NULL,
  operator_id BIGINT,
  transfer_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(32) DEFAULT 'DRAFT',
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_transfer_no (transfer_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS transfer_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  transfer_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  batch_no VARCHAR(64),
  from_location_id BIGINT,
  to_location_id BIGINT,
  qty DECIMAL(18,3) DEFAULT 0,
  actual_qty DECIMAL(18,3) DEFAULT 0,
  tax_incl_price DECIMAL(18,6) DEFAULT 0,
  tax_rate DECIMAL(5,2),
  KEY idx_transfer (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_check (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  check_no VARCHAR(64) NOT NULL,
  warehouse_id BIGINT NOT NULL,
  operator_id BIGINT,
  check_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(32) DEFAULT 'DRAFT',
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_check_no (check_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_check_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  check_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  batch_no VARCHAR(64),
  location_id BIGINT,
  book_qty DECIMAL(18,3) DEFAULT 0,
  actual_qty DECIMAL(18,3) DEFAULT 0,
  diff_qty DECIMAL(18,3) DEFAULT 0,
  tax_incl_price DECIMAL(18,6) DEFAULT 0,
  remark VARCHAR(255),
  KEY idx_check (check_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS borrow_order (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  borrow_no VARCHAR(64) NOT NULL,
  apply_user_id BIGINT NOT NULL,
  dept_id BIGINT,
  borrow_type VARCHAR(32) DEFAULT 'TEMP',
  project_name VARCHAR(128),
  borrow_date DATE,
  expect_return_date DATE,
  actual_return_date DATE,
  purpose VARCHAR(255),
  status VARCHAR(32) DEFAULT 'DRAFT',
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_borrow_no (borrow_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS borrow_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  borrow_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  qty DECIMAL(18,3) NOT NULL,
  lent_qty DECIMAL(18,3) DEFAULT 0,
  returned_qty DECIMAL(18,3) DEFAULT 0,
  unit VARCHAR(32) DEFAULT '个',
  remark VARCHAR(255),
  KEY idx_borrow (borrow_id),
  KEY idx_mat (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS borrow_return_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  borrow_id BIGINT NOT NULL,
  borrow_item_id BIGINT,
  material_id BIGINT NOT NULL,
  return_qty DECIMAL(18,3) NOT NULL,
  condition_status VARCHAR(32) DEFAULT 'GOOD',
  operator_id BIGINT,
  remark VARCHAR(255),
  return_time DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_borrow (borrow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scrap_order (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  scrap_no VARCHAR(64) NOT NULL,
  apply_user_id BIGINT NOT NULL,
  dept_id BIGINT,
  scrap_reason VARCHAR(255),
  scrap_date DATE,
  status VARCHAR(32) DEFAULT 'DRAFT',
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_scrap_no (scrap_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scrap_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  scrap_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  qty DECIMAL(18,3) NOT NULL,
  confirmed_qty DECIMAL(18,3) DEFAULT 0,
  unit VARCHAR(32) DEFAULT '个',
  reason VARCHAR(255),
  remark VARCHAR(255),
  KEY idx_scrap (scrap_id),
  KEY idx_mat (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS supplier_import_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  data_type VARCHAR(32) NOT NULL,
  file_name VARCHAR(255),
  total_count INT DEFAULT 0,
  success_count INT DEFAULT 0,
  fail_count INT DEFAULT 0,
  operator_id BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_type (data_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
