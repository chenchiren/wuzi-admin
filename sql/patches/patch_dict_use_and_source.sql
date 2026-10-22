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
