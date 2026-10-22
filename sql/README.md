# sql/ · 数据库脚本与工具

物资全链路管理系统（`wuzi_test`）的**唯一数据库基线**：结构 + 基础种子 + 全部增量补丁。
纯 MySQL 8.0，`utf8mb4` / `utf8mb4_unicode_ci`，UTF-8 无 BOM。

## 一、目录结构

```
sql/
├── wuzi_full.sql          # ← 唯一初始化入口（结构 + 种子 + 27 个补丁效果）
├── patches/
│   ├── wuzi_test.sql      # 历史基线（未打补丁的原始库）
│   └── patch_*.sql        # 27 个增量补丁，按序幂等
├── InitDb.java            # JDBC 执行 SQL（按行切分，仅对非法 UTF-8 行回退 GBK）
├── SeedDemoChain.java     # 全链路演示数据（可重复执行）
├── EncCheck.java          # 编码体检：库内中文 HEX 抽样
├── FixDbEncoding.java     # 修复历史乱码
├── FixStockBalance.java   # 修 stock_record 与 stock.qty 的对账差额
├── FixStockRecordSign.java# 修流水方向符号
├── _merge_full.py         # 由 baseline + patches 重新生成 wuzi_full.sql
└── run-java-utf8.ps1      # 上述 Java 工具的编译执行入口
```

## 二、当前基线

| 指标 | 值 |
|---|---|
| 业务表 | **85 张** |
| 可执行语句 | **435 条**（按 `InitDb` 行切分口径） |
| 增量补丁 | **27 个** |
| 文件体积 | 152 KB |
| 实测结果 | 空库导入 **0 报错**，85 张表全部建成（MySQL 8.0） |

## 三、两种用法

### 1. 新库：一条命令

```powershell
# 方式 A（推荐）：JDBC 导入，自动处理编码
$env:WUZI_DB_PASSWORD = '你的数据库口令'
.\sql\run-java-utf8.ps1 init .\sql\wuzi_full.sql

# 方式 B：mysql 客户端（账号口令通过环境变量传入）
mysql -u "$env:WUZI_DB_USER" -p --default-character-set=utf8mb4 < sql/wuzi_full.sql
```

Docker Compose 已把本文件挂到初始化目录，`docker compose up` 时自动执行一次：

```yaml
- ./sql/wuzi_full.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
```

### 2. 已有旧库：按序补跑补丁

`wuzi_full.sql` 主库段是 `DROP + CREATE`，**不要**对生产库直接执行。
旧库升级只跑 `patches/` 里缺失的补丁，全部幂等、可重复执行：

```powershell
.\sql\run-java-utf8.ps1 init .\sql\patches\patch_material_alias.sql
```

## 四、补丁顺序（须严格遵守）

| # | 补丁 | 作用 |
|---|---|---|
| 01 | `patch_missing_tables` | 询价 / 调拨 / 盘点 / 借用 / 报废补表 |
| 02 | `patch_indexes` | 列表过滤排序索引（3 条依赖后续表的 ALTER 由合并器移到末尾） |
| 03 | `patch_dict` | 字典类型与明细基线 |
| 04 | `patch_dict_v2` | 字典 v2 扩容 |
| 05 | `patch_dict_price_source` | 价格来源字典 |
| 06 | `patch_dict_resubmit` | 驳回重提状态文案 |
| 07 | `patch_dict_status_labels` | 统一状态中文文案 |
| 08 | `patch_dict_use_and_source` | 领用类型 / 登录来源 |
| 09 | `patch_approval_msg` | 审批流定义、节点、站内信 |
| 10 | `patch_approval_flows` | 业务默认审批流种子 |
| 11 | `patch_finance` | 发票 / 结算 / 附件 / 供应商评价 |
| 12 | `patch_alert_return` | 退库单 + 预警记录 |
| 13 | `patch_org_position` | 组织职位（岗位）层 |
| 14 | `patch_purchase_line_tax` | 采购行税码快照 |
| 15 | `patch_button_perms` | 菜单 + 按钮权限码与角色预授 |
| 16 | `patch_flow_config_perms` | 审批流配置按钮权限 |
| 17 | `patch_org_tree_dept_type` | 部门 `dept_type` 与部门类型字典 |
| 18 | `patch_plan_budget_contract_payment` | 采购计划 / 预算 / 合同 / 付款 / 系统配置 / 任务锁（**必须在 15 之后**） |
| 19 | `patch_unit_project_ledger` | 计量单位 + 项目 / 成本中心 + 台账索引 |
| 20 | `patch_label_template_supplier_contact` | 标签模板 + 供应商联系人 |
| 21 | `patch_inquiry_approval` | 询价独立审批字段与按钮权限 |
| 22 | `patch_import_batch_biz` | 导入批次业务关联（支持真回滚） |
| 23 | `patch_apply_plan_link` | 申请回写 `plan_id` / `plan_no` |
| 24 | `patch_order_inquiry_link` | 订单回写 `inquiry_id` / `inquiry_no` |
| 25 | `patch_use_issue_warehouse` | 领用回写发料仓库 `issue_warehouse_id` |
| 26 | `patch_ai_assistant` | 智能助手 3 表 + `ai:chat` 权限 |
| 27 | `patch_material_alias` | 物资别名词典（口语化查询解析） |

