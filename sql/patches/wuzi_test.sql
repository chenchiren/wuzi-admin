CREATE DATABASE IF NOT EXISTS wuzi_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wuzi_test;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_dept;
DROP TABLE IF EXISTS sys_operation_log;
DROP TABLE IF EXISTS tax_rate_dict;
DROP TABLE IF EXISTS tax_price_adjust_log;
DROP TABLE IF EXISTS material_category;
DROP TABLE IF EXISTS material;
DROP TABLE IF EXISTS supplier_product_price;
DROP TABLE IF EXISTS supplier_product;
DROP TABLE IF EXISTS supplier;
DROP TABLE IF EXISTS stock_record;
DROP TABLE IF EXISTS stock_serial;
DROP TABLE IF EXISTS stock_batch;
DROP TABLE IF EXISTS stock;
DROP TABLE IF EXISTS warehouse_location;
DROP TABLE IF EXISTS warehouse;
DROP TABLE IF EXISTS purchase_order_item;
DROP TABLE IF EXISTS purchase_order;
DROP TABLE IF EXISTS purchase_apply_item;
DROP TABLE IF EXISTS purchase_apply;
DROP TABLE IF EXISTS purchase_arrival_item;
DROP TABLE IF EXISTS purchase_arrival;
DROP TABLE IF EXISTS purchase_return_item;
DROP TABLE IF EXISTS purchase_return;
DROP TABLE IF EXISTS inbound_order_item;
DROP TABLE IF EXISTS inbound_order;
DROP TABLE IF EXISTS outbound_order_item;
DROP TABLE IF EXISTS outbound_order;
DROP TABLE IF EXISTS material_use_apply_item;
DROP TABLE IF EXISTS material_use_apply;
DROP TABLE IF EXISTS approval_record;
DROP TABLE IF EXISTS approval_instance;
DROP TABLE IF EXISTS trace_scan_log;
DROP TABLE IF EXISTS trace_qr_print_log;
DROP TABLE IF EXISTS trace_qr_code;

