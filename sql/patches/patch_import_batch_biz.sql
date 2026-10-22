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
