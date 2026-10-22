# 物资全链路管理系统

企业物资从供应商建档、采购、到货入库，到领用 / 调拨 / 借用 / 退库 / 盘点 / 报废的全生命周期数字化管理。

**覆盖形态**：PC 管理台 + 手机 H5 作业端 + 对外脱敏溯源页  
**核心能力**：税率含税价 · 二维码溯源 · 多级审批流 · 按钮级权限 · 组织职位 · 红冲闭环 · 离线扫码队列

| 项 | 值 |
|---|---|
| 后端 | Spring Boot 3.2 · Java 17 · MyBatis-Plus 3.5 · JWT（JJWT）· EasyExcel · MySQL |
| 包名 / 构件 | `com.relex.wuzi` · `wuzi-admin` |
| PC 前端 | Vue 3 · Vite · Element Plus + Naive UI + ECharts + Tailwind |
| 手机端 | Vue 3 · Vite H5（纯 CSS，无 UI 框架依赖）；根目录另有静态设计原型 `index.html` |
| 数据库 | MySQL 8.0（`utf8mb4` / `utf8mb4_unicode_ci`） |
| 端口 | 后端 `8081` · PC `5173` · 手机 `5174` · Docker Nginx `8080` |
| 接口前缀 | PC `/api/**` · 手机 `/api/m/**` · 对外溯源 `/api/trace/public/**` |

---

## 目录结构

| 路径 | 说明 |
|---|---|
| `物资全链路管理系统设计说明.md` | 总设计（业务边界、模块、状态机、API、MVP；含 12A 税价、12B 计划/预算/合同/付款、V1.3 实现增强） |
| `数据库表设计-税率与二维码溯源.md` | 税率 / 税价 / 溯源表设计与独立复审；V1.3 主数据扩展表 |
| `手机端设计说明.md` | 手机端 IA、扫码、离线弱网、移动端 API |
| `sql/` | `wuzi_full.sql` 统一初始化（结构+种子+27 个补丁）+ `patches/` 增量补丁归档 + Java 工具 |
| `sql/README.md` | 数据库脚本说明：两种导入方式、补丁顺序、85 张表清单、编码规范 |
| `CHANGELOG.md` | 版本更新记录（数据库基线与功能变更） |
| `backend/` | Spring Boot 单体（约 290 个 Java 源文件） |
| `backend/src/main/java/com/relex/wuzi/web/` | PC 业务控制器 |
| `backend/src/main/java/com/relex/wuzi/web/mobile/` | 手机端控制器 |
| `backend/src/main/java/com/relex/wuzi/web/report/` | 高级报表 |
| `frontend/` | PC 管理台（47 业务页 + 登录/布局） |
| `frontend/mobile/` | 手机 H5（15 页，含离线队列） |
| `deploy/nginx.conf` | 生产反向代理（`/api/` → `backend:8081`，SPA `try_files`） |
| `scripts/` | 备份、回归、链路测试、API 对齐审计 |
| `sql/*.java` | 导入与数据修复工具（见「运维工具」；口令从 `WUZI_DB_PASSWORD` 环境变量读取） |
| `index.html` | 手机端**静态设计原型**（AI 生成演示稿，非生产代码；生产 H5 在 `frontend/mobile/`） |
| `docker-compose.yml` | MySQL + 后端 + Nginx 编排 |
| `Dockerfile` | 后端镜像（Temurin 17 JRE） |
| `.env.example` | 环境变量模板 |

---

## 快速启动

### 1. 数据库初始化

```powershell
# 账号口令不写进仓库：设置环境变量，或写入 %USERPROFILE%\.wuzi\env（仓库外）
$env:WUZI_DB_USER     = 'wuzi_app'
$env:WUZI_DB_PASSWORD = '你的数据库口令'

# 统一初始化（结构 + 基础种子 + 全部 27 个补丁效果，一个文件；编码必须 UTF-8，见 1.1 编码规范）
.\sql\run-java-utf8.ps1 init .\sql\wuzi_full.sql
```

`sql/wuzi_full.sql` = 原 `wuzi_test.sql` 主库与全部 27 个增量补丁的合并文件（已验证 435 条语句全部执行成功、85 张表结构与库内一致，空库导入 0 报错），**新库只需执行这一个文件**。数据库脚本说明见 [`sql/README.md`](sql/README.md)，版本变更见 [`CHANGELOG.md`](CHANGELOG.md)。

全链路演示数据另跑 `.\sql\run-java-utf8.ps1 seed`（见 1.1）。

### 1.0.1 已有库增量升级（补丁归档于 `sql/patches/`）

历史库不重灌时，按序补跑缺失的补丁（全部幂等，重复执行安全）：

| 补丁 | 作用 |
|---|---|
| `patch_missing_tables` | 询价 / 调拨 / 盘点 / 借用 / 报废等补表 |
| `patch_indexes` | 列表过滤排序索引 |
| `patch_dict*` | 字典类型/明细、状态文案、价格来源、领用类型、登录来源、审批动作 |
| `patch_approval_msg` | 审批流定义 / 节点 + 站内信 |
| `patch_approval_flows` | 业务默认审批流种子 |
| `patch_finance` | 发票 / 结算 / 附件 / 供应商评价扩展 |
| `patch_alert_return` | 退库单 + 预警记录 |
| `patch_org_position` | 组织职位（岗位）层 |
| `patch_org_tree_dept_type` | 部门树 `dept_type` 字段与部门类型字典 |
| `patch_purchase_line_tax` | 采购行税码快照 |
| `patch_button_perms` | 菜单 + 按钮权限码、角色预授 |
| `patch_flow_config_perms` | 审批流配置按钮权限 |
| `patch_plan_budget_contract_payment` | 采购计划/预算/合同/付款/系统配置/任务锁 + 对应字典与权限（须在 `patch_button_perms` **之后**执行） |
| `patch_unit_project_ledger` | 计量单位字典 `material_unit` + 项目/成本中心 `project` + 台账索引/字典/权限 |
| `patch_label_template_supplier_contact` | 标签模板 `label_template` + 供应商联系人 `supplier_contact` + 权限 |
| `patch_inquiry_approval` | 询价独立审批字段与按钮权限 |
| `patch_import_batch_biz` | 导入批次业务关联表（支持真回滚）+ 导入类型字典 |
| `patch_apply_plan_link` | 采购申请回写 `plan_id`/`plan_no`（计划行转申请关联） |
| `patch_order_inquiry_link` | 采购订单回写 `inquiry_id`/`inquiry_no`（询价转订单关联） |
| `patch_use_issue_warehouse` | 领用申请回写发料仓库 `issue_warehouse_id`（列表反查扣减来源） |
| `patch_ai_assistant` | 智能助手 3 表（会话/消息/工具审计）+ 权限码 `ai:chat` 全角色预授 |
| `patch_material_alias` | 物资别名词典（"复印纸"→"A4打印纸"），智能助手口语化查询解析 |

