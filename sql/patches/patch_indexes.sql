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
ALTER TABLE approval_instance_ext ADD KEY idx_flow_node (flow_id, current_node_no);

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
ALTER TABLE sys_message ADD KEY idx_receiver_time (receiver_id, created_at);

-- 补表（若尚未建过索引）
ALTER TABLE transfer_order ADD KEY idx_status (status);
ALTER TABLE inventory_check ADD KEY idx_status (status);
ALTER TABLE borrow_order ADD KEY idx_status_expect (status, expect_return_date);
ALTER TABLE scrap_order ADD KEY idx_status (status);
ALTER TABLE return_store_order ADD KEY idx_status (status);
