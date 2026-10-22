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