```powershell
# 示例：补跑单个补丁
.\sql\run-java-utf8.ps1 init .\sql\patches\patch_use_issue_warehouse.sql
```

> 补丁有新增或修改时：放入 `sql/patches/`，重跑 `python sql/_merge_full.py` 再生成 `sql/wuzi_full.sql`，保持总 SQL 与补丁一致。

也可用 `mysql --default-character-set=utf8mb4 < sql/wuzi_full.sql` 导入。**禁止**用 ANSI/GBK 客户端直接灌 SQL。

### 1.1 全链路演示数据（可重复执行）

```powershell
# 编码规范：源码 UTF-8；必须 javac -encoding UTF-8（见下方「编码规范」）
.\sql\run-java-utf8.ps1 seed
```

| 链路 | 演示内容 |
|---|---|
| 主数据 | 10 种物资（量管/件管/批次）·4 供应商（税率/纳税人/备注）·3 仓 7 库位 · 价格历史关旧开新 |
| 采购 | 计划→申请→询价→订单（税价七件套）→到货→按单入库（含 1 张 DRAFT 待收） |
| 库存/领用 | 批次/件码/流水 · 领用发料 · 退库三段备注 |
| 借还/调拨/盘点/报废 | 长期借用（扫码见借期）· 部分归还 · 在途差异 · 盘亏 · 强制报废 |
| 财务 | 预算消耗日志 · 框架/普通合同 · 付款节点与付款单 |
| 溯源 | SKU/BATCH/SERIAL 码 · 打印日志 · 扫码日志 · 预警 |

重跑会 `DELETE` 业务表后全量插入；`sys_*` 用户角色权限字典不动。  
校验乱码：`javac -encoding UTF-8 -cp $jar -d sql/out sql/EncCheck.java` 后 `java -Dfile.encoding=UTF-8 -cp "sql/out;$jar" EncCheck`（看 `HEX(material_name)` 是否为合法 UTF-8）。

**编码规范（全项目）**

| 项 | 约定 |
|---|---|
| 源码 / SQL / 文档 | 一律 UTF-8（无 BOM） |
| Java 编译 | `javac -encoding UTF-8`；运行加 `-Dfile.encoding=UTF-8` |
| JDBC | URL 含 `useUnicode=true&characterEncoding=utf8` |
| MySQL | `utf8mb4` / `utf8mb4_unicode_ci`（或 `utf8mb4_0900_ai_ci`） |
| 前端 | Vite/HTML `charset=utf-8`；API JSON 为 UTF-8 |
| 禁止 | GBK/ANSI 保存中文；PowerShell 管道写 `.java`/`.sql`（易带 BOM）；`java Xxx.java` 源码直跑 |

### 2. 后端（:8081）

```powershell
# 生产先设 WUZI_DB_* / WUZI_JWT_SECRET（见 .env.example）
cd backend
mvn spring-boot:run
# 或
java -jar target/wuzi-admin-1.0.0-SNAPSHOT.jar
```

启动时 `DataInitializer` 会确保 `admin/wh01/pur01/rd01` 密码可登录（`admin123`）。

### 3. PC 前端（:5173）

```powershell
cd frontend
npm install
npm run dev
```

### 4. 手机端（:5174）

```powershell
cd frontend/mobile
npm install
npm run dev    # /api 代理到 http://localhost:8081
```

### 5. Docker（可选）

```powershell
# 先构建后端 jar，再：
docker compose up -d
# MySQL :3306 · 后端 :8081 · PC 前端 Nginx :8080
```

Compose 会自动执行 `sql/wuzi_full.sql`（结构 + 种子 + 全部补丁效果）一次完成初始化；已有旧库增量升级见「1.0.1 补丁归档」。

### 默认账号

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | `ADMIN` 系统管理员（全量权限） |
| `wh01` | `admin123` | `WAREHOUSE` 仓管 |
| `pur01` | `admin123` | `PURCHASER` 采购 |
| `rd01` | `admin123` | `EMPLOYEE` 员工 |

角色池另含：`DEPT_MANAGER` 部门负责人、`FINANCE` 财务。  
组织职位（与角色分离）：部长 / 副部长 / 主管等，在「系统管理 → 职位」维护，供审批节点解析审批人。  
**首次部署后请立刻改密。**

---

## 环境变量

模板见 [`.env.example`](.env.example)。后端绑定项：

