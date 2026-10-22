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
