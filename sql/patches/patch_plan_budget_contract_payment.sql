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