| 变量 | 配置键 | 说明 |
|---|---|---|
| `WUZI_DB_URL` | `spring.datasource.url` | JDBC，**必须**含 `useUnicode=true&characterEncoding=utf8` |
| `WUZI_DB_USER` / `WUZI_DB_PASSWORD` | — | 数据库账号 |
| `WUZI_JWT_SECRET` | `wuzi.jwt.secret` | JWT 密钥（≥32 字符，生产必改） |
| `WUZI_JWT_EXPIRE_HOURS` | `wuzi.jwt.expire-hours` | Token 有效期小时，默认 24 |
| `WUZI_CORS_ORIGINS` | `wuzi.cors.allowed-origins` | CORS 白名单，逗号分隔 |
| `WUZI_WECOM_MOCK` | `wuzi.wecom.mock` | 企微免登 mock，生产 `false` |
| — | `wuzi.wecom.corp-id` / `agent-id` / `secret` | 企微真实 OAuth |
| `WUZI_DINGTALK_MOCK` | `wuzi.dingtalk.mock` | 钉钉免登 mock，生产 `false` |
| — | `wuzi.dingtalk.corp-id` / `agent-id` / `app-key` / `app-secret` | 钉钉真实 OAuth |
| — | `wuzi.alert.expiry-days` | 临期预警天数，默认 30 |
| — | `wuzi.label.*` | 标签模板（宽/高/dpi/标题/二维码模块），缺省 50×30mm |

企微/钉钉 mock 模式：`code=demo`（或 `mock*`）直接签发本地 JWT。

---

## 功能总览

### 基础资料

| 模块 | 能力 | 主要 API |
|---|---|---|
| 登录认证 | 账密登录、当前用户、权限码下发 | `POST /api/auth/login` · `GET /api/auth/me` · `GET /api/auth/perms` |
| 用户 | 列表/详情/新建/更新、重置密码、分配角色 | `GET/POST /api/users` · `GET/PUT /api/users/{id}` · `PUT /{id}/password` · `POST /{id}/roles` |
| 角色 / 权限 | 角色 CRUD、全量角色、权限树、角色授权 | `/api/roles` `/api/roles/all` `/api/roles/{id}/permissions` `/api/permissions` `/api/permissions/tree` |
| 部门 | 列表、部门树、新建、更新 | `GET /api/depts` · `GET /depts/tree` · `POST /depts` · `PUT /depts/{id}` |
| **职位（岗位）** | 组织职务（部长/副部长…），与角色分离 | `GET /api/positions` · `GET /positions/all` · `POST` · `PUT/{id}` · `DELETE/{id}` |
| 字典 | 类型 / 明细 / 目录 / 映射 | `/api/dicts/**` |
| **计量单位** | 编码唯一、分类（长度/重量/数量/体积）、相对基准换算系数 | `/api/units` `/api/units/all` |
| **项目 / 成本中心** | `PROJECT` / `COST_CENTER` 主数据 CRUD | `/api/projects` `/api/projects/all` |
| 物资分类 | 分类树 CRUD | `/api/categories` |
| 物资档案 / 供应商 / 产品 | 档案、产品目录、价格 | `/api/materials` `/api/suppliers` `/api/supplier-products` |
| 供应商评价 | 准时率 / 退货率 / 价格分，权重可配（默认 40/30/30） | `GET /api/suppliers/{id}/summary` · `/eval` · `POST /eval/recalc` · `GET /eval/history` |
| 税率字典 | 启停、默认税率 | `/api/tax-rates` |
| Excel 导入中心 | 供应商/产品预览确认、模板下载、错误明细、批次回滚 | `/api/import/**` |
| 附件 | 按业务列取、上传（multipart）、下载、删除 | `GET /api/attachments?bizType=&bizId=` · `POST` · `GET /{id}/download` · `DELETE /{id}` |

### 采购与财务