CREATE TABLE sys_dept (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT DEFAULT 0,
  dept_code VARCHAR(64) NOT NULL,
  dept_name VARCHAR(128) NOT NULL,
  sort_no INT DEFAULT 0,
  status TINYINT DEFAULT 1,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dept_code (dept_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(128) NOT NULL,
  real_name VARCHAR(64) NOT NULL,
  phone VARCHAR(32),
  email VARCHAR(128),
  dept_id BIGINT,
  company_id BIGINT,
  status TINYINT DEFAULT 1,
  last_login_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_permission (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT DEFAULT 0,
  perm_code VARCHAR(128) NOT NULL,
  perm_name VARCHAR(128) NOT NULL,
  perm_type VARCHAR(32) DEFAULT 'MENU',
  path VARCHAR(255),
  sort_no INT DEFAULT 0,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_perm_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_role_permission (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  perm_id BIGINT NOT NULL,
  UNIQUE KEY uk_role_perm (role_id, perm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_operation_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT,
  operator_name VARCHAR(64),
  module VARCHAR(64),
  action VARCHAR(64),
  request_uri VARCHAR(512),
  params TEXT,
  result VARCHAR(32),
  ip VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_op_time (operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tax_rate_dict (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tax_code VARCHAR(32) NOT NULL,
  tax_name VARCHAR(64) NOT NULL,
  tax_rate DECIMAL(5,2) NOT NULL,
  tax_category VARCHAR(32) DEFAULT 'GOODS',
  is_default TINYINT DEFAULT 0,
  is_enabled TINYINT DEFAULT 1,
  effective_from DATE,
  effective_to DATE,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tax_code (tax_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tax_price_adjust_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  biz_type VARCHAR(32) NOT NULL,
  biz_id BIGINT NOT NULL,
  field_name VARCHAR(64) NOT NULL,
  old_value VARCHAR(64),
  new_value VARCHAR(64),
  adjust_type VARCHAR(32) NOT NULL,
  operator_id BIGINT,
  reason VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE material_category (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT DEFAULT 0,
  category_code VARCHAR(64) NOT NULL,
  category_name VARCHAR(64) NOT NULL,
  sort_no INT DEFAULT 0,
  status TINYINT DEFAULT 1,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cat_code (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE material (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(128) NOT NULL,
  category_id BIGINT,
  spec VARCHAR(128),
  brand VARCHAR(64),
  unit VARCHAR(32) DEFAULT '个',
  barcode VARCHAR(64),
  batch_managed TINYINT DEFAULT 0,
  serial_managed TINYINT DEFAULT 0,
  qr_granularity VARCHAR(16) DEFAULT 'SKU',
  shelf_life_days INT,
  safety_stock DECIMAL(18,3) DEFAULT 0,
  max_stock DECIMAL(18,3) DEFAULT 0,
  ref_price DECIMAL(18,6) DEFAULT 0,
  ref_tax_rate DECIMAL(5,2) DEFAULT 13.00,
  status TINYINT DEFAULT 1,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mat_code (material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supplier (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  supplier_code VARCHAR(64) NOT NULL,
  supplier_name VARCHAR(128) NOT NULL,
  credit_code VARCHAR(64),
  supplier_type VARCHAR(32),
  contact_name VARCHAR(64),
  contact_phone VARCHAR(32),
  email VARCHAR(128),
  address VARCHAR(255),
  bank_name VARCHAR(128),
  bank_account VARCHAR(64),
  default_tax_code VARCHAR(32),
  default_tax_rate DECIMAL(5,2) DEFAULT 13.00,
  tax_qualification VARCHAR(32) DEFAULT 'GENERAL',
  invoice_type VARCHAR(32) DEFAULT 'VAT_SPECIAL',
  settle_price_incl TINYINT DEFAULT 1,
  settle_type VARCHAR(32),
  pay_type VARCHAR(32),
  coop_status VARCHAR(32) DEFAULT 'COOPERATING',
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sup_code (supplier_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supplier_product (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  supplier_id BIGINT NOT NULL,
  material_id BIGINT,
  supplier_product_code VARCHAR(64) NOT NULL,
  supplier_sku VARCHAR(64),
  product_name VARCHAR(128) NOT NULL,
  spec VARCHAR(128),
  brand VARCHAR(64),
  unit VARCHAR(32) DEFAULT '个',
  tax_code VARCHAR(32),
  tax_rate DECIMAL(5,2) DEFAULT 13.00,
  tax_inclusive_price DECIMAL(18,6) DEFAULT 0,
  tax_exclusive_price DECIMAL(18,6) DEFAULT 0,
  tax_unit_amount DECIMAL(18,6) DEFAULT 0,
  price_tax_mode VARCHAR(16) DEFAULT 'INCLUSIVE',
  moq DECIMAL(18,3) DEFAULT 1,
  min_qty DECIMAL(18,3) DEFAULT 1,
  lead_time_days INT,
  pack_spec VARCHAR(64),
  shelf_life_days INT,
  currency CHAR(3) DEFAULT 'CNY',
  status TINYINT DEFAULT 1,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sp_code (supplier_id, supplier_product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supplier_product_price (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  supplier_product_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  tax_code VARCHAR(32),
  tax_rate DECIMAL(5,2) DEFAULT 13.00,
  tax_inclusive_price DECIMAL(18,6) NOT NULL,
  tax_exclusive_price DECIMAL(18,6) NOT NULL,
  tax_unit_amount DECIMAL(18,6) DEFAULT 0,
  currency CHAR(3) DEFAULT 'CNY',
  effective_from DATE NOT NULL,
  effective_to DATE,
  source_type VARCHAR(32) DEFAULT 'MANUAL',
  source_id BIGINT,
  cur_uk BIGINT GENERATED ALWAYS AS (IF(effective_to IS NULL, supplier_product_id, NULL)) STORED,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cur_price (cur_uk),
  UNIQUE KEY uk_sp_from (supplier_product_id, effective_from),
  KEY idx_sp_id (supplier_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE warehouse (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  warehouse_code VARCHAR(64) NOT NULL,
  warehouse_name VARCHAR(128) NOT NULL,
  warehouse_type VARCHAR(32),
  address VARCHAR(255),
  manager_id BIGINT,
  phone VARCHAR(32),
  status TINYINT DEFAULT 1,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wh_code (warehouse_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE warehouse_location (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  warehouse_id BIGINT NOT NULL,
  location_code VARCHAR(64) NOT NULL,
  location_name VARCHAR(128),
  area VARCHAR(32),
  status TINYINT DEFAULT 1,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_loc (warehouse_id, location_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  warehouse_id BIGINT NOT NULL,
  location_id BIGINT,
  material_id BIGINT NOT NULL,
  batch_no VARCHAR(64),
  qty DECIMAL(18,3) DEFAULT 0,
  frozen_qty DECIMAL(18,3) DEFAULT 0,
  cost_tax_incl DECIMAL(18,2) DEFAULT 0,
  cost_tax_excl DECIMAL(18,2) DEFAULT 0,
  last_tax_rate DECIMAL(5,2),
  last_tax_incl_price DECIMAL(18,6),
  version INT DEFAULT 0,
  is_deleted TINYINT DEFAULT 0,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_stock (warehouse_id, material_id, batch_no),
  KEY idx_mat (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock_batch (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_no VARCHAR(64) NOT NULL,
  material_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  supplier_id BIGINT,
  purchase_order_id BIGINT,
  inbound_order_id BIGINT,
  production_date DATE,
  expire_date DATE,
  tax_code VARCHAR(32),
  tax_rate DECIMAL(5,2),
  tax_inclusive_price DECIMAL(18,6),
  tax_exclusive_price DECIMAL(18,6),
  cost_tax_incl DECIMAL(18,2) DEFAULT 0,
  cost_tax_excl DECIMAL(18,2) DEFAULT 0,
  qty DECIMAL(18,3) DEFAULT 0,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_batch (batch_no, warehouse_id, material_id),
  KEY idx_batch_no (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock_serial (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  serial_no VARCHAR(64) NOT NULL,
  material_id BIGINT NOT NULL,
  warehouse_id BIGINT,
  location_id BIGINT,
  batch_no VARCHAR(64),
  status VARCHAR(32) DEFAULT 'IN_STOCK',
  user_id BIGINT,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_serial (serial_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  biz_type VARCHAR(32) NOT NULL,
  biz_no VARCHAR(64) NOT NULL,
  biz_id BIGINT,
  warehouse_id BIGINT NOT NULL,
  location_id BIGINT,
  material_id BIGINT NOT NULL,
  batch_no VARCHAR(64),
  serial_no VARCHAR(64),
  change_qty DECIMAL(18,3) NOT NULL,
  before_qty DECIMAL(18,3) NOT NULL,
  after_qty DECIMAL(18,3) NOT NULL,
  amount_sign TINYINT DEFAULT 1,
  tax_incl_amount DECIMAL(18,2) DEFAULT 0,
  operator_id BIGINT,
  remark VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_biz (biz_type, biz_no),
  KEY idx_mat (material_id),
  KEY idx_wh_time (warehouse_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_apply (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  apply_no VARCHAR(64) NOT NULL,
  apply_user_id BIGINT NOT NULL,
  dept_id BIGINT,
  apply_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  need_date DATE,
  urgency VARCHAR(16) DEFAULT 'NORMAL',
  purpose VARCHAR(255),
  status VARCHAR(32) DEFAULT 'DRAFT',
  total_tax_incl_amount DECIMAL(18,2) DEFAULT 0,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_apply_no (apply_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_apply_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  apply_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  qty DECIMAL(18,3) NOT NULL,
  unit VARCHAR(32) DEFAULT '个',
  ref_price DECIMAL(18,6) DEFAULT 0,
  tax_rate DECIMAL(5,2) DEFAULT 13.00,
  tax_incl_amount DECIMAL(18,2) DEFAULT 0,
  remark VARCHAR(255),
  KEY idx_apply (apply_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_order (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  supplier_id BIGINT NOT NULL,
  apply_id BIGINT,
  purchase_user_id BIGINT,
  dept_id BIGINT,
  order_date DATE,
  expect_arrival_date DATE,
  currency CHAR(3) DEFAULT 'CNY',
  default_tax_code VARCHAR(32),
  default_tax_rate DECIMAL(5,2),
  tax_inclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_exclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_amount DECIMAL(18,2) DEFAULT 0,
  tax_rate_summary VARCHAR(255),
  pay_type VARCHAR(32),
  settle_type VARCHAR(32),
  status VARCHAR(32) DEFAULT 'DRAFT',
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  supplier_product_id BIGINT,
  tax_code VARCHAR(32),
  tax_rate DECIMAL(5,2) DEFAULT 13.00,
  tax_inclusive_price DECIMAL(18,6) DEFAULT 0,
  tax_exclusive_price DECIMAL(18,6) DEFAULT 0,
  tax_unit_amount DECIMAL(18,6) DEFAULT 0,
  qty DECIMAL(18,3) DEFAULT 0,
  received_qty DECIMAL(18,3) DEFAULT 0,
  qualified_qty DECIMAL(18,3) DEFAULT 0,
  tax_inclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_exclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_amount DECIMAL(18,2) DEFAULT 0,
  currency CHAR(3) DEFAULT 'CNY',
  unit VARCHAR(32) DEFAULT '个',
  price_source VARCHAR(32),
  price_source_id BIGINT,
  expected_arrival_date DATE,
  tax_calc_flag VARCHAR(16) DEFAULT 'AUTO',
  version INT DEFAULT 0,
  KEY idx_po (order_id),
  KEY idx_mat (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_arrival (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  arrival_no VARCHAR(64) NOT NULL,
  order_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  arrival_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  operator_id BIGINT,
  transport_info VARCHAR(255),
  status VARCHAR(32) DEFAULT 'DRAFT',
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_arrival_no (arrival_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_arrival_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  arrival_id BIGINT NOT NULL,
  purchase_order_item_id BIGINT NOT NULL DEFAULT 0,
  material_id BIGINT NOT NULL,
  tax_code VARCHAR(32),
  tax_rate DECIMAL(5,2) DEFAULT 13.00,
  tax_inclusive_price DECIMAL(18,6) DEFAULT 0,
  tax_exclusive_price DECIMAL(18,6) DEFAULT 0,
  arrive_qty DECIMAL(18,3) DEFAULT 0,
  tax_inclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_exclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_amount DECIMAL(18,2) DEFAULT 0,
  KEY idx_poi (purchase_order_item_id),
  KEY idx_arrival (arrival_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_return (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  return_no VARCHAR(64) NOT NULL,
  order_id BIGINT,
  supplier_id BIGINT NOT NULL,
  reason VARCHAR(255),
  status VARCHAR(32) DEFAULT 'DRAFT',
  amount_sign TINYINT DEFAULT -1,
  tax_incl_amount DECIMAL(18,2) DEFAULT 0,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_return_no (return_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_return_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  return_id BIGINT NOT NULL,
  purchase_order_item_id BIGINT NOT NULL DEFAULT 0,
  inbound_order_item_id BIGINT,
  material_id BIGINT NOT NULL,
  tax_code VARCHAR(32),
  tax_rate DECIMAL(5,2) DEFAULT 13.00,
  tax_inclusive_price DECIMAL(18,6) DEFAULT 0,
  tax_exclusive_price DECIMAL(18,6) DEFAULT 0,
  return_qty DECIMAL(18,3) DEFAULT 0,
  tax_inclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_exclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_amount DECIMAL(18,2) DEFAULT 0,
  amount_sign TINYINT DEFAULT -1,
  KEY idx_poi (purchase_order_item_id),
  KEY idx_return (return_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inbound_order (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  inbound_no VARCHAR(64) NOT NULL,
  inbound_type VARCHAR(32) DEFAULT 'PURCHASE',
  warehouse_id BIGINT NOT NULL,
  supplier_id BIGINT,
  source_no VARCHAR(64),
  source_id BIGINT,
  operator_id BIGINT,
  inbound_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(32) DEFAULT 'DRAFT',
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_inbound_no (inbound_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inbound_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  inbound_id BIGINT NOT NULL,
  purchase_order_item_id BIGINT NOT NULL DEFAULT 0,
  purchase_arrival_item_id BIGINT,
  material_id BIGINT NOT NULL,
  location_id BIGINT,
  tax_code VARCHAR(32),
  tax_rate DECIMAL(5,2) DEFAULT 13.00,
  tax_inclusive_price DECIMAL(18,6) DEFAULT 0,
  tax_exclusive_price DECIMAL(18,6) DEFAULT 0,
  in_qty DECIMAL(18,3) DEFAULT 0,
  cost_tax_incl DECIMAL(18,2) DEFAULT 0,
  cost_tax_excl DECIMAL(18,2) DEFAULT 0,
  tax_amount DECIMAL(18,2) DEFAULT 0,
  batch_no VARCHAR(64),
  production_date DATE,
  expire_date DATE,
  KEY idx_poi (purchase_order_item_id),
  KEY idx_inbound (inbound_id),
  KEY idx_batch_no (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outbound_order (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  outbound_no VARCHAR(64) NOT NULL,
  outbound_type VARCHAR(32) DEFAULT 'USE',
  warehouse_id BIGINT NOT NULL,
  apply_id BIGINT,
  dept_id BIGINT,
  user_id BIGINT,
  operator_id BIGINT,
  outbound_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(32) DEFAULT 'DRAFT',
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_outbound_no (outbound_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outbound_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  outbound_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  batch_no VARCHAR(64),
  location_id BIGINT,
  qty DECIMAL(18,3) DEFAULT 0,
  actual_qty DECIMAL(18,3) DEFAULT 0,
  tax_incl_price DECIMAL(18,6) DEFAULT 0,
  tax_rate DECIMAL(5,2),
  KEY idx_outbound (outbound_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE material_use_apply (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  apply_no VARCHAR(64) NOT NULL,
  apply_user_id BIGINT NOT NULL,
  dept_id BIGINT,
  use_type VARCHAR(32) DEFAULT 'DAILY',
  project_name VARCHAR(128),
  need_date DATE,
  purpose VARCHAR(255),
  status VARCHAR(32) DEFAULT 'DRAFT',
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_use_no (apply_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE material_use_apply_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  apply_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  qty DECIMAL(18,3) NOT NULL,
  unit VARCHAR(32) DEFAULT '个',
  remark VARCHAR(255),
  KEY idx_apply (apply_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_instance (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  biz_type VARCHAR(32) NOT NULL,
  biz_id BIGINT NOT NULL,
  biz_no VARCHAR(64),
  applicant_id BIGINT NOT NULL,
  title VARCHAR(255),
  status VARCHAR(32) DEFAULT 'PENDING',
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_biz (biz_type, biz_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE approval_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  instance_id BIGINT NOT NULL,
  node_name VARCHAR(64),
  approver_id BIGINT NOT NULL,
  action VARCHAR(16) NOT NULL,
  comment VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trace_qr_code (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  qr_code VARCHAR(64) NOT NULL,
  qr_type VARCHAR(16) NOT NULL,
  qr_content VARCHAR(255) NOT NULL,
  trace_token VARCHAR(64) NOT NULL,
  content_mode VARCHAR(16) DEFAULT 'TOKEN',
  material_id BIGINT NOT NULL DEFAULT 0,
  batch_no VARCHAR(64),
  batch_id BIGINT,
  serial_no VARCHAR(64),
  serial_id BIGINT,
  material_code VARCHAR(64),
  material_name VARCHAR(128),
  spec VARCHAR(128),
  unit VARCHAR(32),
  supplier_id BIGINT,
  purchase_order_id BIGINT,
  inbound_order_id BIGINT,
  status VARCHAR(32) DEFAULT 'CREATED',
  qr_granularity VARCHAR(16) DEFAULT 'SKU',
  current_warehouse_id BIGINT,
  current_location_id BIGINT,
  print_count INT DEFAULT 0,
  scan_count INT DEFAULT 0,
  first_scan_at DATETIME,
  last_scan_at DATETIME,
  activated_at DATETIME,
  retired_at DATETIME,
  expire_date DATE,
  label_version INT DEFAULT 1,
  is_enabled TINYINT DEFAULT 1,
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  sku_uk BIGINT GENERATED ALWAYS AS (CASE WHEN qr_type='SKU' AND material_id>0 THEN material_id ELSE NULL END) STORED,
  batch_uk VARCHAR(160) GENERATED ALWAYS AS (CASE WHEN qr_type='BATCH' AND batch_no IS NOT NULL THEN CONCAT(material_id,'#',batch_no) ELSE NULL END) STORED,
  serial_uk VARCHAR(80) GENERATED ALWAYS AS (CASE WHEN qr_type='SERIAL' AND serial_no IS NOT NULL THEN serial_no ELSE NULL END) STORED,
  UNIQUE KEY uk_qr_code (qr_code),
  UNIQUE KEY uk_trace_token (trace_token),
  UNIQUE KEY uk_sku (sku_uk),
  UNIQUE KEY uk_batch (batch_uk),
  UNIQUE KEY uk_serial (serial_uk),
  KEY idx_material (material_id),
  KEY idx_batch_no (batch_no),
  KEY idx_status_wh (status, current_warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trace_qr_print_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  qr_id BIGINT NOT NULL,
  qr_code VARCHAR(64) NOT NULL,
  print_batch_no VARCHAR(64),
  print_type VARCHAR(32) DEFAULT 'FIRST',
  reprint_reason VARCHAR(255),
  template_code VARCHAR(32),
  template_size VARCHAR(16),
  printer_name VARCHAR(64),
  copy_count INT DEFAULT 1,
  operator_id BIGINT,
  print_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  biz_type VARCHAR(32),
  biz_id BIGINT,
  KEY idx_qr (qr_id),
  KEY idx_print_batch (print_batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trace_scan_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  qr_id BIGINT,
  qr_code VARCHAR(64) NOT NULL,
  scan_type VARCHAR(32) DEFAULT 'QUERY',
  biz_type VARCHAR(32),
  biz_id BIGINT,
  operator_id BIGINT,
  operator_name VARCHAR(64),
  channel VARCHAR(16) DEFAULT 'PC',
  warehouse_id BIGINT,
  client_ip VARCHAR(64),
  result VARCHAR(16) DEFAULT 'SUCCESS',
  fail_reason VARCHAR(128),
  scan_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id, scan_time),
  KEY idx_qr_time (qr_id, scan_time),
  KEY idx_code_time (qr_code, scan_time),
  KEY idx_result (result, scan_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO tax_rate_dict (tax_code, tax_name, tax_rate, tax_category, is_default) VALUES
('TAX-00','零税率',0.00,'OTHER',0),
('TAX-01','简易征收1%',1.00,'OTHER',0),
('TAX-03','简易征收3%',3.00,'OTHER',0),
('TAX-06','服务6%',6.00,'SERVICE',0),
('TAX-09','运输建筑9%',9.00,'TRANSPORT',0),
('TAX-13','货物13%',13.00,'GOODS',1)
ON DUPLICATE KEY UPDATE tax_name=VALUES(tax_name);

INSERT INTO sys_dept (id, parent_id, dept_code, dept_name) VALUES
(1,0,'ROOT','总公司'),(2,1,'WH','仓储部'),(3,1,'PUR','采购部'),(4,1,'RD','研发部'),(5,1,'FIN','财务部')
ON DUPLICATE KEY UPDATE dept_name=VALUES(dept_name);

INSERT INTO sys_user (id, username, password, real_name, dept_id) VALUES
(1,'admin','$2a$10$EqKcp1WFfvXhqhS6xFIeV.0n9nJq7x3Yq8mH8Yq8mH8Yq8mH8Yq8m','系统管理员',1),
(2,'wh01','$2a$10$EqKcp1WFfvXhqhS6xFIeV.0n9nJq7x3Yq8mH8Yq8mH8Yq8mH8Yq8m','张仓管',2),
(3,'pur01','$2a$10$EqKcp1WFfvXhqhS6xFIeV.0n9nJq7x3Yq8mH8Yq8mH8Yq8mH8Yq8m','李采购',3),
(4,'rd01','$2a$10$EqKcp1WFfvXhqhS6xFIeV.0n9nJq7x3Yq8mH8Yq8mH8Yq8mH8Yq8m','王研发',4)
ON DUPLICATE KEY UPDATE real_name=VALUES(real_name);

INSERT INTO sys_role (id, role_code, role_name) VALUES
(1,'ADMIN','系统管理员'),(2,'WAREHOUSE','仓库管理员'),(3,'PURCHASER','采购员'),(4,'EMPLOYEE','普通员工'),(5,'FINANCE','财务人员')
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1,1),(2,2),(3,3),(4,4)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT INTO material_category (id, parent_id, category_code, category_name) VALUES
(1,0,'OFFICE','办公用品'),(2,1,'PAPER','纸张'),(3,1,'PEN','文具'),(4,0,'PROD','生产物资'),(5,0,'PPE','劳保用品')
ON DUPLICATE KEY UPDATE category_name=VALUES(category_name);

INSERT INTO material (id, material_code, material_name, category_id, spec, brand, unit, batch_managed, qr_granularity, ref_price, ref_tax_rate) VALUES
(1,'A4-001','A4打印纸',2,'70g','XX','包',1,'BATCH',22.00,13.00),
(2,'PEN-001','黑色签字笔',3,'0.5mm','XX','盒',0,'SKU',18.00,13.00),
(3,'TNR-001','硒鼓',3,'CF218A','XX','个',0,'SKU',180.00,13.00)
ON DUPLICATE KEY UPDATE material_name=VALUES(material_name);

INSERT INTO supplier (id, supplier_code, supplier_name, contact_name, contact_phone, default_tax_rate, coop_status) VALUES
(1,'GYS001','XX办公用品有限公司','张三','13800000001',13.00,'COOPERATING'),
(2,'GYS002','XX商贸有限公司','李四','13800000002',13.00,'COOPERATING')
ON DUPLICATE KEY UPDATE supplier_name=VALUES(supplier_name);

INSERT INTO supplier_product (id, supplier_id, material_id, supplier_product_code, product_name, spec, unit, tax_rate, tax_inclusive_price, tax_exclusive_price, tax_unit_amount) VALUES
(1,1,1,'SP001','A4打印纸','70g','包',13.00,22.000000,19.469027,2.530973),
(2,1,2,'SP002','黑色签字笔','0.5mm','盒',13.00,18.000000,15.929204,2.070796),
(3,2,1,'SP001','A4打印纸','70g','包',13.00,21.500000,19.026549,2.473451)
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

INSERT INTO warehouse (id, warehouse_code, warehouse_name, warehouse_type, manager_id) VALUES
(1,'WH01','主仓库','NORMAL',2),(2,'WH02','设备仓','EQUIPMENT',2)
ON DUPLICATE KEY UPDATE warehouse_name=VALUES(warehouse_name);

INSERT INTO warehouse_location (id, warehouse_id, location_code, location_name, area) VALUES
(1,1,'A01','A区01','A'),(2,1,'A02','A区02','A'),(3,1,'B01','B区01','B')
ON DUPLICATE KEY UPDATE location_name=VALUES(location_name);

INSERT INTO trace_qr_code (qr_code, qr_type, qr_content, trace_token, material_id, batch_no, material_code, material_name, qr_granularity, status) VALUES
('SKU-MAT-1','SKU','MAT:A4-001','demo-token-a4-sku-0001',1,NULL,'A4-001','A4打印纸','SKU','ACTIVE'),
('BATCH-MAT-1-B20260315','BATCH','MAT:A4-001|BATCH:B20260315','demo-token-a4-batch-0001',1,'B20260315','A4-001','A4打印纸','BATCH','ACTIVE')
ON DUPLICATE KEY UPDATE status=VALUES(status);

INSERT INTO sys_role (id, role_code, role_name) VALUES
(1,'ADMIN','系统管理员'),(2,'WAREHOUSE','仓库管理员'),(3,'PURCHASER','采购员'),(4,'EMPLOYEE','普通员工'),(5,'FINANCE','财务人员')
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1,1),(2,2),(3,3),(4,4)
ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);

INSERT INTO material_category (id, parent_id, category_code, category_name) VALUES
(1,0,'OFFICE','办公用品'),(2,1,'PAPER','纸张'),(3,1,'PEN','文具'),(4,0,'PROD','生产物资'),(5,0,'PPE','劳保用品')
ON DUPLICATE KEY UPDATE category_name=VALUES(category_name);

INSERT INTO material (id, material_code, material_name, category_id, spec, brand, unit, batch_managed, qr_granularity, ref_price, ref_tax_rate) VALUES
(1,'A4-001','A4打印纸',2,'70g','XX','包',1,'BATCH',22.00,13.00),
(2,'PEN-001','黑色签字笔',3,'0.5mm','XX','盒',0,'SKU',18.00,13.00),
(3,'TNR-001','硒鼓',3,'CF218A','XX','个',0,'SKU',180.00,13.00)
ON DUPLICATE KEY UPDATE material_name=VALUES(material_name);

INSERT INTO supplier (id, supplier_code, supplier_name, contact_name, contact_phone, default_tax_rate, coop_status) VALUES
(1,'GYS001','XX办公用品有限公司','张三','13800000001',13.00,'COOPERATING'),
(2,'GYS002','XX商贸有限公司','李四','13800000002',13.00,'COOPERATING')
ON DUPLICATE KEY UPDATE supplier_name=VALUES(supplier_name);

INSERT INTO supplier_product (id, supplier_id, material_id, supplier_product_code, product_name, spec, unit, tax_rate, tax_inclusive_price, tax_exclusive_price, tax_unit_amount) VALUES
(1,1,1,'SP001','A4打印纸','70g','包',13.00,22.000000,19.469027,2.530973),
(2,1,2,'SP002','黑色签字笔','0.5mm','盒',13.00,18.000000,15.929204,2.070796),
(3,2,1,'SP001','A4打印纸','70g','包',13.00,21.500000,19.026549,2.473451)
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name);

INSERT INTO warehouse (id, warehouse_code, warehouse_name, warehouse_type, manager_id) VALUES
(1,'WH01','主仓库','NORMAL',2),(2,'WH02','设备仓','EQUIPMENT',2)
ON DUPLICATE KEY UPDATE warehouse_name=VALUES(warehouse_name);

INSERT INTO warehouse_location (id, warehouse_id, location_code, location_name, area) VALUES
(1,1,'A01','A区01','A'),(2,1,'A02','A区02','A'),(3,1,'B01','B区01','B')
ON DUPLICATE KEY UPDATE location_name=VALUES(location_name);

INSERT INTO trace_qr_code (qr_code, qr_type, qr_content, trace_token, material_id, batch_no, material_code, material_name, qr_granularity, status) VALUES
('SKU-MAT-1','SKU','MAT:A4-001','demo-token-a4-sku-0001',1,NULL,'A4-001','A4打印纸','SKU','ACTIVE'),
('BATCH-MAT-1-B20260315','BATCH','MAT:A4-001|BATCH:B20260315','demo-token-a4-batch-0001',1,'B20260315','A4-001','A4打印纸','BATCH','ACTIVE')
ON DUPLICATE KEY UPDATE status=VALUES(status);
