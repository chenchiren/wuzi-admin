-- =====================================================================
-- 物资全链路管理系统 · 统一初始化 SQL（结构与数据）
-- 本文件 = 原 wuzi_test.sql（建库/建表/种子）+ 全部 27 个增量补丁，按序合并。
-- 由来：初始化此前需「主库 + patch_* 按序执行」，现统一为本文件一次执行。
--   历史基线与增量补丁归档于 sql/patches/（已有旧库按需增量升级时使用）。
-- 基线：85 张业务表 / 435 条语句 / 27 个补丁，UTF-8 无 BOM，单文件导入零报错（MySQL 8.0 实测）。
-- 用法：
--   PowerShell: .\sql\run-java-utf8.ps1 init .\sql\wuzi_full.sql
--   或 mysql --default-character-set=utf8mb4 < sql/wuzi_full.sql
-- 特性：
--   1. 可重复执行：主库段 DROP+CREATE；补丁段 information_schema 幂等判断；种子 ON DUPLICATE KEY。
--   2. 补丁效果已含：字典/审批/预算合同付款/单位项目/标签联系人/询价审批/导入回滚/
--      plan 关联/询价关联/领用发料仓库/智能助手/物资别名等全部 27 项。
--   3. 全链路演示数据不在此文件，另行执行 .\sql\run-java-utf8.ps1 seed（SeedDemoChain）。
--   4. 编码：UTF-8 无 BOM；导入工具见 run-java-utf8.ps1 / InitDb.java。
-- 合并顺序（与 README 补丁表一致）：
--   missing_tables -> indexes -> dict -> dict_v2 -> dict_price_source -> dict_resubmit
--   -> dict_status_labels -> dict_use_and_source -> approval_msg -> approval_flows
--   -> finance -> alert_return -> org_position -> purchase_line_tax -> button_perms
--   -> flow_config_perms -> org_tree_dept_type -> plan_budget_contract_payment
--   -> unit_project_ledger -> label_template_supplier_contact -> inquiry_approval
--   -> import_batch_biz -> apply_plan_link -> order_inquiry_link -> use_issue_warehouse
--   -> ai_assistant -> material_alias
-- =====================================================================

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

-- =====================================================================
-- 增量补丁：patch_missing_tables
-- =====================================================================
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

-- =====================================================================
-- 增量补丁：patch_indexes
-- =====================================================================
USE wuzi_test;
SET NAMES utf8mb4;

-- 列表页常用过滤/排序索引（幂等：重复执行用 DROP+ADD 或 IF 逻辑环境自备）
-- 若已存在同名索引会报错，可先 DROP KEY 再执行

-- 采购申请
ALTER TABLE purchase_apply ADD KEY idx_status_time (status, apply_time);
ALTER TABLE purchase_apply ADD KEY idx_apply_user (apply_user_id);

-- 采购订单
ALTER TABLE purchase_order ADD KEY idx_status_date (status, order_date);
ALTER TABLE purchase_order ADD KEY idx_supplier (supplier_id);

-- 入库/出库
ALTER TABLE inbound_order ADD KEY idx_status_time (status, inbound_time);
ALTER TABLE outbound_order ADD KEY idx_status_time (status, outbound_time);
ALTER TABLE outbound_order ADD KEY idx_wh_status (warehouse_id, status);

-- 领用
ALTER TABLE material_use_apply ADD KEY idx_status_time (status, need_date);
ALTER TABLE material_use_apply ADD KEY idx_apply_user (apply_user_id);

-- 物资/供应商 模糊搜索辅助（LIKE 前缀仍可走索引；中缀依赖扫描但有 status 过滤时更好）
ALTER TABLE material ADD KEY idx_status (status, is_deleted);
ALTER TABLE supplier ADD KEY idx_coop (coop_status, is_deleted);

-- 审批待办
ALTER TABLE approval_instance ADD KEY idx_status_type (status, biz_type);
ALTER TABLE approval_instance ADD KEY idx_applicant (applicant_id, status);

-- 库存
ALTER TABLE stock ADD KEY idx_wh_mat (warehouse_id, material_id);
ALTER TABLE stock ADD KEY idx_qty (qty);

-- 出入库流水
ALTER TABLE stock_record ADD KEY idx_created (created_at);
ALTER TABLE stock_record ADD KEY idx_wh_mat_time (warehouse_id, material_id, created_at);

-- 操作日志
ALTER TABLE sys_operation_log ADD KEY idx_created (created_at);
ALTER TABLE sys_operation_log ADD KEY idx_module_time (module, created_at);

-- 消息

-- 补表（若尚未建过索引）
ALTER TABLE transfer_order ADD KEY idx_status (status);
ALTER TABLE inventory_check ADD KEY idx_status (status);
ALTER TABLE borrow_order ADD KEY idx_status_expect (status, expect_return_date);
ALTER TABLE scrap_order ADD KEY idx_status (status);

-- =====================================================================
-- 增量补丁：patch_dict
-- =====================================================================
USE wuzi_test;
SET NAMES utf8mb4;