| 模块 | 能力 | 主要 API |
|---|---|---|
| 采购申请 | 明细行 CRUD、提交/作废、`@DataScope` | `/api/purchase/applies` |
| 询价 | 从申请生成、录报价、选定供应商、完成 | `/api/purchase/inquiries` |
| 采购订单 | 列表/详情/**创建**、含税价落库、提交/作废 | `GET/POST /api/purchase/orders` · `/{id}/submit|cancel` |
| 到货验收 | 按订单到货、验收、完成、作废 | `/api/purchase/arrivals` |
| 采购退货 | 提交/确认/取消/删除 + **红冲** | `/api/purchase/returns` |
| 发票 | 列表/详情/**登记**、**三单匹配** | `GET/POST /api/invoices` · `POST /{id}/match` |
| 结算 | 列表/详情/**生成结算**、订单行余额 | `GET/POST /api/settles` · `GET /order-items/{orderItemId}/balance` |
| 价格趋势 | 历史价格、明细 | `/api/price-trends` `/api/price-trend-details` |
| 价税计算 | 唯一税价入口 | `POST /api/price/calc` · `GET /api/price/example` |
| **采购计划** | 需求汇总、提交审核、计划行批量转采购申请（`/{id}/to-apply`） | `/api/purchase-plans` |
| **合同管理** | 行税价、付款节点、生效冻结预算 | `/api/contracts` |
| **预算控制** | 年度/部门预算、冻结/消耗/释放 | `/api/budgets` |
| **付款应付** | 付款单、核销合同节点、账龄 | `/api/payments` · `/ap-aging` |

### 仓储与库存

| 模块 | 能力 | 主要 API |
|---|---|---|
| 仓库 / 库位 | 多仓多库位 CRUD | `/api/warehouses` `/api/warehouses/{id}/locations` |
| 入库 | 分页/详情/**创建**、确认入库、入库红冲 | `GET/POST /api/inbound/orders` · `POST /{id}/confirm` |
| 出库 | 分页/详情/创建、提交/确认、出库红冲 | `GET/POST /api/outbound/orders` · `POST /{id}/submit|confirm|red-flush` |
| 库存 | 余额、流水、批次、库存预警查询 | `/api/stock/records` `/batches` `/alerts` |
| 批次 / 序列号 | `stock_batch` / `stock_serial` | `StockService` |
| **序列号 / 批次台账** | 台账分页（物资/仓库/库位联显）、流转记录抽屉 | `GET /api/stock/serials` · `/serials/{no}/trail` · `/batches` · `/batches/{id}/trail` |
| 调拨 | 提交、调出、调入 | `/api/transfer/orders` |
| 盘点 | 列表/详情/**创建**、提交/确认调账；抽盘、按库位向导、回填账面、导出 | `GET/POST /api/inventory/checks` · `/spot` · `/location-wizard` · `/{id}/fill-book` · `/{id}/export` · `/{id}/submit|confirm` |
| 库存预警 | 低库存 / 超库存 / 临期批次 / 预警记录处理 | `/api/alerts/low-stock` `/over-stock` `/expiry` `/record` |
| 红冲 | 采购退货 / 入库 / 出库红冲 | `/api/red-flush/**` |

### 资产作业

| 模块 | 能力 | 主要 API |
|---|---|---|
| 领用 | 列表/详情/创建/更新 → 审批 → 发料（**必选发料仓库**，回写申请单）；发料记录 | `GET/POST /api/material-use` · `PUT/{id}` · `/{id}/submit|approve|issue` · `/{id}/issue-records` |
| 借用 | 创建 → 审批 → 借出 / 归还；逾期清单 / 归还记录 | `GET/POST /api/borrows` · `/{id}/submit|approve|lend|return` · `/overdue` · `/{id}/return-records` |
| 退库 | 创建 → 提交 → 验收 → 确认入库（可驳回/取消/删除） | `GET/POST /api/return-stores` · `/{id}/submit|accept|reject|confirm|cancel` · `DELETE /{id}` · 流水 `/api/return-store/records` |
| 报废 | 创建 → 提交 → 确认报废 / 驳回 | `GET/POST /api/scraps` · `/{id}/submit|confirm|reject` |

### 溯源与标签

| 模块 | 能力 | 主要 API |
|---|---|---|
| 二维码 | 生成 / 激活 / 列表 / 详情 | `/api/trace/qr/**` |
| 对外脱敏溯源 | `trace_token` 公开页（隐藏内部价） | `GET /api/trace/public/{traceToken}` |
| 扫码日志 | 打印日志、扫码流水 | `/api/trace/print-logs` · `TraceScanService` |
| 标签打印 | ZPL（Zebra）/ 可打印 HTML / 模板 / 码数据 | `/api/labels/zpl/{qrId}` `/html/{qrId}` `/template` `/data/{qrId}` |

### 审批 · 消息 · 分析 · 系统

| 模块 | 能力 | 主要 API / 页面 |
|---|---|---|
| **智能助手** | AI 对话查数（库存/批次/单据/待办/预警/价格/报表/供应商，8 个只读工具，按角色裁剪；本地 vLLM OpenAI 兼容协议；SSE 流式；限流熔断审计） | `POST /api/ai/chat`（SSE）· `GET /api/ai/sessions` · `GET /api/ai/sessions/{id}/messages` · `DELETE /api/ai/sessions/{id}` · `GET /api/ai/status` |
| 多级审批流 | 流程/节点配置、发起、待办、转交、加签、驳回后重提 | `/api/flow-approvals/**` · `FlowConfigs.vue` |
| 审批中心 | 待办 / 我发起 / 按业务查询、通过/驳回 | `/api/approvals/**` · `Approvals.vue` |
| 统一状态机 | 见下文 | 各业务 `/{id}/submit` |
| 消息中心 | 站内信、未读数、单条/全部已读、发送 | `GET /api/messages` · `GET /unread-count` · `POST /{id}/read` · `POST /read-all` · `POST /send` |
| 工作台 | 待办计数、快捷入口、趋势图 | `Home.vue` · `GET /api/stats/dashboard` |
| 基础统计 | 供应商采购排行、出入库 | `/api/stats/supplier-purchase-rank` `/inout` |
| 高级报表 | 月度采购/出入库趋势、分类/仓库库存、部门消耗、供应商排行、CSV 导出 | `/api/reports/**` |
| 系统配置 | 键值参数维护（`sys_config`） | `GET/POST /api/configs` · `GET /configs/key/{key}` · `DELETE /{id}` · `Configs.vue` |
| 操作日志 | `@OpLog` 切面安全落库、分页查询；客户端也可上报 | `GET /api/logs` · `POST /api/logs` |

### 手机端 `/api/m/**`

| 能力 | 页面 / API |
|---|---|
| 登录 + 企微/钉钉免登 | `Login.vue` · `POST /api/m/auth/login` · `/api/m/auth/wechat-work/login` · `/api/m/auth/dingtalk/login` |
| 首页待办、作业宫格 | `Home.vue` · `GET /api/m/home` `/todos` |
| 全局扫码解析（URL token / 码值 / 序列号 / 批号） | `Scan.vue` · `POST /api/m/scan/resolve` · `GET /recent` |
| 扫码入库 / 出库 | `Inbound.vue` `Outbound.vue` · `/api/m/inbounds/**` `/outbounds/**` |
| 到货验收 | `Arrival.vue` · `/api/m/arrivals/**` |
| 领用申请 | `UseApply.vue` · `POST /api/m/material-use` |
| 待我审批 | `Approvals.vue` · `POST /api/m/approvals/{id}/approve|reject` |
| 库存查询 / 盘点 | `Stocks.vue` `Inventory.vue` · `GET /api/m/stocks` |
| 调拨 / 借还 / 消息 | `Transfers.vue` `Borrows.vue` `Messages.vue` |
| 溯源 | `Trace.vue` · `GET /api/m/trace/{traceToken}` |
| **离线队列** | `offline.js` · 扫码日志/盘点草稿/作业动作本地暂存 → `POST /api/m/sync/batch` |
| 我的 | `Me.vue` · 同步状态、退出 |

设计细节见 [`手机端设计说明.md`](手机端设计说明.md)、`frontend/mobile/README.md`。

### PC 侧栏（8 组）

| 组 | 页面 |
|---|---|
| 基础数据 | 物资档案 · 物资分类 · 供应商 · **计量单位** · **项目/成本中心** · 价格趋势 · 税率字典 |
| 采购管理 | 采购计划 · 采购申请 · 询价 · 采购订单 · **合同管理** · 采购退货 · 审批待办 · 到货验收 |
| 仓储作业 | 仓库库位 · 入库 · 出库 · 库存 · **序列/批次台账** · 调拨 · 盘点 |
| 领用资产 | 领用 · 退库 · 借用 · 报废 · 库存预警 |
| 财务结算 | 发票结算 · 预算控制 · 付款应付 |
| 溯源标签 | 二维码溯源 · 标签打印 · **标签模板** · **对外公开溯源** |
| 分析报表 | 统计看板 · 高级报表 |
| 系统管理 | 消息中心 · 审批流配置 · 用户 · **职位** · 部门 · 角色权限 · 字典管理 · **系统配置** · Excel 导入 · 操作日志 |

工作台 `/home` 独立入口。共 **47 业务页**。

---

## API 通用约定

| 约定 | 说明 |
|---|---|
| 统一响应 | `{ "code": 200, "msg": "ok", "data": … }`（`common/R`）；业务失败 `code=500` 或 `BizException`，未认证 `401` |
| 分页 | `data = { "total": n, "records": [...] }`（`common/PageResult`）；查询参数 `page`（默认 1）、`size` |
| 认证 | `Authorization: Bearer <jwt>`；过滤器注入 `@RequestAttribute userId` / `userName` / `roles` |
| 附件 | `multipart/form-data` 字段 `file` + `bizType` / `bizId` |
| 操作日志 | 服务端 `@OpLog`；也可 `POST /api/logs` 由客户端补记 |
| 单号 | `BillNoUtil` 生成（如 `CG-`/`RK-`/`CK-` 前缀） |
| 二维码 SVG | `common/QrSvg` 服务端生成，无外部图片服务 |

---

## 核心业务闭环

```text
计划/财务：采购计划 ─审批→ 计划行转申请（to-apply 生成采购申请）
           预算 ←FREEZE─ 合同生效 ─→ 付款节点 ─→ 付款确认 ─CONSUME→ 预算

采购：  采购申请 ─审批→ 询价比价 → 采购订单(含税) ─审批→ 到货验收
        → 采购入库 → 库存 → 发票三单匹配 → 结算
        ↘ 退货：采购退货 ─审批→ 确认退货（红冲库存 + amount_sign=-1）

仓储：  入库 / 出库 / 调拨(调出→调入) / 盘点(→调账)
        ↘ 错单：入库红冲 / 出库红冲（状态 RED_FLUSHED）

资产：  领用申请 ─审批→ 发料出库
        借用 ─审批→ 借出 → 归还 / 逾期
        退库 → 仓管验收 → 确认入库
        报废 ─审批→ 确认报废

溯源：  生成码 → 激活 → 打印贴标(ZPL/HTML) → 扫码流转
        → 全链路日志 → 对外脱敏查询
```

### 统一状态机

```text
DRAFT → PENDING_APPROVAL → APPROVED → 执行态（COMPLETED / ISSUED / LENT / …）
              ↓
          REJECTED  →（可 RESUBMIT 重新发起，同实例从首节点重走）
作废分支：CANCELLED
红冲终态：RED_FLUSHED
```

`ApprovalService` 常量：`DRAFT` / `PENDING_APPROVAL` / `APPROVED` / `REJECTED` / `COMPLETED` / `CANCELLED`。  
`FlowApprovalService` 动作：`START` / `APPROVE` / `REJECT` / `TRANSFER` / `ADD_SIGN`（+ 驳回后 `RESUBMIT`）。  
> 兼容注：领用模块历史数据状态值为 `SUBMITTED`，与 `PENDING_APPROVAL` 同义；审批接口两者均接受，新写入统一 `PENDING_APPROVAL`。

### 审批流接入一览

| 业务 | 提交 | 审批后执行 | 默认流程 |
|---|---|---|---|
| 采购申请 | `/purchase/applies/{id}/submit` | 询价等下游 | 部门负责人→财务 |
| 采购订单 | `/purchase/orders/{id}/submit` | 到货 | 财务 |
| 采购退货 | `/purchase/returns/{id}/submit` | 确认退货（红冲） | 采购→财务 |
| 领用 | `/material-use/{id}/submit` | 发料 | 部门负责人→仓管 |
| 借用 | `/borrows/{id}/submit` | 借出 | 部门负责人 |
| 报废 | `/scraps/{id}/submit` | 确认报废 | 部门负责人→仓管 |
| 调拨 | `/transfer/orders/{id}/submit` | 调出 | 仓管 |
| 退库 | `/return-stores/{id}/submit` | 仓管验收 | 部门负责人 |
| 盘点 | `/inventory/checks/{id}/submit` | 确认调账 | 仓管 |
| 到货 | `/purchase/arrivals/{id}/submit` | 确认到货 | 仓管 |
| 出库 | `/outbound/orders/{id}/submit` | 确认出库 | 仓管 |

审批节点审批人类型：`USER` / `ROLE` / `POSITION` / `DEPT_LEADER`（`OrgPositionService` 解析）。  
范围：`ALL` / `SELF` / `PARENT`。流程可在「审批流配置」维护。

---

## 权限与安全

系统同时使用 **角色（权限）** 与 **职位（组织职务）** 两套身份。

| 层 | 实现 |
|---|---|
| 认证 | JWT Bearer；无 Token / 无效 Token 一律 `401` |
| 路径级 RBAC | `RoleAccessFilter` + `ApiAccessRules`（系统管理仅 ADMIN；采购/仓储/财务写操作按角色） |
| 按钮级权限 | `@RequirePerm` + `PermAspect`；未标注写接口由 `PermCodeResolver` 按路径兜底 |
| 权限码 | `资源:动作`，如 `po:apply:create`、`wh:inbound:confirm`、`fin:invoice:match` |
| 前端控制 | `v-perm="'po:apply:create'"` 指令（`directives/perm.js` + `utils/perm.js`） |
| 角色分配 | 角色页「权限」树勾选 MENU + BUTTON |
| 数据权限 | `@DataScope` + `DataScopeAspect`（采购申请、领用申请、发票、结算等按部门/创建人） |
| 操作日志 | `@OpLog` + `OpLogAspect`，失败不影响主流程 |
| CORS | 环境变量白名单 |
| 密钥 | DB / JWT / 企微钉钉密钥走环境变量 |

### 权限码前缀（`PermCodeResolver`）

| 前缀 | 码基 | 示例动作 |
|---|---|---|
| `/api/users` | `sys:user` | `create` `update` `reset-pwd` `assign-role` |
| `/api/roles` | `sys:role` | `create` `update` `delete` `assign-perm` |
| `/api/depts` | `sys:dept` | `create` `update` |
| `/api/positions` | `sys:position` | `create` `update` `delete` |
| `/api/materials` / `categories` | `base:material` / `base:category` | `create` `update` `delete` |
| `/api/suppliers` / `supplier-products` | `base:supplier` / `base:supplier-product` | 同上 |
| `/api/tax-rates` | `base:tax-rate` | `create` `update` `toggle` `delete` |
| `/api/purchase/applies` | `po:apply` | `create` `submit` `cancel` `delete` |
| `/api/purchase/inquiries` | `po:inquiry` | `create` `quote` `select` |
| `/api/purchase/orders` | `po:order` | `create` `submit` `cancel` |
| `/api/purchase/returns` | `po:return` | `create` `submit` `confirm` `cancel` `delete` |
| `/api/purchase/arrivals` | `po:arrival` | `create` `submit` `complete` `cancel` |
| `/api/invoices` / `settles` | `fin:invoice` / `fin:settle` | `create` `match` |
| `/api/warehouses` | `wh:warehouse` / `wh:location` | `create` `update` `delete` |
| `/api/inbound` `/outbound` `/transfer` `/inventory` | `wh:inbound` 等 | `create` `submit` `confirm` `out` `in` `red-flush` |
| `/api/material-use` | `asset:use` | `create` `submit` `approve` `issue` |
| `/api/return-stores` | `asset:return` | `create` `submit` `accept` `confirm` |
| `/api/borrows` / `scraps` | `asset:borrow` / `asset:scrap` | `create` `submit` `lend` `return` `confirm` |
| `/api/trace` / `labels` | `trace:qr` / `label` | `generate` `activate` |
| `/api/alerts` / `messages` / `import` | `alert` / `msg` / `sys:import` | `handle` `read-all` `create` |
| `/api/flow-approvals/flows` | `flow:config` | `create` `update` `delete` |
| `/api/approvals` | `approval` | `approve` `reject` `transfer` `add-sign` |

执行按钮权限种子后**重新登录**生效：

```powershell
.\sql\run-java-utf8.ps1 init .\sql\patches\patch_button_perms.sql
.\sql\run-java-utf8.ps1 init .\sql\patches\patch_flow_config_perms.sql
```

菜单码控制侧栏可见性，按钮码控制操作；ADMIN 全量放行。

---

## 税价与库存约定

### 税价（`PriceTaxCalculator` 唯一入口）

```text
Pn = ROUND(Pi / (1 + t/100), 6)   # 不含税单价
Ai = ROUND(Pi * Q, 2)             # 含税金额
An = ROUND(Pn * Q, 2)             # 不含税金额
T  = Ai - An                      # 税额，保证 Ai = An + T
```

- 税率存**百分数值**（`13.00` = 13%），禁止存 `0.13`。
- 单价 `DECIMAL(18,6)`，金额 `DECIMAL(18,2)`，应用层禁止 float。
- 舍入偏差只告警写 `tax_price_adjust_log`，不改写 `T`。

### 库存（`StockService`）

- 入库增加、出库校验可用量并扣减、红冲回滚；`stock.version` 乐观锁。
- `stock_record.amount_sign`：`+1` 正向 / `-1` 反向（出库、红冲）。
- `change_qty` 为带符号实际变动量；金额字段存**绝对值**，用 `amount_sign` 表达方向。
- 一切汇总必须 `SUM(amount * amount_sign)`。
- 批次 / 序列号独立表跟踪；全量流水，禁止旁路改 `stock` 表。

---

## 定时任务

| 任务 | 说明 |
|---|---|
| `StockAlertJob` | 每日扫描临期批次（`wuzi.alert.expiry-days`，默认 30 天）、低库存、超库存，写入 `alert_record` |
| `OpsJobs` | 借用逾期、合同付款节点提醒、供应商评价重算（`sys_job_lock` 防重） |

## 可观测与契约

| 项 | 入口 |
|---|---|
| OpenAPI / Swagger | `/swagger-ui.html` · `/v3/api-docs` |
| 健康/指标 | `/actuator/health` · `/actuator/metrics` · `/actuator/prometheus` |
| 链路 ID | 请求头 `X-Request-Id`，日志 pattern 含 `%X{requestId}` |

---

## 数据库字符集规定

本规定来自真实故障：种子 SQL 混用 UTF-8/GBK，初始化时整文件按 GBK 重解，导致中文字段写成乱码。**全链路只允许一种编码：UTF-8。**

| 环节 | 规定 |
|---|---|
| 数据库 | 库/表/列统一 `utf8mb4` |
| SQL 文件 | `sql/*.sql` 必须是**无 BOM 的 UTF-8**；禁止同文件混用 GBK/GB18030 |
| 导入工具 | `InitDb` 按行优先 UTF-8；仅非法 UTF-8 行回退 GBK。**禁止**「发现 U+FFFD 就整文件按 GBK 重解」 |
| JDBC | 连接串必须带 `useUnicode=true&characterEncoding=utf8` |
| 应用 | Java / Vue / JSON 均为 UTF-8；勿改 `charset=GBK` |
| 日志 | 控制台乱码以库内 `HEX(col)` 与 API 响应为准，勿反向「修」库 |

落库后抽检：

```sql
SELECT id, username, real_name, HEX(real_name) FROM sys_user ORDER BY id;
SELECT tax_code, tax_name, HEX(tax_name) FROM tax_rate_dict ORDER BY id;
-- 正确 HEX 示例：系统管理员 = E7B3BBE7BB9FE7AEA1E79086E59198
-- 出现 EF BF BD（U+FFFD）或明显 GBK 误读 → 编码已坏
```

接口抽检：`POST /api/auth/login` 的 `data.realName`、`GET /api/tax-rates` 的 `taxName` 应为可读中文。

**禁止**：UTF-8 再按 GBK 转码写入；SQL 另存为 ANSI/GBK 导入；去掉 JDBC `characterEncoding=utf8`；整库 GBK 往返转码。已损坏时用 `sql/FixDbEncoding.java` 修核心种子表。

---

## 后端结构速览

```text
com.relex.wuzi
├── annotation/     @RequirePerm @DataScope
├── aspect/         PermAspect · DataScopeAspect · OpLogAspect
├── common/         R · PageResult · BizException · BillNoUtil · QrSvg · GlobalExceptionHandler
├── config/         WebConfig · MybatisPlusConfig · DataInitializer · SchedulingConfig · DataScopeAopConfig
├── domain/         80 实体（采购/仓储/资产/财务/溯源/系统）
├── job/            StockAlertJob · OpsJobs
├── mapper/         82 Mapper
├── security/       JwtAuthFilter · JwtUtil · RoleAccessFilter · ApiAccessRules · PermCodeResolver
├── service/        PriceTaxCalculator · StockService · ApprovalService · FlowApprovalService
│                   SupplierEvalService · OrgPositionService · PermissionService
│                   InboundOutboundRedFlushService · ReturnRedFlushService
│                   ExcelImportService · LabelPrintService · MessageService · TraceScanService …
└── web/            57 PC 控制器 + mobile/ 7 + report/ 1
```

---

## 运维工具与脚本

### `sql/` 下 Java 工具

| 工具 | 用途 |
|---|---|
| `InitDb.java` | 建库并执行 SQL：UTF-8 优先，**仅非法 UTF-8 行**回退 GBK（禁止整文件 GBK 重解） |
| `FixDbEncoding.java` | 批量修复核心种子表中文乱码（含税率字典） |
| `FixStockBalance.java` | 写入 `BALANCE_FIX` 流水，使 `SUM(change_qty)` 与 `stock.qty` 对齐 |
| `FixStockRecordSign.java` | 修复历史 `change_qty` 符号，保证 `before_qty + change_qty = after_qty` |

> 这些工具内含本地调试用 JDBC 账号，**不要直接用于生产**；生产改用环境变量连接串。

### `scripts/`

| 项 | 用途 |
|---|---|
| `backup-db.ps1` | mysqldump 备份（参数：Host/Port/User/Database/OutDir） |
| `e2e-smoke.ps1` | 接口冒烟（登录 + 核心读写） |
| `chain-full-test.ps1` | 全链路业务串联 + 库存一致性 |
| `regression-full.ps1` | 回归 |
| `check-stock-balance.ps1` | 翻页拉 `stock_record` 校验余额 |
| `audit_api_alignment.py` | 前端 `api.get/post/...` 调用 vs 后端 Controller 路径对齐审计 |

### 单元测试

| 项 | 用途 |
|---|---|
| `PriceTaxCalculatorTest` | 税价恒等式与舍入 |
| `PermCodeResolverTest` | 路径 → 按钮权限码 |
| `ApiAccessRulesTest` | 路径级 RBAC 规则 |
| `BudgetMathTest` | 预算冻结/消耗/释放 |
| `ContractPaymentStateMachineTest` | 合同/付款状态机 |
| `BillNoUtilTest` | 单号生成 |

```powershell
cd backend
mvn test
```

---

## 前端共享模块与设计 Token

| 模块 | 说明 |
|---|---|
| `frontend/src/api/request.js` | axios 封装、统一 Token / 错误处理 |
| `frontend/src/utils/perm.js` + `directives/perm.js` | `v-perm` 按钮权限 |
| `frontend/src/utils/dict.js` | 字典码 → 中文文案 |
| `frontend/src/components/ApprovalFlowDialog.vue` | 审批流程节点弹窗 |
| `frontend/src/components/StatusTag.vue` | 单据状态标签 |
| `frontend/src/components/TaxRateSelect.vue` | 税率选择（字典/手填） |
| `frontend/src/components/BrandLogo.vue` | 品牌标识 |
| `frontend/src/icons/**` | SVG 图标注册表（`AppIcon` + `registry/paths`） |
| `frontend/mobile/src/offline.js` | 离线队列（扫码日志 / 盘点草稿 / 作业动作） |
| `frontend/mobile/src/styles.css` | 手机端 Design Token |

**Design Token（PC / 手机一致）**：主色 `#2F6BFF` · 成功 `#22A06B` · 警告 `#F79009` · 危险 `#F04438` · 背景 `#F5F6F8` · 圆角 12–14px。

---

## 相关设计文档

| 文档 | 内容 |
|---|---|
| [物资全链路管理系统设计说明.md](物资全链路管理系统设计说明.md) | 总设计 V1.3：角色、模块、状态机、API、MVP、V1.3 实现增强 |
| [数据库表设计-税率与二维码溯源.md](数据库表设计-税率与二维码溯源.md) | 税率字典、税价字段、溯源三表、索引、复审结论、V1.3 主数据扩展表 |
| [手机端设计说明.md](手机端设计说明.md) | 手机端 IA、扫码、离线弱网、移动端 API（V1.1 对齐实现） |
| [frontend/mobile/README.md](frontend/mobile/README.md) | 手机端路由与已对接 API 清单 |

---

## 简化 / 未做

- 移动端完整离线写回（扫码日志/草稿已支持，复杂单据仍以在线为准）
- PDA 安卓专用端（H5 同 API，专项调优未做）
- Redis / 分布式调度（当前内存缓存 + `sys_job_lock` 定时任务）
- MinIO 生产接入（已抽象 `StorageService`，`wuzi.storage.type=minio` 需引入依赖并配置）

### 已补齐能力（原「简化 / 未做」）

| 项 | 说明 |
|---|---|
| 企微 / 钉钉消息 | 应用消息推送（`NotifyChannelService`），默认 mock；配置 `wuzi.wecom.*` / `wuzi.dingtalk.*` 后真实发送 |
| 邮件 / 短信 | `MailNotifyService`（SMTP）+ `SmsNotifyService`（logging/http/aliyun），`wuzi.mail.*` / `wuzi.sms.*` |
| 询价独立审批 | `POST /api/purchase/inquiries/{id}/submit|approve|reject|cancel` |
| 标签模板在线编辑 | `label_template` 表 + `LabelTemplates.vue`，打印可 `?templateCode=` |
| 供应商联系人 | `supplier_contact` 表 + 供应商页联系人管理 |
| 计量单位字典 | `material_unit`（含换算系数）+ `Units.vue` |
| 项目/成本中心 | `project` 表 + `Projects.vue` |
| 序列号/批次台账 | `StockLedgers.vue` + `/api/stock/serials|batches` |
| 对外公开溯源 | `PublicTrace.vue`（`#/public-trace?token=`）+ 查询频次限制 |
| 列表导出 | `/api/exports/**` + 物资/库存/出入库/采购/领用导出按钮 |
| 导入扩展 | 物资档案、期初库存；`import_batch_biz` 真回滚 |
| 登录安全 | 登录限流/锁定、密码策略（≥8 位字母数字） |
| 附件存储抽象 | `StorageService` 本地/MinIO 可切换 |

---

## 开发约定

1. **税价**只走 `PriceTaxCalculator`；金额红冲只用 `amount_sign`，不写负数金额。
2. **库存**只经 `StockService` 变更，保证流水完整；禁止旁路改 `stock` 表。
3. **写接口**要么标注 `@RequirePerm`，要么让 `PermCodeResolver` 能从路径解析出权限码。
4. **SQL** 一律 UTF-8 无 BOM；补丁放入 `sql/patches/`，并重跑 `python sql/_merge_full.py` 同步再生成 `wuzi_full.sql`。
5. **逻辑删除**统一 `is_deleted`（MyBatis-Plus 全局配置）；单据行保留 `version` 乐观锁。
6. 新增业务单据沿用状态机：`DRAFT → PENDING_APPROVAL → APPROVED → 执行态` / `REJECTED`。
7. **角色管权限，职位管组织职务**；审批节点优先用 `POSITION` / `DEPT_LEADER` 定位审批人。
8. 单号生成用 `BillNoUtil`；操作审计用 `@OpLog`，勿手写日志表。
9. 手机端弱网动作走 `offline.js` 队列 + `/api/m/sync/batch`，带 `requestId` 幂等。
10. 根目录 `index.html` 仅作设计原型参考，**不要**当作生产手机端入口；生产以 `frontend/mobile/` 为准。
11. 统一响应/分页走 `R` / `PageResult`；新接口勿自造包装结构。

> 说明：完整初始化以 `sql/wuzi_full.sql` 为准（结构 + 种子 + 全部补丁效果，一个文件）；历史基线 `wuzi_test.sql` 与增量补丁归档于 `sql/patches/`，供已有旧库升级。

---

## 生产上线检查清单（P2 安全门禁）

上线前必须完成，缺一不可：

1. **密钥与口令**
   - 设置强随机 `WUZI_JWT_SECRET`（≥32 字符，禁止 test/default）
   - 设置 `WUZI_DB_PASSWORD`，并修改 MySQL root 业务账号
   - 首次登录后立即修改 `admin/admin123` 等演示账号

2. **网络与 CORS**
   - `WUZI_CORS_ORIGINS` 仅列可信前端源（禁止 `*`）
   - Swagger / actuator 仅内网或经网关鉴权
   - 关闭 `WUZI_WECOM_MOCK` / `WUZI_DINGTALK_MOCK`

3. **权限**
   - 写接口已声明 `@RequirePerm`（启动期 `PermContractChecker` 强制）
   - 未登记写接口默认拒绝（`ApiAccessRules.DENY`）
   - 手机端 `/api/m/**` 按作业域收口，勿重新放宽

4. **数据**
   - 按 README 补丁顺序执行全部 SQL，或使用 Compose 挂载的完整补丁链
   - 演示数据（`SeedDemoChain`）不得导入生产

5. **CI / 工程**
   - `python scripts/check_arch.py` 必须通过
   - `python scripts/audit_api_alignment.py` 必须通过
   - `backend mvn test` 必须通过（含 PermContractTest）

---

## 许可证

[MIT](LICENSE) © 2026 chenchiren

可自由使用、修改、分发与商用，保留版权声明即可。生产部署前请务必替换演示账号口令与数据库凭据。

