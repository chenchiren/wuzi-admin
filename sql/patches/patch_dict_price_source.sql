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