-- 字典：人工维护界面中文文案（状态/类型/角色等）
CREATE TABLE IF NOT EXISTS sys_dict (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  dict_type VARCHAR(64) NOT NULL COMMENT '字典类型，如 STATUS / INBOUND_TYPE',
  dict_key VARCHAR(64) NOT NULL COMMENT '原始编码，如 DRAFT',
  dict_label VARCHAR(128) NOT NULL COMMENT '中文显示名',
  sort_no INT DEFAULT 0,
  enabled TINYINT DEFAULT 1,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dict (dict_type, dict_key),
  KEY idx_type (dict_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 状态 STATUS ==========
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('STATUS', 'DRAFT', '草稿', 10),
('STATUS', 'PENDING_APPROVAL', '待审批', 20),
('STATUS', 'SUBMITTED', '待审批', 21),
('STATUS', 'PENDING', '待审批', 22),
('STATUS', 'APPROVED', '已通过', 30),
('STATUS', 'REJECTED', '已驳回', 40),
('STATUS', 'CONFIRMED', '已确认', 50),
('STATUS', 'COMPLETED', '已完成', 60),
('STATUS', 'CANCELLED', '已作废', 70),
('STATUS', 'ISSUED', '已发料', 80),
('STATUS', 'LENT', '已借出', 90),
('STATUS', 'PART_RETURNED', '部分归还', 95),
('STATUS', 'RETURNED', '已归还', 100),
('STATUS', 'OUT_DONE', '已调出', 110),
('STATUS', 'RED_FLUSHED', '已红冲', 120),
('STATUS', 'APPLIED', '已申请', 130),
('STATUS', 'ACCEPTED', '已验收', 140),
('STATUS', 'ACTIVE', '启用/激活', 150),
('STATUS', 'CREATED', '已创建', 160),
('STATUS', 'ENABLED', '启用', 170),
('STATUS', 'DISABLED', '停用', 180),
('STATUS', '1', '启用', 190),
('STATUS', '0', '停用', 191)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- ========== 通用状态（用户/部门/分类，多为数字） ==========
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('NUM_STATUS', '0', '停用', 10),
('NUM_STATUS', '1', '启用', 20),
('NUM_STATUS', 'true', '是', 30),
('NUM_STATUS', 'false', '否', 40)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- ========== 入库类型 ==========
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('INBOUND_TYPE', 'PURCHASE', '采购入库', 10),
('INBOUND_TYPE', 'RETURN', '退货入库', 20),
('INBOUND_TYPE', 'PURCHASE_RETURN', '采购退货入库', 25),
('INBOUND_TYPE', 'TRANSFER', '调拨入库', 30),
('INBOUND_TYPE', 'ADJUST', '盘点入库', 40),
('INBOUND_TYPE', 'SCRAP_RETURN', '报废退回', 50),
('INBOUND_TYPE', 'OTHER', '其他入库', 60)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- ========== 出库类型 ==========
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('OUTBOUND_TYPE', 'USE', '领用出库', 10),
('OUTBOUND_TYPE', 'OTHER', '其他出库', 20),
('OUTBOUND_TYPE', 'TRANSFER', '调拨出库', 30),
('OUTBOUND_TYPE', 'SCRAP', '报废出库', 40),
('OUTBOUND_TYPE', 'SALE', '销售出库', 50),
('OUTBOUND_TYPE', 'RETURN', '退货出库', 60),
('OUTBOUND_TYPE', 'PURCHASE_RETURN', '采购退货出库', 70)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- ========== 二维码类型 / 粒度 ==========
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('QR_TYPE', 'SKU', '品码', 10),
('QR_TYPE', 'BATCH', '批码', 20),
('QR_TYPE', 'SERIAL', '件码', 30),
('QR_GRANULARITY', 'SKU', '品码', 10),
('QR_GRANULARITY', 'BATCH', '批码', 20),
('QR_GRANULARITY', 'SERIAL', '件码', 30)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- ========== 业务类型（审批/库存流水） ==========
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('BIZ_TYPE', 'PURCHASE_APPLY', '采购申请', 10),
('BIZ_TYPE', 'PURCHASE_ORDER', '采购订单', 20),
('BIZ_TYPE', 'PURCHASE_RETURN', '采购退货', 30),
('BIZ_TYPE', 'PURCHASE_INQUIRY', '采购询价', 40),
('BIZ_TYPE', 'PURCHASE_ARRIVAL', '到货验收', 50),
('BIZ_TYPE', 'MATERIAL_USE', '领用申请', 60),
('BIZ_TYPE', 'BORROW', '借用申请', 70),
('BIZ_TYPE', 'SCRAP', '报废申请', 80),
('BIZ_TYPE', 'TRANSFER', '调拨申请', 90),
('BIZ_TYPE', 'RETURN_STORE', '退库申请', 100),
('BIZ_TYPE', 'INVENTORY_CHECK', '盘点申请', 110),
('BIZ_TYPE', 'OUTBOUND', '出库申请', 120),
('BIZ_TYPE', 'USE_ISSUE', '领用发料', 130),
('BIZ_TYPE', 'USE_RETURN', '退库', 140),
('BIZ_TYPE', 'BORROW_LEND', '借出', 150),
('BIZ_TYPE', 'BORROW_RETURN', '归还', 160),
('BIZ_TYPE', 'TRANSFER_OUT', '调拨出库', 170),
('BIZ_TYPE', 'TRANSFER_IN', '调拨入库', 180),
('BIZ_TYPE', 'INBOUND', '入库', 190),
('BIZ_TYPE', 'OUTBOUND_OP', '出库', 191),
('BIZ_TYPE', 'SCRAP_OP', '报废出库', 200),
('BIZ_TYPE', 'CHECK_GAIN', '盘盈', 210),
('BIZ_TYPE', 'CHECK_LOSS', '盘亏', 220),
('BIZ_TYPE', 'IN', '入库', 230),
('BIZ_TYPE', 'OUT', '出库', 240)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- ========== 领用 / 借用 / 紧急度 / 合作状态 ==========
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('USE_TYPE', 'DAILY', '日常', 10),
('USE_TYPE', 'PROJECT', '项目', 20),
('BORROW_TYPE', 'TEMP', '临时', 10),
('BORROW_TYPE', 'LONG', '长期', 20),
('URGENCY', 'NORMAL', '普通', 10),
('URGENCY', 'URGENT', '紧急', 20),
('COOP_STATUS', 'ACTIVE', '合作中', 10),
('COOP_STATUS', 'PAUSED', '暂停', 20),
('COOP_STATUS', 'ENDED', '终止', 30),
('TAX_CATEGORY', 'VAT', '增值税', 10),
('TAX_CATEGORY', 'EXTRA', '附加税', 20),
('TAX_CATEGORY', 'OTHER', '其他', 30),
('PRICE_MODE', 'INCLUSIVE', '含税价', 10),
('PRICE_MODE', 'EXCLUSIVE', '不含税价', 20)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- ========== 角色 ==========
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('ROLE', 'ADMIN', '系统管理员', 10),
('ROLE', 'WAREHOUSE', '仓库管理员', 20),
('ROLE', 'PURCHASER', '采购员', 30),
('ROLE', 'EMPLOYEE', '普通员工', 40),
('ROLE', 'FINANCE', '财务人员', 50),
('ROLE', 'DEPT_MANAGER', '部门负责人', 60)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- ========== 审批动作 ==========
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('APPROVAL_ACTION', 'START', '发起', 10),
('APPROVAL_ACTION', 'APPROVE', '通过', 20),
('APPROVAL_ACTION', 'REJECT', '驳回', 30),
('APPROVAL_ACTION', 'TRANSFER', '转交', 40),
('APPROVAL_ACTION', 'ADD_SIGN', '加签', 50)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- ========== 操作日志 result ==========
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('LOG_RESULT', 'SUCCESS', '成功', 10),
('LOG_RESULT', 'FAIL', '失败', 20),
('LOG_RESULT', 'ERROR', '错误', 30)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================================
-- 增量补丁：patch_dict_v2
-- =====================================================================
USE wuzi_test;
SET NAMES utf8mb4;

-- ============================================================
-- 字典重构：类型表（事件/业务） + 明细表（编码→中文）
-- 调用方式：按 type_code 取「该事件的全部状态/类型列表」
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_dict_type (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  type_code VARCHAR(64) NOT NULL COMMENT '事件编码，如 INBOUND_STATUS',
  type_name VARCHAR(128) NOT NULL COMMENT '中文名，如 入库单状态',
  type_group VARCHAR(32) DEFAULT 'BUSINESS' COMMENT '分组：BUSINESS业务 / BASE基础',
  usage_remark VARCHAR(255) COMMENT '使用说明：哪个页面/事件调用',
  sort_no INT DEFAULT 0,
  enabled TINYINT DEFAULT 1,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_type_code (type_code),
  KEY idx_group (type_group, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 明细：挂在 type_code 下（原 sys_dict.dict_type 即 type_code）
CREATE TABLE IF NOT EXISTS sys_dict (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  dict_type VARCHAR(64) NOT NULL COMMENT '对应 sys_dict_type.type_code',
  dict_key VARCHAR(64) NOT NULL COMMENT '原始编码',
  dict_label VARCHAR(128) NOT NULL COMMENT '中文显示',
  sort_no INT DEFAULT 0,
  enabled TINYINT DEFAULT 1,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dict (dict_type, dict_key),
  KEY idx_type (dict_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 类型（事件）==========
INSERT INTO sys_dict_type (type_code, type_name, type_group, usage_remark, sort_no) VALUES
('INBOUND_STATUS', '入库单状态', 'BUSINESS', '入库列表 / 筛选', 10),
('OUTBOUND_STATUS', '出库单状态', 'BUSINESS', '出库列表 / 筛选', 20),
('INBOUND_TYPE', '入库类型', 'BUSINESS', '入库列表类型列', 30),
('OUTBOUND_TYPE', '出库类型', 'BUSINESS', '出库列表类型列', 40),
('PURCHASE_APPLY_STATUS', '采购申请状态', 'BUSINESS', '采购申请列表', 50),
('PURCHASE_ORDER_STATUS', '采购订单状态', 'BUSINESS', '采购订单列表', 60),
('PURCHASE_RETURN_STATUS', '采购退货状态', 'BUSINESS', '采购退货列表', 70),
('PURCHASE_ARRIVAL_STATUS', '到货单状态', 'BUSINESS', '到货验收列表', 80),
('MATERIAL_USE_STATUS', '领用单状态', 'BUSINESS', '领用列表 / 筛选', 90),
('BORROW_STATUS', '借用单状态', 'BUSINESS', '借用列表 / 筛选', 100),
('SCRAP_STATUS', '报废单状态', 'BUSINESS', '报废列表 / 筛选', 110),
('TRANSFER_STATUS', '调拨单状态', 'BUSINESS', '调拨列表 / 筛选', 120),
('INVENTORY_STATUS', '盘点单状态', 'BUSINESS', '盘点列表 / 筛选', 130),
('RETURN_STORE_STATUS', '退库单状态', 'BUSINESS', '退库列表 / 筛选', 140),
('APPROVAL_STATUS', '审批实例状态', 'BUSINESS', '审批待办列表', 150),
('BIZ_TYPE', '审批/流水业务类型', 'BUSINESS', '审批待办、库存流水', 160),
('APPROVAL_ACTION', '审批动作', 'BUSINESS', '审批记录时间线', 170),
('QR_TYPE', '二维码类型', 'BUSINESS', '二维码溯源、标签打印', 180),
('QR_GRANULARITY', '码粒度', 'BUSINESS', '物资档案', 190),
('QR_STATUS', '二维码状态', 'BUSINESS', '二维码溯源、标签打印', 200),
('USE_TYPE', '领用类型', 'BUSINESS', '领用列表类型列', 210),
('BORROW_TYPE', '借用类型', 'BUSINESS', '借用列表类型列', 220),
('URGENCY', '紧急程度', 'BUSINESS', '采购申请紧急度', 230),
('COOP_STATUS', '供应商合作状态', 'BUSINESS', '供应商列表状态', 240),
('TAX_CATEGORY', '税率分类', 'BUSINESS', '税率字典', 250),
('ROLE', '角色编码', 'BASE', '用户角色、侧栏角色标签', 260),
('NUM_STATUS', '启用/停用', 'BASE', '用户/部门/分类等开关状态', 270),
('LOG_RESULT', '日志结果', 'BUSINESS', '操作日志结果列', 280)
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name),
  type_group = VALUES(type_group),
  usage_remark = VALUES(usage_remark),
  sort_no = VALUES(sort_no);

-- ========== 各事件状态列表（完整）==========
DELETE FROM sys_dict WHERE dict_type IN (
  'INBOUND_STATUS','OUTBOUND_STATUS','PURCHASE_APPLY_STATUS','PURCHASE_ORDER_STATUS',
  'PURCHASE_RETURN_STATUS','PURCHASE_ARRIVAL_STATUS','MATERIAL_USE_STATUS','BORROW_STATUS',
  'SCRAP_STATUS','TRANSFER_STATUS','INVENTORY_STATUS','RETURN_STORE_STATUS','APPROVAL_STATUS','QR_STATUS'
);

INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
-- 入库
('INBOUND_STATUS','DRAFT','草稿',10),
('INBOUND_STATUS','PENDING_APPROVAL','待审批',20),
('INBOUND_STATUS','APPROVED','已通过',30),
('INBOUND_STATUS','CONFIRMED','已确认入库',40),
('INBOUND_STATUS','REJECTED','已驳回',50),
('INBOUND_STATUS','CANCELLED','已作废',60),
-- 出库
('OUTBOUND_STATUS','DRAFT','草稿',10),
('OUTBOUND_STATUS','PENDING_APPROVAL','待审批',20),
('OUTBOUND_STATUS','APPROVED','已通过',30),
('OUTBOUND_STATUS','CONFIRMED','已确认出库',40),
('OUTBOUND_STATUS','REJECTED','已驳回',50),
('OUTBOUND_STATUS','RED_FLUSHED','已红冲',60),
-- 采购申请
('PURCHASE_APPLY_STATUS','DRAFT','草稿',10),
('PURCHASE_APPLY_STATUS','SUBMITTED','待审批',20),
('PURCHASE_APPLY_STATUS','PENDING_APPROVAL','待审批',21),
('PURCHASE_APPLY_STATUS','APPROVED','已通过',30),
('PURCHASE_APPLY_STATUS','REJECTED','已驳回',40),
('PURCHASE_APPLY_STATUS','CANCELLED','已作废',50),
('PURCHASE_APPLY_STATUS','COMPLETED','已完成',60),
-- 采购订单
('PURCHASE_ORDER_STATUS','DRAFT','草稿',10),
('PURCHASE_ORDER_STATUS','PENDING_APPROVAL','待审批',20),
('PURCHASE_ORDER_STATUS','APPROVED','已通过',30),
('PURCHASE_ORDER_STATUS','REJECTED','已驳回',40),
('PURCHASE_ORDER_STATUS','CANCELLED','已作废',50),
('PURCHASE_ORDER_STATUS','PART_RECEIVED','部分收货',60),
('PURCHASE_ORDER_STATUS','RECEIVED','已收货',70),
-- 采购退货
('PURCHASE_RETURN_STATUS','DRAFT','草稿',10),
('PURCHASE_RETURN_STATUS','PENDING_APPROVAL','待审批',20),
('PURCHASE_RETURN_STATUS','APPROVED','已通过',30),
('PURCHASE_RETURN_STATUS','REJECTED','已驳回',40),
('PURCHASE_RETURN_STATUS','COMPLETED','已完成',50),
('PURCHASE_RETURN_STATUS','CANCELLED','已作废',60),
-- 到货
('PURCHASE_ARRIVAL_STATUS','DRAFT','草稿',10),
('PURCHASE_ARRIVAL_STATUS','PENDING_APPROVAL','待审批',20),
('PURCHASE_ARRIVAL_STATUS','APPROVED','已通过',30),
('PURCHASE_ARRIVAL_STATUS','COMPLETED','已完成',40),
('PURCHASE_ARRIVAL_STATUS','REJECTED','已驳回',50),
('PURCHASE_ARRIVAL_STATUS','CANCELLED','已作废',60),
-- 领用
('MATERIAL_USE_STATUS','DRAFT','草稿',10),
('MATERIAL_USE_STATUS','SUBMITTED','待审批',20),
('MATERIAL_USE_STATUS','PENDING_APPROVAL','待审批',21),
('MATERIAL_USE_STATUS','APPROVED','已通过',30),
('MATERIAL_USE_STATUS','REJECTED','已驳回',40),
('MATERIAL_USE_STATUS','ISSUED','已发料',50),
('MATERIAL_USE_STATUS','CLOSED','已关闭',60),
-- 借用
('BORROW_STATUS','DRAFT','草稿',10),
('BORROW_STATUS','PENDING_APPROVAL','待审批',20),
('BORROW_STATUS','APPROVED','已通过',30),
('BORROW_STATUS','REJECTED','已驳回',40),
('BORROW_STATUS','LENT','已借出',50),
('BORROW_STATUS','PART_RETURNED','部分归还',60),
('BORROW_STATUS','RETURNED','已归还',70),
('BORROW_STATUS','OVERDUE','已逾期',80),
-- 报废
('SCRAP_STATUS','DRAFT','草稿',10),
('SCRAP_STATUS','PENDING_APPROVAL','待审批',20),
('SCRAP_STATUS','APPROVED','已通过',30),
('SCRAP_STATUS','SUBMITTED','待审批',35),
('SCRAP_STATUS','CONFIRMED','已确认报废',40),
('SCRAP_STATUS','REJECTED','已驳回',50),
-- 调拨
('TRANSFER_STATUS','DRAFT','草稿',10),
('TRANSFER_STATUS','PENDING_APPROVAL','待审批',20),
('TRANSFER_STATUS','APPROVED','已通过',30),
('TRANSFER_STATUS','OUT_DONE','已调出',40),
('TRANSFER_STATUS','CONFIRMED','已确认',50),
('TRANSFER_STATUS','REJECTED','已驳回',60),
-- 盘点
('INVENTORY_STATUS','DRAFT','草稿',10),
('INVENTORY_STATUS','PENDING_APPROVAL','待审批',20),
('INVENTORY_STATUS','APPROVED','已通过',30),
('INVENTORY_STATUS','CONFIRMED','已调账',40),
('INVENTORY_STATUS','REJECTED','已驳回',50),
-- 退库
('RETURN_STORE_STATUS','DRAFT','草稿',10),
('RETURN_STORE_STATUS','PENDING_APPROVAL','待审批',20),
('RETURN_STORE_STATUS','APPROVED','已通过',30),
('RETURN_STORE_STATUS','APPLIED','已申请',35),
('RETURN_STORE_STATUS','ACCEPTED','已验收',40),
('RETURN_STORE_STATUS','CONFIRMED','已入库',50),
('RETURN_STORE_STATUS','REJECTED','已驳回',60),
('RETURN_STORE_STATUS','CANCELLED','已作废',70),
-- 审批实例
('APPROVAL_STATUS','PENDING','待审批',10),
('APPROVAL_STATUS','PENDING_APPROVAL','待审批',11),
('APPROVAL_STATUS','APPROVED','已通过',20),
('APPROVAL_STATUS','REJECTED','已驳回',30),
-- 二维码
('QR_STATUS','CREATED','已创建',10),
('QR_STATUS','ACTIVE','已激活',20),
('QR_STATUS','RETIRED','已停用',30)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- ========== 保留原有非状态字典（已存在则只更新文案）==========
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('INBOUND_TYPE','PURCHASE','采购入库',10),
('INBOUND_TYPE','RETURN','退货入库',20),
('INBOUND_TYPE','PURCHASE_RETURN','采购退货入库',25),
('INBOUND_TYPE','TRANSFER','调拨入库',30),
('INBOUND_TYPE','ADJUST','盘点入库',40),
('INBOUND_TYPE','OTHER','其他入库',60),
('OUTBOUND_TYPE','USE','领用出库',10),
('OUTBOUND_TYPE','OTHER','其他出库',20),
('OUTBOUND_TYPE','TRANSFER','调拨出库',30),
('OUTBOUND_TYPE','SCRAP','报废出库',40),
('QR_TYPE','SKU','品码',10),
('QR_TYPE','BATCH','批码',20),
('QR_TYPE','SERIAL','件码',30),
('QR_GRANULARITY','SKU','品码',10),
('QR_GRANULARITY','BATCH','批码',20),
('QR_GRANULARITY','SERIAL','件码',30),
('BIZ_TYPE','PURCHASE_APPLY','采购申请',10),
('BIZ_TYPE','PURCHASE_ORDER','采购订单',20),
('BIZ_TYPE','PURCHASE_RETURN','采购退货',30),
('BIZ_TYPE','PURCHASE_INQUIRY','采购询价',40),
('BIZ_TYPE','PURCHASE_ARRIVAL','到货验收',50),
('BIZ_TYPE','MATERIAL_USE','领用申请',60),
('BIZ_TYPE','BORROW','借用申请',70),
('BIZ_TYPE','SCRAP','报废申请',80),
('BIZ_TYPE','TRANSFER','调拨申请',90),
('BIZ_TYPE','RETURN_STORE','退库申请',100),
('BIZ_TYPE','INVENTORY_CHECK','盘点申请',110),
('BIZ_TYPE','OUTBOUND','出库申请',120),
('BIZ_TYPE','USE_ISSUE','领用发料',130),
('BIZ_TYPE','USE_RETURN','退库',140),
('BIZ_TYPE','BORROW_LEND','借出',150),
('BIZ_TYPE','BORROW_RETURN','归还',160),
('BIZ_TYPE','TRANSFER_OUT','调拨出库',170),
('BIZ_TYPE','TRANSFER_IN','调拨入库',180),
('USE_TYPE','DAILY','日常',10),
('USE_TYPE','PROJECT','项目',20),
('BORROW_TYPE','TEMP','临时',10),
('BORROW_TYPE','LONG','长期',20),
('URGENCY','NORMAL','普通',10),
('URGENCY','URGENT','紧急',20),
('COOP_STATUS','ACTIVE','合作中',10),
('COOP_STATUS','PAUSED','暂停',20),
('COOP_STATUS','ENDED','终止',30),
('TAX_CATEGORY','VAT','增值税',10),
('TAX_CATEGORY','EXTRA','附加税',20),
('TAX_CATEGORY','OTHER','其他',30),
('ROLE','ADMIN','系统管理员',10),
('ROLE','WAREHOUSE','仓库管理员',20),
('ROLE','PURCHASER','采购员',30),
('ROLE','EMPLOYEE','普通员工',40),
('ROLE','FINANCE','财务人员',50),
('ROLE','DEPT_MANAGER','部门负责人',60),
('NUM_STATUS','0','停用',10),
('NUM_STATUS','1','启用',20),
('NUM_STATUS','true','是',30),
('NUM_STATUS','false','否',40),
('APPROVAL_ACTION','START','发起',10),
('APPROVAL_ACTION','APPROVE','通过',20),
('APPROVAL_ACTION','REJECT','驳回',30),
('APPROVAL_ACTION','TRANSFER','转交',40),
('APPROVAL_ACTION','ADD_SIGN','加签',50),
('LOG_RESULT','SUCCESS','成功',10),
('LOG_RESULT','FAIL','失败',20),
('LOG_RESULT','ERROR','错误',30)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- 库存流水 BIZ_TYPE 补充
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('BIZ_TYPE','IN','入库',230),
('BIZ_TYPE','OUT','出库',240),
('BIZ_TYPE','CHECK_GAIN','盘盈',250),
('BIZ_TYPE','CHECK_LOSS','盘亏',260),
('BIZ_TYPE','SCRAP_OP','报废出库',270)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- 旧 STATUS 映射到通用场景（可选，供兼容）
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('STATUS','DRAFT','草稿',10),
('STATUS','PENDING_APPROVAL','待审批',20),
('STATUS','SUBMITTED','待审批',21),
('STATUS','PENDING','待审批',22),
('STATUS','APPROVED','已通过',30),
('STATUS','REJECTED','已驳回',40),
('STATUS','CONFIRMED','已确认',50),
('STATUS','COMPLETED','已完成',60),
('STATUS','CANCELLED','已作废',70)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

INSERT INTO sys_dict_type (type_code, type_name, type_group, usage_remark, sort_no) VALUES
('STATUS', '通用状态（兼容）', 'BASE', '旧数据兼容；新页面请用具体事件状态', 999)
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name);

-- =====================================================================
-- 增量补丁：patch_dict_price_source
-- =====================================================================
-- 价格来源字典
USE wuzi_test;
SET NAMES utf8mb4;

INSERT INTO sys_dict_type (type_code, type_name, type_group, usage_remark, sort_no) VALUES
('PRICE_SOURCE', '价格来源', 'BUSINESS', '价格趋势来源列', 400),
('PRICE_TREND_DIR', '价格涨跌', 'BUSINESS', '价格趋势环比', 410)
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name),
  type_group = VALUES(type_group),
  usage_remark = VALUES(usage_remark),
  sort_no = VALUES(sort_no);

INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('PRICE_SOURCE','SUPPLIER_PRICE','供应商报价',10),
('PRICE_SOURCE','PURCHASE_ORDER','采购订单',20),
('PRICE_SOURCE','MANUAL','手工录入',30),
('PRICE_SOURCE','PRODUCT','商品价',40),
('PRICE_SOURCE','MATERIAL_REF','物资参考价',50),
('PRICE_TREND_DIR','UP','上涨',10),
('PRICE_TREND_DIR','DOWN','下跌',20),
('PRICE_TREND_DIR','FLAT','持平',30)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- =====================================================================
-- 增量补丁：patch_dict_resubmit
-- =====================================================================
-- 审批动作补全：重新发起
USE wuzi_test;
SET NAMES utf8mb4;
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('APPROVAL_ACTION','RESUBMIT','重新发起',60)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- =====================================================================
-- 增量补丁：patch_dict_status_labels
-- =====================================================================
-- 字典补齐：发票/结算/询价/预警/消息/流程/条件/导入等状态
-- 幂等：按 type_code / dict_key 更新

INSERT INTO sys_dict_type (type_code, type_name, type_group, usage_remark, sort_no) VALUES
('PURCHASE_INQUIRY_STATUS', '询价单状态', 'BUSINESS', '询价列表/筛选', 75),
('INVOICE_STATUS', '发票状态', 'BUSINESS', '发票登记列表', 290),
('SETTLE_STATUS', '结算单状态', 'BUSINESS', '结算列表', 300),
('MATCH_STATUS', '三单匹配结果', 'BUSINESS', '发票匹配状态', 310),
('ALERT_STATUS', '预警处理状态', 'BUSINESS', '预警记录', 320),
('ALERT_TYPE', '预警类型', 'BUSINESS', '预警记录类型列', 330),
('CONDITION_STATUS', '物资成色', 'BUSINESS', '退库/借用明细', 340),
('MSG_READ', '消息已读', 'BASE', '消息中心', 350),
('FLOW_STATUS', '审批流状态', 'BASE', '审批流配置', 360),
('IMPORT_STATUS', '导入结果', 'BUSINESS', 'Excel 导入结果', 370),
('YES_NO', '是/否', 'BASE', '批次管理等开关', 380)
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name),
  type_group = VALUES(type_group),
  usage_remark = VALUES(usage_remark),
  sort_no = VALUES(sort_no);

INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
-- 询价
('PURCHASE_INQUIRY_STATUS','DRAFT','草稿',10),
('PURCHASE_INQUIRY_STATUS','PENDING_APPROVAL','待审批',20),
('PURCHASE_INQUIRY_STATUS','APPROVED','已选定',30),
('PURCHASE_INQUIRY_STATUS','REJECTED','已驳回',40),
('PURCHASE_INQUIRY_STATUS','CANCELLED','已作废',50),
-- 发票
('INVOICE_STATUS','REGISTERED','已登记',10),
('INVOICE_STATUS','MATCHED','已匹配',20),
('INVOICE_STATUS','MATCH_FAILED','匹配失败',25),
('INVOICE_STATUS','PARTIAL','部分匹配',30),
('INVOICE_STATUS','UNMATCHED','未匹配',40),
('INVOICE_STATUS','SETTLED','已结算',50),
('INVOICE_STATUS','VOID','已作废',60),
-- 入库（补红冲）
('INBOUND_STATUS','RED_FLUSHED','已红冲',70),
-- 询价（补完成）
('PURCHASE_INQUIRY_STATUS','COMPLETED','已完成',55),
-- 结算
('SETTLE_STATUS','DRAFT','草稿',10),
('SETTLE_STATUS','PENDING','待确认',20),
('SETTLE_STATUS','CONFIRMED','已确认',30),
('SETTLE_STATUS','PAID','已付款',40),
('SETTLE_STATUS','CANCELLED','已作废',50),
-- 三单匹配（后端 matchStatus: PASS/FAIL/WARN，历史兼容 NONE/MATCHED/DIFF/PARTIAL）
('MATCH_STATUS','NONE','未匹配',10),
('MATCH_STATUS','MATCHED','已匹配',20),
('MATCH_STATUS','PASS','通过',25),
('MATCH_STATUS','WARN','有差异',28),
('MATCH_STATUS','FAIL','未通过',32),
('MATCH_STATUS','DIFF','有差异',30),
('MATCH_STATUS','PARTIAL','部分匹配',40),
-- 预警
('ALERT_STATUS','OPEN','待处理',10),
('ALERT_STATUS','READ','已读',20),
('ALERT_STATUS','HANDLED','已处理',30),
('ALERT_TYPE','LOW_STOCK','低库存',10),
('ALERT_TYPE','OVER_STOCK','超库存',20),
('ALERT_TYPE','EXPIRY','临期',30),
('ALERT_TYPE','BORROW_OVERDUE','借用逾期',40),
-- 成色
('CONDITION_STATUS','GOOD','完好',10),
('CONDITION_STATUS','DAMAGED','损坏',20),
('CONDITION_STATUS','REPAIRABLE','可修',30),
('CONDITION_STATUS','SCRAPPED','报废',40),
-- 消息
('MSG_READ','0','未读',10),
('MSG_READ','1','已读',20),
('MSG_READ','true','已读',25),
('MSG_READ','false','未读',28),
-- 审批流
('FLOW_STATUS','ACTIVE','启用',10),
('FLOW_STATUS','INACTIVE','停用',20),
('FLOW_STATUS','1','启用',25),
('FLOW_STATUS','0','停用',28),
-- 导入
('IMPORT_STATUS','SUCCESS','成功',10),
('IMPORT_STATUS','FAIL','失败',20),
('IMPORT_STATUS','PARTIAL','部分成功',30),
('IMPORT_STATUS','PENDING','待确认',40),
('IMPORT_STATUS','PENDING_CONFIRM','待确认',45),
-- 是否
('YES_NO','Y','是',10),
('YES_NO','N','否',20),
('YES_NO','1','是',25),
('YES_NO','0','否',28),
('YES_NO','true','是',30),
('YES_NO','false','否',40),
-- 通用 STATUS 补齐
('STATUS','DRAFT','草稿',10),
('STATUS','PENDING_APPROVAL','待审批',20),
('STATUS','SUBMITTED','待审批',21),
('STATUS','PENDING','待审批',22),
('STATUS','APPROVED','已通过',30),
('STATUS','REJECTED','已驳回',40),
('STATUS','CONFIRMED','已确认',50),
('STATUS','COMPLETED','已完成',60),
('STATUS','CANCELLED','已作废',70),
('STATUS','REGISTERED','已登记',80),
('STATUS','MATCHED','已匹配',85),
('STATUS','SETTLED','已结算',88),
('STATUS','OPEN','待处理',90),
('STATUS','READ','已读',92),
('STATUS','HANDLED','已处理',94),
('STATUS','ACTIVE','启用',96),
('STATUS','INACTIVE','停用',98),
('STATUS','LENT','已借出',100),
('STATUS','ISSUED','已发料',102),
('STATUS','RETURNED','已归还',104),
('STATUS','ACCEPTED','已验收',106),
('STATUS','APPLIED','已申请',108),
('STATUS','RED_FLUSHED','已红冲',110),
('STATUS','OUT_DONE','已调出',112)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- =====================================================================
-- 增量补丁：patch_dict_use_and_source
-- =====================================================================
-- 补充缺失字典：领用类型 OTHER、登录来源、调拨中间态等
USE wuzi_test;
SET NAMES utf8mb4;

INSERT INTO sys_dict_type (type_code, type_name, type_group, usage_remark, sort_no) VALUES
('USER_SOURCE', '登录来源', 'BASE', '移动端「我的」登录方式', 390)
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name),
  type_group = VALUES(type_group),
  usage_remark = VALUES(usage_remark),
  sort_no = VALUES(sort_no);

INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
-- 领用类型补全
('USE_TYPE','OTHER','其他',30),
-- 登录来源
('USER_SOURCE','PASSWORD','账号密码',10),
('USER_SOURCE','WECHAT_WORK','企业微信',20),
('USER_SOURCE','DINGTALK','钉钉',30),
-- 调拨状态补全（移动端用到）
('TRANSFER_STATUS','SUBMITTED','待审批',25),
('TRANSFER_STATUS','OUT','已调出',35),
('TRANSFER_STATUS','IN_TRANSIT','在途',45),
('TRANSFER_STATUS','TRANSFERRED','已调拨',48),
('TRANSFER_STATUS','OUT_DONE','已调出',40),
-- 库存盘点兼容
('INVENTORY_STATUS','SUBMITTED','待审批',25)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- 通用 STATUS 兼容补充
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('STATUS','ISSUED','已发料',55),
('STATUS','LENT','已借出',56),
('STATUS','RETURNED','已归还',57),
('STATUS','ACCEPTED','已验收',58),
('STATUS','APPLIED','已申请',59)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================================
-- 增量补丁：patch_approval_msg
-- =====================================================================
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
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_node := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'approval_instance' AND COLUMN_NAME = 'current_node_no'
);
SET @sql := IF(@col_node = 0,
  'ALTER TABLE approval_instance ADD COLUMN current_node_no VARCHAR(32) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

-- =====================================================================
-- 增量补丁：patch_approval_flows
-- =====================================================================
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

-- =====================================================================
-- 增量补丁：patch_finance
-- =====================================================================
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

-- =====================================================================
-- 增量补丁：patch_alert_return
-- =====================================================================
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

-- =====================================================================
-- 增量补丁：patch_org_position
-- =====================================================================
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

-- =====================================================================
-- 增量补丁：patch_purchase_line_tax
-- =====================================================================
-- 采购行税率：税码快照（字典选种 / 手填时 tax_code 为空）
-- 采购申请行补 tax_code

SET @c_apply := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_apply_item' AND COLUMN_NAME = 'tax_code'
);
SET @sql := IF(@c_apply = 0,
  'ALTER TABLE purchase_apply_item ADD COLUMN tax_code VARCHAR(32) NULL AFTER tax_rate',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 发票：记录票面税种编码（可空，手填税率时为空）
SET @c_inv := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'invoice' AND COLUMN_NAME = 'tax_code'
);
SET @sql := IF(@c_inv = 0,
  'ALTER TABLE invoice ADD COLUMN tax_code VARCHAR(32) NULL AFTER tax_rate',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 确保采购订单行有 tax_code（历史补丁已加则跳过）
SET @c_oi := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order_item' AND COLUMN_NAME = 'tax_code'
);
SET @sql := IF(@c_oi = 0,
  'ALTER TABLE purchase_order_item ADD COLUMN tax_code VARCHAR(32) NULL AFTER tax_rate',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 询价行 / 退货行 / 到货行
SET @c_ii := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry_item' AND COLUMN_NAME = 'tax_code'
);
SET @sql := IF(@c_ii = 0,
  'ALTER TABLE purchase_inquiry_item ADD COLUMN tax_code VARCHAR(32) NULL AFTER tax_rate',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_ri := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_return_item' AND COLUMN_NAME = 'tax_code'
);
SET @sql := IF(@c_ri = 0,
  'ALTER TABLE purchase_return_item ADD COLUMN tax_code VARCHAR(32) NULL AFTER tax_rate',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 增量补丁：patch_button_perms
-- =====================================================================
-- 按钮级权限种子：sys_permission 菜单 + 按钮
-- 幂等：按 perm_code 更新；ADMIN(1) 授予全部；WAREHOUSE/PURCHASER/EMPLOYEE/FINANCE 按岗位预授

DELETE FROM sys_role_permission WHERE role_id IN (1,2,3,4,5,6);
DELETE FROM sys_permission WHERE perm_code LIKE '%';

-- ========== 系统管理 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(100, 0, 'sys', '系统管理', 'MENU', NULL, 900),
(110, 100, 'sys:user', '用户管理', 'MENU', '/users', 10),
(111, 110, 'sys:user:create', '新增用户', 'BUTTON', NULL, 1),
(112, 110, 'sys:user:update', '编辑用户', 'BUTTON', NULL, 2),
(113, 110, 'sys:user:delete', '删除用户', 'BUTTON', NULL, 3),
(114, 110, 'sys:user:reset-pwd', '重置密码', 'BUTTON', NULL, 4),
(115, 110, 'sys:user:assign-role', '分配角色', 'BUTTON', NULL, 5),
(120, 100, 'sys:role', '角色权限', 'MENU', '/roles', 20),
(121, 120, 'sys:role:create', '新增角色', 'BUTTON', NULL, 1),
(122, 120, 'sys:role:update', '编辑角色', 'BUTTON', NULL, 2),
(123, 120, 'sys:role:delete', '删除角色', 'BUTTON', NULL, 3),
(124, 120, 'sys:role:assign-perm', '分配权限', 'BUTTON', NULL, 4),
(130, 100, 'sys:dept', '部门管理', 'MENU', '/depts', 30),
(131, 130, 'sys:dept:create', '新增部门', 'BUTTON', NULL, 1),
(132, 130, 'sys:dept:update', '编辑部门', 'BUTTON', NULL, 2),
(133, 130, 'sys:dept:delete', '删除部门', 'BUTTON', NULL, 3),
(140, 100, 'sys:import', 'Excel导入', 'MENU', '/excel-import', 40),
(141, 140, 'sys:import:create', '执行导入', 'BUTTON', NULL, 1),
(150, 100, 'sys:log', '操作日志', 'MENU', '/logs', 50),
(160, 100, 'sys:dict', '字典管理', 'MENU', '/dicts', 60),
(161, 160, 'sys:dict:update', '编辑字典', 'BUTTON', NULL, 1),
(162, 160, 'sys:dict:create', '新增字典', 'BUTTON', NULL, 2),
(163, 160, 'sys:dict:delete', '删除字典', 'BUTTON', NULL, 3),
(170, 100, 'flow:config', '审批流配置', 'MENU', '/flow-configs', 70);

-- ========== 基础数据 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(200, 0, 'base', '基础数据', 'MENU', NULL, 100),
(210, 200, 'base:material', '物资档案', 'MENU', '/materials', 10),
(211, 210, 'base:material:create', '新增物资', 'BUTTON', NULL, 1),
(212, 210, 'base:material:update', '编辑物资', 'BUTTON', NULL, 2),
(213, 210, 'base:material:delete', '删除物资', 'BUTTON', NULL, 3),
(220, 200, 'base:category', '物资分类', 'MENU', '/categories', 20),
(221, 220, 'base:category:create', '新增分类', 'BUTTON', NULL, 1),
(222, 220, 'base:category:update', '编辑分类', 'BUTTON', NULL, 2),
(223, 220, 'base:category:delete', '删除分类', 'BUTTON', NULL, 3),
(230, 200, 'base:supplier', '供应商', 'MENU', '/suppliers', 30),
(231, 230, 'base:supplier:create', '新增供应商', 'BUTTON', NULL, 1),
(232, 230, 'base:supplier:update', '编辑供应商', 'BUTTON', NULL, 2),
(233, 230, 'base:supplier:delete', '删除供应商', 'BUTTON', NULL, 3),
(234, 230, 'base:supplier-product:create', '新增供应商产品', 'BUTTON', NULL, 4),
(235, 230, 'base:supplier-product:update', '编辑供应商产品', 'BUTTON', NULL, 5),
(236, 230, 'base:supplier-product:delete', '删除供应商产品', 'BUTTON', NULL, 6),
(240, 200, 'base:tax-rate', '税率字典', 'MENU', '/tax-rates', 40),
(241, 240, 'base:tax-rate:create', '新增税率', 'BUTTON', NULL, 1),
(242, 240, 'base:tax-rate:update', '编辑税率', 'BUTTON', NULL, 2),
(243, 240, 'base:tax-rate:delete', '删除税率', 'BUTTON', NULL, 3),
(244, 240, 'base:tax-rate:toggle', '启停税率', 'BUTTON', NULL, 4),
(250, 200, 'base:price-trend', '价格趋势', 'MENU', '/price-trends', 50);

-- ========== 采购 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(300, 0, 'po', '采购管理', 'MENU', NULL, 200),
(310, 300, 'po:apply', '采购申请', 'MENU', '/purchase-applies', 10),
(311, 310, 'po:apply:create', '新建申请', 'BUTTON', NULL, 1),
(312, 310, 'po:apply:submit', '提交申请', 'BUTTON', NULL, 2),
(313, 310, 'po:apply:cancel', '作废申请', 'BUTTON', NULL, 3),
(314, 310, 'po:apply:delete', '删除申请', 'BUTTON', NULL, 4),
(320, 300, 'po:inquiry', '询价', 'MENU', '/inquiries', 20),
(321, 320, 'po:inquiry:create', '生成询价', 'BUTTON', NULL, 1),
(322, 320, 'po:inquiry:quote', '录入报价', 'BUTTON', NULL, 2),
(323, 320, 'po:inquiry:select', '选定供应商', 'BUTTON', NULL, 3),
(330, 300, 'po:order', '采购订单', 'MENU', '/purchase-orders', 30),
(331, 330, 'po:order:create', '新建订单', 'BUTTON', NULL, 1),
(332, 330, 'po:order:submit', '提交订单', 'BUTTON', NULL, 2),
(333, 330, 'po:order:cancel', '作废订单', 'BUTTON', NULL, 3),
(340, 300, 'po:return', '采购退货', 'MENU', '/purchase-returns', 40),
(341, 340, 'po:return:create', '新建退货', 'BUTTON', NULL, 1),
(342, 340, 'po:return:submit', '提交退货', 'BUTTON', NULL, 2),
(343, 340, 'po:return:confirm', '确认退货', 'BUTTON', NULL, 3),
(344, 340, 'po:return:cancel', '取消退货', 'BUTTON', NULL, 4),
(345, 340, 'po:return:delete', '删除退货', 'BUTTON', NULL, 5),
(350, 300, 'po:arrival', '到货验收', 'MENU', '/arrivals', 50),
(351, 350, 'po:arrival:create', '生成到货', 'BUTTON', NULL, 1),
(352, 350, 'po:arrival:submit', '提交到货', 'BUTTON', NULL, 2),
(353, 350, 'po:arrival:complete', '确认到货', 'BUTTON', NULL, 3),
(354, 350, 'po:arrival:cancel', '作废到货', 'BUTTON', NULL, 4),
(360, 300, 'approval', '审批待办', 'MENU', '/approvals', 60),
(361, 360, 'approval:approve', '审批通过', 'BUTTON', NULL, 1),
(362, 360, 'approval:reject', '审批驳回', 'BUTTON', NULL, 2),
(363, 360, 'approval:transfer', '审批转交', 'BUTTON', NULL, 3),
(364, 360, 'approval:add-sign', '审批加签', 'BUTTON', NULL, 4);

-- ========== 仓储 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(400, 0, 'wh', '仓储作业', 'MENU', NULL, 300),
(410, 400, 'wh:warehouse', '仓库库位', 'MENU', '/warehouses', 10),
(411, 410, 'wh:warehouse:create', '新建仓库', 'BUTTON', NULL, 1),
(412, 410, 'wh:warehouse:update', '编辑仓库', 'BUTTON', NULL, 2),
(413, 410, 'wh:warehouse:delete', '删除仓库', 'BUTTON', NULL, 3),
(414, 410, 'wh:location:create', '新建库位', 'BUTTON', NULL, 4),
(415, 410, 'wh:location:update', '编辑库位', 'BUTTON', NULL, 5),
(416, 410, 'wh:location:delete', '删除库位', 'BUTTON', NULL, 6),
(420, 400, 'wh:inbound', '入库', 'MENU', '/inbounds', 20),
(421, 420, 'wh:inbound:create', '新建入库', 'BUTTON', NULL, 1),
(422, 420, 'wh:inbound:confirm', '确认入库', 'BUTTON', NULL, 2),
(430, 400, 'wh:outbound', '出库', 'MENU', '/outbounds', 30),
(431, 430, 'wh:outbound:create', '新建出库', 'BUTTON', NULL, 1),
(432, 430, 'wh:outbound:submit', '提交出库', 'BUTTON', NULL, 2),
(433, 430, 'wh:outbound:confirm', '确认出库', 'BUTTON', NULL, 3),
(434, 430, 'wh:outbound:red-flush', '出库红冲', 'BUTTON', NULL, 4),
(440, 400, 'stock:view', '库存查询', 'MENU', '/stock', 40),
(450, 400, 'wh:transfer', '调拨', 'MENU', '/transfers', 50),
(451, 450, 'wh:transfer:create', '新建调拨', 'BUTTON', NULL, 1),
(452, 450, 'wh:transfer:submit', '提交调拨', 'BUTTON', NULL, 2),
(453, 450, 'wh:transfer:out', '调出', 'BUTTON', NULL, 3),
(454, 450, 'wh:transfer:in', '调入', 'BUTTON', NULL, 4),
(460, 400, 'wh:inventory', '盘点', 'MENU', '/inventory-checks', 60),
(461, 460, 'wh:inventory:create', '新建盘点', 'BUTTON', NULL, 1),
(462, 460, 'wh:inventory:submit', '提交盘点', 'BUTTON', NULL, 2),
(463, 460, 'wh:inventory:confirm', '确认调账', 'BUTTON', NULL, 3);

-- ========== 领用资产 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(500, 0, 'asset', '领用资产', 'MENU', NULL, 400),
(510, 500, 'asset:use', '领用', 'MENU', '/material-use', 10),
(511, 510, 'asset:use:create', '新建领用', 'BUTTON', NULL, 1),
(512, 510, 'asset:use:submit', '提交领用', 'BUTTON', NULL, 2),
(513, 510, 'asset:use:approve', '审批领用', 'BUTTON', NULL, 3),
(514, 510, 'asset:use:issue', '领用发料', 'BUTTON', NULL, 4),
(520, 500, 'asset:return', '退库', 'MENU', '/return-stores', 20),
(521, 520, 'asset:return:create', '新建退库', 'BUTTON', NULL, 1),
(522, 520, 'asset:return:submit', '提交退库', 'BUTTON', NULL, 2),
(523, 520, 'asset:return:accept', '退库验收', 'BUTTON', NULL, 3),
(524, 520, 'asset:return:confirm', '退库入库', 'BUTTON', NULL, 4),
(530, 500, 'asset:borrow', '借用', 'MENU', '/borrows', 30),
(531, 530, 'asset:borrow:create', '新建借用', 'BUTTON', NULL, 1),
(532, 530, 'asset:borrow:submit', '提交借用', 'BUTTON', NULL, 2),
(533, 530, 'asset:borrow:approve', '审批借用', 'BUTTON', NULL, 3),
(534, 530, 'asset:borrow:lend', '借出', 'BUTTON', NULL, 4),
(535, 530, 'asset:borrow:return', '归还', 'BUTTON', NULL, 5),
(540, 500, 'asset:scrap', '报废', 'MENU', '/scraps', 40),
(541, 540, 'asset:scrap:create', '新建报废', 'BUTTON', NULL, 1),
(542, 540, 'asset:scrap:submit', '提交报废', 'BUTTON', NULL, 2),
(543, 540, 'asset:scrap:confirm', '确认报废', 'BUTTON', NULL, 3),
(544, 540, 'asset:scrap:reject', '驳回报废', 'BUTTON', NULL, 4),
(550, 500, 'alert:view', '库存预警', 'MENU', '/alerts', 50),
(551, 550, 'alert:handle', '处理预警', 'BUTTON', NULL, 1);

-- ========== 财务 / 溯源 / 报表 ==========
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(600, 0, 'fin', '财务结算', 'MENU', NULL, 500),
(610, 600, 'fin:invoice', '发票登记', 'MENU', '/finance', 10),
(611, 610, 'fin:invoice:create', '登记发票', 'BUTTON', NULL, 1),
(612, 610, 'fin:invoice:match', '三单匹配', 'BUTTON', NULL, 2),
(613, 610, 'fin:settle:create', '生成结算', 'BUTTON', NULL, 3),
(700, 0, 'trace', '溯源标签', 'MENU', NULL, 600),
(710, 700, 'trace:qr', '二维码溯源', 'MENU', '/trace', 10),
(711, 710, 'trace:qr:generate', '生成二维码', 'BUTTON', NULL, 1),
(712, 710, 'trace:qr:activate', '激活二维码', 'BUTTON', NULL, 2),
(720, 700, 'label:print', '标签打印', 'MENU', '/label-print', 20),
(721, 720, 'label:print:generate', '生成标签', 'BUTTON', NULL, 1),
(800, 0, 'report', '分析报表', 'MENU', NULL, 700),
(810, 800, 'report:stats', '统计看板', 'MENU', '/stats', 10),
(820, 800, 'report:advanced', '高级报表', 'MENU', '/reports', 20),
(900, 0, 'msg', '消息中心', 'MENU', '/messages', 800),
(901, 900, 'msg:read', '标记已读', 'BUTTON', NULL, 1),
(902, 900, 'msg:read-all', '全部已读', 'BUTTON', NULL, 2);

-- ========== ADMIN 授予全部 ==========
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission WHERE is_deleted = 0;

-- 仓管：仓储+领用执行+预警+盘点+扫码标签
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 2, id FROM sys_permission WHERE is_deleted = 0 AND (
  perm_code LIKE 'wh:%' OR perm_code LIKE 'stock:%' OR perm_code LIKE 'asset:%'
  OR perm_code LIKE 'alert:%' OR perm_code LIKE 'trace:%' OR perm_code LIKE 'label:%'
  OR perm_code LIKE 'msg:%' OR perm_code LIKE 'approval:%'
  OR perm_code IN ('base','base:material','base:category','base:supplier','base:price-trend')
);

-- 采购：采购域+供应商+询价
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 3, id FROM sys_permission WHERE is_deleted = 0 AND (
  perm_code LIKE 'po:%' OR perm_code LIKE 'approval:%' OR perm_code LIKE 'msg:%'
  OR perm_code LIKE 'base:%' OR perm_code IN ('base','po','report','report:stats')
);

-- 员工：申请类 + 查询
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 4, id FROM sys_permission WHERE is_deleted = 0 AND (
  perm_code IN ('po:apply','po:apply:create','po:apply:submit','po:apply:cancel','po:apply:delete')
  OR perm_code IN ('asset:use','asset:use:create','asset:use:submit')
  OR perm_code IN ('asset:return','asset:return:create','asset:return:submit')
  OR perm_code IN ('asset:borrow','asset:borrow:create','asset:borrow:submit','asset:borrow:return')
  OR perm_code LIKE 'msg:%' OR perm_code IN ('stock:view','base:material','base','po','asset')
  OR perm_code IN ('trace:qr','trace:qr:generate','label:print','report:stats')
);

-- 财务：发票结算 + 审批
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 5, id FROM sys_permission WHERE is_deleted = 0 AND (
  perm_code LIKE 'fin:%' OR perm_code LIKE 'approval:%' OR perm_code LIKE 'msg:%'
  OR perm_code IN ('base','base:tax-rate','base:tax-rate:create','base:tax-rate:update','base:tax-rate:delete','base:tax-rate:toggle')
  OR perm_code IN ('po','po:order','po:return','report','report:stats','report:advanced','stock:view')
);

-- 部门负责人（若存在 role_id=6 DEPT_MANAGER）
INSERT INTO sys_role_permission (role_id, perm_id)
SELECT 6, id FROM sys_permission WHERE is_deleted = 0 AND (
  perm_code LIKE 'approval:%' OR perm_code LIKE 'msg:%'
  OR perm_code IN ('po:apply','po:apply:create','po:apply:submit','po:apply:cancel','po:apply:delete')
  OR perm_code IN ('asset:use','asset:use:create','asset:use:submit','asset:use:approve')
  OR perm_code IN ('asset:return','asset:return:create','asset:return:submit')
  OR perm_code IN ('asset:borrow','asset:borrow:create','asset:borrow:submit','asset:borrow:approve')
  OR perm_code IN ('asset:scrap','asset:scrap:create','asset:scrap:submit')
  OR perm_code IN ('stock:view','base','base:material','report:stats')
) AND EXISTS (SELECT 1 FROM sys_role r WHERE r.id = 6 AND r.is_deleted = 0);

-- =====================================================================
-- 增量补丁：patch_flow_config_perms
-- =====================================================================
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

-- =====================================================================
-- 增量补丁：patch_org_tree_dept_type
-- =====================================================================
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

-- =====================================================================
-- 增量补丁：patch_plan_budget_contract_payment
-- =====================================================================
-- 采购计划 / 预算 / 合同 / 付款 + 任务锁 + 系统配置
USE wuzi_test;
SET NAMES utf8mb4;

-- 定时任务锁
CREATE TABLE IF NOT EXISTS sys_job_lock (
  job_name VARCHAR(64) NOT NULL PRIMARY KEY,
  locked_by VARCHAR(128),
  locked_at DATETIME,
  expire_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 系统参数
CREATE TABLE IF NOT EXISTS sys_config (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  config_key VARCHAR(64) NOT NULL,
  config_value VARCHAR(512),
  config_name VARCHAR(128),
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===================== 采购计划 / 需求汇总 =====================
CREATE TABLE IF NOT EXISTS purchase_plan (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  plan_no VARCHAR(64) NOT NULL,
  title VARCHAR(128) NOT NULL,
  plan_year INT NOT NULL,
  plan_month INT,
  dept_id BIGINT,
  project_code VARCHAR(64),
  plan_type VARCHAR(32) DEFAULT 'MONTHLY',
  status VARCHAR(32) DEFAULT 'DRAFT',
  total_amount DECIMAL(18,2) DEFAULT 0,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_plan_no (plan_no),
  KEY idx_plan_dept (dept_id),
  KEY idx_plan_year (plan_year, plan_month),
  KEY idx_plan_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS purchase_plan_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  material_id BIGINT,
  material_name VARCHAR(128),
  spec VARCHAR(128),
  unit VARCHAR(32),
  qty DECIMAL(18,3) DEFAULT 0,
  estimated_price DECIMAL(18,6) DEFAULT 0,
  estimated_amount DECIMAL(18,2) DEFAULT 0,
  purpose VARCHAR(255),
  demand_dept_id BIGINT,
  status VARCHAR(32) DEFAULT 'PENDING',
  apply_id BIGINT,
  remark VARCHAR(255),
  KEY idx_plan_item (plan_id),
  KEY idx_plan_item_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===================== 预算 =====================
CREATE TABLE IF NOT EXISTS budget (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  budget_no VARCHAR(64) NOT NULL,
  budget_year INT NOT NULL,
  dept_id BIGINT,
  project_code VARCHAR(64),
  category VARCHAR(64) DEFAULT 'PURCHASE',
  budget_amount DECIMAL(18,2) DEFAULT 0,
  used_amount DECIMAL(18,2) DEFAULT 0,
  frozen_amount DECIMAL(18,2) DEFAULT 0,
  status VARCHAR(32) DEFAULT 'ACTIVE',
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_budget_no (budget_no),
  KEY idx_budget_scope (budget_year, dept_id, project_code, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS budget_control_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  budget_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  amount DECIMAL(18,2) DEFAULT 0,
  biz_type VARCHAR(64),
  biz_id BIGINT,
  before_used DECIMAL(18,2) DEFAULT 0,
  before_frozen DECIMAL(18,2) DEFAULT 0,
  after_used DECIMAL(18,2) DEFAULT 0,
  after_frozen DECIMAL(18,2) DEFAULT 0,
  remark VARCHAR(255),
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_budget_log (budget_id),
  KEY idx_budget_log_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===================== 合同 =====================
CREATE TABLE IF NOT EXISTS purchase_contract (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  contract_no VARCHAR(64) NOT NULL,
  title VARCHAR(128) NOT NULL,
  contract_type VARCHAR(32) DEFAULT 'ORDINARY',
  supplier_id BIGINT,
  sign_date DATE,
  start_date DATE,
  end_date DATE,
  contract_amount DECIMAL(18,2) DEFAULT 0,
  paid_amount DECIMAL(18,2) DEFAULT 0,
  tax_inclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_amount DECIMAL(18,2) DEFAULT 0,
  status VARCHAR(32) DEFAULT 'DRAFT',
  budget_id BIGINT,
  dept_id BIGINT,
  owner_id BIGINT,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_contract_no (contract_no),
  KEY idx_contract_supplier (supplier_id),
  KEY idx_contract_status (status),
  KEY idx_contract_budget (budget_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS purchase_contract_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  material_id BIGINT,
  material_name VARCHAR(128),
  spec VARCHAR(128),
  unit VARCHAR(32),
  qty DECIMAL(18,3) DEFAULT 0,
  price DECIMAL(18,6) DEFAULT 0,
  tax_rate DECIMAL(5,2) DEFAULT 0,
  tax_exclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_inclusive_amount DECIMAL(18,2) DEFAULT 0,
  tax_amount DECIMAL(18,2) DEFAULT 0,
  remark VARCHAR(255),
  KEY idx_contract_item (contract_id),
  KEY idx_contract_item_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS contract_payment_node (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  node_name VARCHAR(64) NOT NULL,
  plan_date DATE,
  plan_amount DECIMAL(18,2) DEFAULT 0,
  paid_amount DECIMAL(18,2) DEFAULT 0,
  status VARCHAR(32) DEFAULT 'PENDING',
  remark VARCHAR(255),
  KEY idx_pay_node (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===================== 付款 / 应付 =====================
CREATE TABLE IF NOT EXISTS payment_order (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  payment_no VARCHAR(64) NOT NULL,
  supplier_id BIGINT,
  contract_id BIGINT,
  settle_id BIGINT,
  pay_node_id BIGINT,
  budget_id BIGINT,
  amount DECIMAL(18,2) DEFAULT 0,
  pay_method VARCHAR(32) DEFAULT 'BANK',
  pay_date DATE,
  status VARCHAR(32) DEFAULT 'DRAFT',
  dept_id BIGINT,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_by BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by BIGINT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_payment_no (payment_no),
  KEY idx_payment_supplier (supplier_id),
  KEY idx_payment_contract (contract_id),
  KEY idx_payment_settle (settle_id),
  KEY idx_payment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 字典种子（type_code 唯一键幂等）
INSERT INTO sys_dict_type (type_code, type_name, type_group, usage_remark, sort_no) VALUES
  ('PLAN_STATUS', '采购计划状态', 'BUSINESS', '采购计划列表/筛选', 210),
  ('PLAN_TYPE', '采购计划类型', 'BUSINESS', '采购计划类型下拉/列表', 212),
  ('PLAN_ITEM_STATUS', '计划行状态', 'BUSINESS', '采购计划明细', 215),
  ('BUDGET_ACTION', '预算动作', 'BUSINESS', '冻结/消耗/释放', 220),
  ('BUDGET_STATUS', '预算状态', 'BUSINESS', '预算控制列表', 225),
  ('CONTRACT_TYPE', '合同类型', 'BUSINESS', '合同类型下拉', 230),
  ('CONTRACT_STATUS', '合同状态', 'BUSINESS', '合同列表/筛选', 235),
  ('PAYMENT_STATUS', '付款状态', 'BUSINESS', '付款单列表/筛选', 240),
  ('PAY_METHOD', '付款方式', 'BASE', '付款方式下拉', 245),
  ('CONTRACT_PAY_NODE_STATUS', '合同付款节点状态', 'BUSINESS', '合同付款节点', 250)
ON DUPLICATE KEY UPDATE
  type_name = VALUES(type_name),
  type_group = VALUES(type_group),
  usage_remark = VALUES(usage_remark),
  sort_no = VALUES(sort_no);

INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
  -- 采购计划
  ('PLAN_STATUS','DRAFT','草稿',10),
  ('PLAN_STATUS','SUBMITTED','待审批',20),
  ('PLAN_STATUS','APPROVED','已通过',30),
  ('PLAN_STATUS','REJECTED','已驳回',40),
  ('PLAN_STATUS','CLOSED','已关闭',50),
  ('PLAN_STATUS','CANCELLED','已作废',60),
  ('PLAN_ITEM_STATUS','PENDING','待转单',10),
  ('PLAN_ITEM_STATUS','CONVERTED','已转申请',20),
  ('PLAN_TYPE','MONTHLY','月度',10),
  ('PLAN_TYPE','QUARTERLY','季度',20),
  ('PLAN_TYPE','YEARLY','年度',30),
  ('PLAN_TYPE','PROJECT','项目',40),
  -- 预算（BudgetService / BudgetController）
  ('BUDGET_ACTION','FREEZE','冻结',10),
  ('BUDGET_ACTION','CONSUME','消耗',20),
  ('BUDGET_ACTION','RELEASE','释放',30),
  ('BUDGET_STATUS','ACTIVE','启用中',10),
  ('BUDGET_STATUS','FROZEN','已冻结',20),
  -- 合同
  ('CONTRACT_TYPE','ORDINARY','普通合同',10),
  ('CONTRACT_TYPE','FRAMEWORK','框架协议',20),
  ('CONTRACT_STATUS','DRAFT','草稿',10),
  ('CONTRACT_STATUS','PENDING','待审批',20),
  ('CONTRACT_STATUS','ACTIVE','已生效',30),
  ('CONTRACT_STATUS','CLOSED','已关闭',40),
  ('CONTRACT_STATUS','TERMINATED','已终止',50),
  -- 付款
  ('PAYMENT_STATUS','DRAFT','草稿',10),
  ('PAYMENT_STATUS','PENDING','待审批',20),
  ('PAYMENT_STATUS','PAID','已付款',30),
  ('PAYMENT_STATUS','REJECTED','已驳回',40),
  ('PAYMENT_STATUS','CANCELLED','已作废',50),
  ('PAY_METHOD','BANK','银行转账',10),
  ('PAY_METHOD','ACCEPTANCE','承兑汇票',20),
  ('PAY_METHOD','CASH','现金',30),
  ('PAY_METHOD','OTHER','其他',40),
  ('CONTRACT_PAY_NODE_STATUS','PENDING','待付款',10),
  ('CONTRACT_PAY_NODE_STATUS','PARTIAL','部分付款',20),
  ('CONTRACT_PAY_NODE_STATUS','PAID','已付清',30)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- 按钮权限码种子
-- 注意：id 需避开 patch_button_perms 占用段（100–900），统一用 960+；
-- 固定 id 冲突时 INSERT IGNORE 会整行跳过，故用 ON DUPLICATE KEY（id 主键 / perm_code 唯一键）幂等
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
  (960, 300, 'po:plan', '采购计划', 'MENU', '/purchase-plans', 5),
  (961, 960, 'po:plan:create', '新建计划', 'BUTTON', NULL, 1),
  (962, 960, 'po:plan:update', '编辑计划', 'BUTTON', NULL, 2),
  (963, 960, 'po:plan:submit', '提交计划', 'BUTTON', NULL, 3),
  (964, 960, 'po:plan:approve', '审核计划', 'BUTTON', NULL, 4),
  (965, 960, 'po:plan:reject', '驳回计划', 'BUTTON', NULL, 5),
  (966, 960, 'po:plan:cancel', '作废计划', 'BUTTON', NULL, 6),
  (967, 960, 'po:plan:convert', '计划转申请', 'BUTTON', NULL, 7),
  (970, 300, 'po:contract', '合同管理', 'MENU', '/contracts', 6),
  (971, 970, 'po:contract:create', '新建合同', 'BUTTON', NULL, 1),
  (972, 970, 'po:contract:update', '编辑合同', 'BUTTON', NULL, 2),
  (973, 970, 'po:contract:submit', '提交合同', 'BUTTON', NULL, 3),
  (974, 970, 'po:contract:activate', '合同生效', 'BUTTON', NULL, 4),
  (975, 970, 'po:contract:close', '关闭合同', 'BUTTON', NULL, 5),
  (980, 600, 'fin:budget', '预算控制', 'MENU', '/budgets', 20),
  (981, 980, 'fin:budget:create', '新建预算', 'BUTTON', NULL, 1),
  (982, 980, 'fin:budget:update', '调整预算', 'BUTTON', NULL, 2),
  (983, 980, 'fin:budget:toggle', '启停预算', 'BUTTON', NULL, 3),
  (985, 600, 'fin:payment', '付款应付', 'MENU', '/payments', 5),
  (986, 985, 'fin:payment:create', '新建付款', 'BUTTON', NULL, 1),
  (987, 985, 'fin:payment:submit', '提交付款', 'BUTTON', NULL, 2),
  (988, 985, 'fin:payment:confirm', '确认付款', 'BUTTON', NULL, 3),
  (989, 985, 'fin:payment:reject', '驳回付款', 'BUTTON', NULL, 4),
  (990, 985, 'fin:payment:cancel', '作废付款', 'BUTTON', NULL, 5),
  (995, 100, 'sys:config', '系统配置', 'MENU', '/configs', 65),
  (996, 995, 'sys:config:save', '保存配置', 'BUTTON', NULL, 1),
  (997, 995, 'sys:config:delete', '删除配置', 'BUTTON', NULL, 2),
  (998, 420, 'wh:inbound:red-flush', '入库红冲', 'BUTTON', NULL, 3),
  (999, 320, 'po:inquiry:complete', '完成询价', 'BUTTON', NULL, 4)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_name = VALUES(perm_name),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  sort_no = VALUES(sort_no);

-- 修复历史错挂：perm_code 已存在但 parent 不对时，按码回写父节点
UPDATE sys_permission p
  JOIN (
    SELECT 'po:plan' c, 300 pid, '/purchase-plans' pth UNION ALL
    SELECT 'po:plan:create', 960, NULL UNION ALL
    SELECT 'po:plan:update', 960, NULL UNION ALL
    SELECT 'po:plan:submit', 960, NULL UNION ALL
    SELECT 'po:plan:approve', 960, NULL UNION ALL
    SELECT 'po:plan:reject', 960, NULL UNION ALL
    SELECT 'po:plan:cancel', 960, NULL UNION ALL
    SELECT 'po:plan:convert', 960, NULL UNION ALL
    SELECT 'po:contract', 300, '/contracts' UNION ALL
    SELECT 'po:contract:create', 970, NULL UNION ALL
    SELECT 'po:contract:update', 970, NULL UNION ALL
    SELECT 'po:contract:submit', 970, NULL UNION ALL
    SELECT 'po:contract:activate', 970, NULL UNION ALL
    SELECT 'po:contract:close', 970, NULL UNION ALL
    SELECT 'fin:budget', 600, '/budgets' UNION ALL
    SELECT 'fin:budget:create', 980, NULL UNION ALL
    SELECT 'fin:budget:update', 980, NULL UNION ALL
    SELECT 'fin:budget:toggle', 980, NULL UNION ALL
    SELECT 'fin:payment', 600, '/payments' UNION ALL
    SELECT 'fin:payment:create', 985, NULL UNION ALL
    SELECT 'fin:payment:submit', 985, NULL UNION ALL
    SELECT 'fin:payment:confirm', 985, NULL UNION ALL
    SELECT 'fin:payment:reject', 985, NULL UNION ALL
    SELECT 'fin:payment:cancel', 985, NULL UNION ALL
    SELECT 'sys:config', 100, '/configs' UNION ALL
    SELECT 'sys:config:save', 995, NULL UNION ALL
    SELECT 'sys:config:delete', 995, NULL
  ) t ON t.c = p.perm_code
SET p.parent_id = t.pid,
    p.path = COALESCE(t.pth, p.path);

-- ADMIN(1) 全量预授；FINANCE 预授预算/付款；PURCHASER 预授计划/合同
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission WHERE perm_code LIKE 'po:plan%' OR perm_code LIKE 'po:contract%'
  OR perm_code LIKE 'fin:budget%' OR perm_code LIKE 'fin:payment%' OR perm_code LIKE 'sys:config%'
  OR perm_code IN ('wh:inbound:red-flush', 'po:inquiry:complete');

INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 6, id FROM sys_permission WHERE perm_code LIKE 'fin:budget%' OR perm_code LIKE 'fin:payment%';

INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 3, id FROM sys_permission WHERE perm_code LIKE 'po:plan%' OR perm_code LIKE 'po:contract%'
  OR perm_code = 'po:inquiry:complete';

INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 2, id FROM sys_permission WHERE perm_code = 'wh:inbound:red-flush';

-- =====================================================================
-- 增量补丁：patch_unit_project_ledger
-- =====================================================================
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

-- =====================================================================
-- 增量补丁：patch_label_template_supplier_contact
-- =====================================================================
-- ============================================================
-- 标签模板在线编辑 + 供应商联系人
-- 1) label_template：标签尺寸/显示字段/自定义 HTML
-- 2) supplier_contact：供应商多联系人（主联系人）
-- 3) 权限种子（id 1000+，避开 100-999 占用段）
-- ============================================================

-- ---------- 标签模板 ----------
CREATE TABLE IF NOT EXISTS label_template (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  template_code VARCHAR(64) NOT NULL,
  template_name VARCHAR(128) NOT NULL,
  width_mm DECIMAL(8,2) NOT NULL DEFAULT 50.00,
  height_mm DECIMAL(8,2) NOT NULL DEFAULT 30.00,
  dpi INT NOT NULL DEFAULT 203,
  title VARCHAR(128) DEFAULT '物资标签',
  qr_module_size INT NOT NULL DEFAULT 5,
  show_batch TINYINT NOT NULL DEFAULT 1,
  show_serial TINYINT NOT NULL DEFAULT 1,
  show_supplier TINYINT NOT NULL DEFAULT 0,
  show_price TINYINT NOT NULL DEFAULT 0,
  html_body TEXT NULL COMMENT '可选自定义 HTML 标签体，空则用内置布局',
  status TINYINT DEFAULT 1,
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_label_template_code (template_code),
  KEY idx_label_template_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子：默认 50×30 / 60×40（幂等）
INSERT INTO label_template
  (id, template_code, template_name, width_mm, height_mm, dpi, title, qr_module_size,
   show_batch, show_serial, show_supplier, show_price, status)
VALUES
  (1, 'DEFAULT_50X30', '默认 50×30', 50, 30, 203, '物资标签', 5, 1, 1, 0, 0, 1),
  (2, 'DEFAULT_60X40', '默认 60×40', 60, 40, 203, '物资标签', 6, 1, 1, 1, 0, 1)
ON DUPLICATE KEY UPDATE
  template_name = VALUES(template_name),
  width_mm = VALUES(width_mm),
  height_mm = VALUES(height_mm),
  dpi = VALUES(dpi),
  title = VALUES(title),
  qr_module_size = VALUES(qr_module_size),
  show_batch = VALUES(show_batch),
  show_serial = VALUES(show_serial),
  show_supplier = VALUES(show_supplier),
  show_price = VALUES(show_price),
  status = VALUES(status);

-- ---------- 供应商联系人 ----------
CREATE TABLE IF NOT EXISTS supplier_contact (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  supplier_id BIGINT NOT NULL,
  contact_name VARCHAR(64) NOT NULL,
  phone VARCHAR(32),
  email VARCHAR(128),
  position VARCHAR(64),
  is_primary TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_supplier_contact_sid (supplier_id),
  KEY idx_supplier_contact_primary (supplier_id, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 权限：标签模板 ----------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(1000, 700,  'label:template',         '标签模板',   'MENU',   '/label-templates', 25),
(1001, 1000, 'label:template:create',  '新建模板',   'BUTTON', NULL, 1),
(1002, 1000, 'label:template:update',  '编辑模板',   'BUTTON', NULL, 2),
(1003, 1000, 'label:template:delete',  '删除模板',   'BUTTON', NULL, 3),
(1004, 1000, 'label:template:preview', '预览模板',   'BUTTON', NULL, 4)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_name = VALUES(perm_name),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  sort_no = VALUES(sort_no);

-- ---------- 权限：供应商联系人（挂在供应商 230 下） ----------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(1010, 230, 'base:supplier-contact:create',     '新增联系人',   'BUTTON', NULL, 7),
(1011, 230, 'base:supplier-contact:update',     '编辑联系人',   'BUTTON', NULL, 8),
(1012, 230, 'base:supplier-contact:delete',     '删除联系人',   'BUTTON', NULL, 9),
(1013, 230, 'base:supplier-contact:set-primary','设主联系人',   'BUTTON', NULL, 10)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_name = VALUES(perm_name),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  sort_no = VALUES(sort_no);

-- ADMIN 全量预授
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission
WHERE perm_code LIKE 'label:template%' OR perm_code LIKE 'base:supplier-contact%';

-- 仓管：标签模板
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 2, id FROM sys_permission WHERE perm_code LIKE 'label:template%';

-- 采购：供应商联系人
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 3, id FROM sys_permission WHERE perm_code LIKE 'base:supplier-contact%';

-- =====================================================================
-- 增量补丁：patch_inquiry_approval
-- =====================================================================
-- 询价独立审批：purchase_inquiry 补审批人/审批时间 + 按钮权限种子
-- 幂等：字段按 information_schema 判断；权限按 perm_code upsert

SET @c_ab := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry' AND COLUMN_NAME = 'approved_by'
);
SET @sql := IF(@c_ab = 0,
  'ALTER TABLE purchase_inquiry ADD COLUMN approved_by BIGINT NULL AFTER selected_supplier_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_at := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry' AND COLUMN_NAME = 'approved_at'
);
SET @sql := IF(@c_at = 0,
  'ALTER TABLE purchase_inquiry ADD COLUMN approved_at DATETIME NULL AFTER approved_by',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 实体字段补齐（历史库缺列）
SET @c_it := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry' AND COLUMN_NAME = 'inquire_time'
);
SET @sql := IF(@c_it = 0,
  'ALTER TABLE purchase_inquiry ADD COLUMN inquire_time DATETIME NULL AFTER title',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_ps := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry_item' AND COLUMN_NAME = 'price_source'
);
SET @sql := IF(@c_ps = 0,
  'ALTER TABLE purchase_inquiry_item ADD COLUMN price_source VARCHAR(32) NULL AFTER is_selected',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_rm := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_inquiry_item' AND COLUMN_NAME = 'remark'
);
SET @sql := IF(@c_rm = 0,
  'ALTER TABLE purchase_inquiry_item ADD COLUMN remark VARCHAR(255) NULL AFTER price_source',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 询价按钮权限：提交/作废/审批/驳回/完成
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, sort_no) VALUES
(324, 320, 'po:inquiry:submit', '提交询价', 'BUTTON', NULL, 4),
(325, 320, 'po:inquiry:cancel', '作废询价', 'BUTTON', NULL, 5),
(326, 320, 'po:inquiry:approve', '审批通过', 'BUTTON', NULL, 6),
(327, 320, 'po:inquiry:reject', '审批驳回', 'BUTTON', NULL, 7),
(328, 320, 'po:inquiry:complete', '完成询价', 'BUTTON', NULL, 8)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id),
  perm_name = VALUES(perm_name),
  perm_type = VALUES(perm_type),
  path = VALUES(path),
  sort_no = VALUES(sort_no);

-- ADMIN 角色授予新按钮
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT 1, id FROM sys_permission
WHERE perm_code IN ('po:inquiry:submit','po:inquiry:cancel','po:inquiry:approve','po:inquiry:reject','po:inquiry:complete');

-- 询价状态标签：APPROVED 语义改为「已通过」（选定供应商后仍为 APPROVED，完成置 COMPLETED）
UPDATE sys_dict SET dict_label = '已通过'
WHERE dict_type = 'PURCHASE_INQUIRY_STATUS' AND dict_key = 'APPROVED';

-- =====================================================================
-- 增量补丁：patch_import_batch_biz
-- =====================================================================
-- 导入扩展：import_batch_biz 批次业务主键关联（真回滚支撑）
-- 幂等：CREATE TABLE IF NOT EXISTS

CREATE TABLE IF NOT EXISTS import_batch_biz (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_id VARCHAR(64) NOT NULL,
  data_type VARCHAR(32) NOT NULL COMMENT 'SUPPLIER/SUPPLIER_PRODUCT/MATERIAL/OPENING_STOCK',
  biz_table VARCHAR(32) NOT NULL COMMENT 'material/stock/stock_record/supplier/supplier_product',
  biz_id BIGINT NOT NULL COMMENT '业务主键',
  ref_id BIGINT NULL COMMENT '关联主键（如 warehouse_id）',
  seq INT DEFAULT 0 COMMENT '导入顺序，回滚按 seq 逆序',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_batch (batch_id),
  KEY idx_batch_seq (batch_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入批次业务主键关联';

-- 导入类型字典补充
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('IMPORT_TYPE','SUPPLIER','供应商',10),
('IMPORT_TYPE','SUPPLIER_PRODUCT','供应商产品',20),
('IMPORT_TYPE','MATERIAL','物资档案',30),
('IMPORT_TYPE','OPENING_STOCK','期初库存',40)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);

-- =====================================================================
-- 增量补丁：patch_apply_plan_link
-- =====================================================================
-- 采购申请关联回来源采购计划（计划行转申请）
-- 1) purchase_apply 补 plan_id / plan_no，便于申请列表反查计划
-- 2) 对 plan_id 建索引

USE wuzi_test;
SET NAMES utf8mb4;

SET @c_plan_id := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_apply' AND COLUMN_NAME = 'plan_id'
);
SET @sql := IF(@c_plan_id = 0,
  'ALTER TABLE purchase_apply ADD COLUMN plan_id BIGINT NULL AFTER id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_plan_no := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_apply' AND COLUMN_NAME = 'plan_no'
);
SET @sql := IF(@c_plan_no = 0,
  'ALTER TABLE purchase_apply ADD COLUMN plan_no VARCHAR(64) NULL AFTER apply_no',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_apply' AND INDEX_NAME = 'idx_plan_id'
);
SET @sql := IF(@c_idx = 0,
  'ALTER TABLE purchase_apply ADD KEY idx_plan_id (plan_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 增量补丁：patch_order_inquiry_link
-- =====================================================================
-- 采购订单关联回来源询价（询价选定供应商生成订单）
USE wuzi_test;
SET NAMES utf8mb4;

SET @c_inquiry_id := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'inquiry_id'
);
SET @sql := IF(@c_inquiry_id = 0,
  'ALTER TABLE purchase_order ADD COLUMN inquiry_id BIGINT NULL AFTER apply_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_inquiry_no := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND COLUMN_NAME = 'inquiry_no'
);
SET @sql := IF(@c_inquiry_no = 0,
  'ALTER TABLE purchase_order ADD COLUMN inquiry_no VARCHAR(64) NULL AFTER inquiry_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @c_idx := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order' AND INDEX_NAME = 'idx_inquiry_id'
);
SET @sql := IF(@c_idx = 0,
  'ALTER TABLE purchase_order ADD KEY idx_inquiry_id (inquiry_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 增量补丁：patch_use_issue_warehouse
-- =====================================================================
-- 领用申请记录发料仓库，便于列表反查库存扣减来源
USE wuzi_test;
SET NAMES utf8mb4;

SET @c := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'material_use_apply' AND COLUMN_NAME = 'issue_warehouse_id'
);
SET @sql := IF(@c = 0,
  'ALTER TABLE material_use_apply ADD COLUMN issue_warehouse_id BIGINT NULL AFTER status',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 增量补丁：patch_ai_assistant
-- =====================================================================
-- =============================================================
-- patch_ai_assistant.sql  智能助手 P0（可重复执行，幂等）
-- 3 张表 + 权限种子（菜单 ai + 按钮 ai:chat，全角色预授）
-- 编码：UTF-8 无 BOM；导入必须 --default-character-set=utf8mb4
-- =============================================================

CREATE TABLE IF NOT EXISTS ai_chat_session (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT       NOT NULL,
  title       VARCHAR(100) NOT NULL DEFAULT '新对话',
  is_deleted  TINYINT      NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ai_session_user (user_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_chat_message (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id  BIGINT      NOT NULL,
  role        VARCHAR(16) NOT NULL COMMENT 'user/assistant/tool',
  content     MEDIUMTEXT  NULL,
  tool_name   VARCHAR(64) NULL,
  tool_args   JSON        NULL,
  tokens      INT         NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_msg_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_tool_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT      NOT NULL,
  session_id    BIGINT      NULL,
  tool_name     VARCHAR(64) NOT NULL,
  args_json     JSON        NULL,
  result_digest VARCHAR(500) NULL,
  cost_ms       INT         NULL,
  success       TINYINT     NOT NULL DEFAULT 1,
  error_msg     VARCHAR(500) NULL,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_tool_user (user_id, created_at),
  KEY idx_ai_tool_name (tool_name, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 权限种子：不硬编码 id，存在性判断保证幂等
INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, path, sort_no)
SELECT 0, 'ai', '智能助手', 'MENU', NULL, 950
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'ai');

SET @ai_menu := (SELECT id FROM sys_permission WHERE perm_code = 'ai');

INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, sort_no)
SELECT @ai_menu, 'ai:chat', '智能对话', 'BUTTON', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'ai:chat');

-- 全角色预授（新增角色后重跑本补丁即可补授）
INSERT IGNORE INTO sys_role_permission (role_id, perm_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.is_deleted = 0
  AND p.perm_code IN ('ai', 'ai:chat');

-- =====================================================================
-- 增量补丁：patch_material_alias
-- =====================================================================
-- =============================================================
-- patch_material_alias.sql  物资别名词典（智能助手查询增强，幂等）
-- 解决同义词/俗名无法 LIKE 命中的问题：用户问"复印纸"→档案"A4打印纸"
-- 解析优先级：AiLookupService 先查本词典（等值），未中再走 LIKE 降级
-- 维护方式：直接 INSERT（P1 提供物资档案页内管理入口）
-- =============================================================

CREATE TABLE IF NOT EXISTS material_alias (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  material_id BIGINT       NOT NULL,
  alias       VARCHAR(100) NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_alias (alias),
  KEY idx_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 种子：按档案名关联（档案不存在时该行自然不插入），幂等
INSERT INTO material_alias (material_id, alias)
SELECT m.id, t.alias
FROM material m
JOIN (
  SELECT 'A4打印纸' AS mname, '复印纸' AS alias UNION ALL
  SELECT 'A4打印纸', '打印纸' UNION ALL
  SELECT 'A4打印纸', 'A4纸' UNION ALL
  SELECT 'A4打印纸', 'A4' UNION ALL
  SELECT '中性签字笔', '签字笔' UNION ALL
  SELECT '中性签字笔', '水笔' UNION ALL
  SELECT '中性签字笔', '笔' UNION ALL
  SELECT '激光打印机硒鼓', '硒鼓' UNION ALL
  SELECT '激光打印机硒鼓', '粉盒' UNION ALL
  SELECT 'ABS安全帽', '安全帽' UNION ALL
  SELECT 'ABS安全帽', '头盔' UNION ALL
  SELECT '丁腈防护手套', '手套' UNION ALL
  SELECT '丁腈防护手套', '防护手套' UNION ALL
  SELECT '丁腈防护手套', '丁腈手套' UNION ALL
  SELECT '防砸劳保鞋', '劳保鞋' UNION ALL
  SELECT '工业润滑油', '润滑油' UNION ALL
  SELECT '工业润滑油', '机油' UNION ALL
  SELECT '五层瓦楞纸箱', '纸箱' UNION ALL
  SELECT '五层瓦楞纸箱', '包装箱' UNION ALL
  SELECT '单反相机套机', '相机' UNION ALL
  SELECT '单反相机套机', '单反' UNION ALL
  SELECT 'ThinkPad T14 笔记本', '笔记本' UNION ALL
  SELECT 'ThinkPad T14 笔记本', '电脑' UNION ALL
  SELECT 'ThinkPad T14 笔记本', '笔记本电脑'
) t ON m.material_name = t.mname
WHERE NOT EXISTS (SELECT 1 FROM material_alias x WHERE x.alias = t.alias);

-- =====================================================================
-- 补建索引段：patch_indexes 中三条 ALTER 依赖后续补丁建的表，
-- 原按序执行时失败跳过；此处统一补建（幂等前请确认列已存在）。
-- =====================================================================
ALTER TABLE approval_instance_ext ADD KEY idx_flow_node (flow_id, current_node_no);
ALTER TABLE sys_message ADD KEY idx_receiver_time (receiver_id, created_at);
ALTER TABLE return_store_order ADD KEY idx_status (status);
