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