> 新增或修改补丁后，务必重跑合并器，保持总 SQL 与补丁一致：
> `python sql/_merge_full.py`

## 五、85 张表清单

**组织 / 权限 / 系统（13）**
`sys_user` `sys_role` `sys_permission` `sys_user_role` `sys_role_permission` `sys_dept` `sys_position`
`sys_dict` `sys_dict_type` `sys_config` `sys_message` `sys_operation_log` `sys_job_lock`

**物资 / 供应商 / 仓库主数据（13）**
`material` `material_category` `material_unit` `material_alias`
`supplier` `supplier_contact` `supplier_product` `supplier_product_price` `supplier_eval_score` `supplier_import_record`
`warehouse` `warehouse_location` `label_template`

**税率与价格（2）**
`tax_rate_dict` `tax_price_adjust_log`

**采购（14）**
`purchase_plan` `purchase_plan_item` `purchase_apply` `purchase_apply_item`
`purchase_inquiry` `purchase_inquiry_item` `purchase_order` `purchase_order_item`
`purchase_arrival` `purchase_arrival_item` `purchase_contract` `purchase_contract_item`
`purchase_return` `purchase_return_item`

**财务 / 项目（8）**
`budget` `budget_control_log` `project` `contract_payment_node` `payment_order` `invoice` `settle_order` `settle_order_item`

**库存（13）**
`stock` `stock_batch` `stock_record` `stock_serial`
`inbound_order` `inbound_order_item` `outbound_order` `outbound_order_item`
`transfer_order` `transfer_order_item` `inventory_check` `inventory_check_item` `alert_record`

**领用 / 借用 / 退库 / 报废（9）**
`material_use_apply` `material_use_apply_item` `borrow_order` `borrow_order_item` `borrow_return_record`
`return_store_order` `return_store_order_item` `scrap_order` `scrap_order_item`

**审批 / 附件 / 导入（7）**
`approval_flow_def` `approval_flow_node` `approval_instance` `approval_instance_ext` `approval_record`
`attachment` `import_batch_biz`

**二维码溯源（3）**
`trace_qr_code` `trace_qr_print_log` `trace_scan_log`

**智能助手（3）**
`ai_chat_session` `ai_chat_message` `ai_tool_log`

## 六、编码与执行注意

| 事项 | 要求 |
|---|---|
| 文件编码 | 一律 UTF-8 **无 BOM**；禁止 ANSI / GBK / 混编 |
| 客户端 | `mysql --default-character-set=utf8mb4` |
| 行尾 | LF（仓库 `.gitattributes` 已固定 `*.sql eol=lf`） |
| 注释切分 | `InitDb` 按「行尾分号」切语句，单行多语句（`PREPARE;EXECUTE;DEALLOCATE`）会被判为一条而报错，故合并器已拆行 |
| 幂等 | 主库段可重复执行（DROP+CREATE）；补丁段用 `information_schema` 判断列/表是否已存在 |

## 七、账号与口令

脚本**不含明文口令，也不内置任何账号**。执行前必须提供两个环境变量：

```powershell
$env:WUZI_DB_USER     = 'wuzi_app'   # 数据库账号（建议专用业务账号，不要用超级管理员）
$env:WUZI_DB_PASSWORD = '你的口令'
```

也可以写到 `%USERPROFILE%\.wuzi\env`（**仓库外**，不受 git 影响）：

```
WUZI_DB_USER=wuzi_app
WUZI_DB_PASSWORD=你的口令
```

`run-java-utf8.ps1` 按「进程环境变量 → `%USERPROFILE%\.wuzi\env` → `sql/.env.local`」的顺序读取；
缺少任一项时直接报错退出，不会用默认账号或空口令连库。

## 八、演示数据

`wuzi_full.sql` 只建结构与基础种子，**不含**业务演示数据。
需要 10 物资 / 4 供应商 / 3 仓 7 库位 / 完整采购到付款链路时：

```powershell
$env:WUZI_DB_PASSWORD = '你的口令'
.\sql\run-java-utf8.ps1 seed
```

演示数据可重复执行（先清业务表再灌）。
