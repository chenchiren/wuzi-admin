-- 审批动作补全：重新发起
USE wuzi_test;
SET NAMES utf8mb4;
INSERT INTO sys_dict (dict_type, dict_key, dict_label, sort_no) VALUES
('APPROVAL_ACTION','RESUBMIT','重新发起',60)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label), sort_no = VALUES(sort_no);
