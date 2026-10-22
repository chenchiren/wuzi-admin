# 更新记录

物资全链路管理系统的版本与数据库基线变更。数据库脚本以 `sql/wuzi_full.sql` 为唯一入口，
历史增量见 `sql/patches/`，细节见 [`sql/README.md`](sql/README.md)。

版本号规则：`主版本.次版本.修订号`；数据库结构有破坏性变更时升主版本。

---

## [v1.0.0] - 2026-10-22

首个可公开发布的整理版本。功能范围与 2026-10-10 的开发基线一致，本次重点是**把数据库脚本、文档和本地密钥整理成可上仓库的形态**。

### 数据库

- **统一初始化基线**：`sql/wuzi_full.sql` = 原始库 `wuzi_test.sql` + 27 个增量补丁按序合并。
  - 85 张业务表 / 435 条可执行语句 / 152 KB / UTF-8 无 BOM
  - 实测：空库导入 0 报错，85 张表全部建成（MySQL 8.0）
  - 可重复执行：主库段 `DROP + CREATE`；补丁段用 `information_schema` 幂等判断；种子 `ON DUPLICATE KEY`
- **补丁归档**：`sql/patches/` 保留 27 个增量脚本，已有旧库可按序补跑，无需重灌。
- **合并器**：`sql/_merge_full.py` 负责重新生成总 SQL，并处理两类只在合并时才暴露的问题：
  1. `patch_indexes` 中 3 条 `ALTER` 依赖后续补丁才建的表 → 移到文件末尾「补建索引段」；
  2. `patch_approval_msg` 里 `PREPARE;EXECUTE;DEALLOCATE` 挤在一行 → 拆成三行，`approval_instance.flow_id` / `current_node_no` 才能真正落库。
- **文档**：新增 `sql/README.md`（目录说明、两种用法、27 补丁顺序、85 张表清单、编码规范）。

### 安全

- **移除明文凭据**：数据库账号、口令、JWT 密钥不再写进仓库。
  - `start-backend.cmd` / `start-backend-dev.cmd` 改为从 `start-backend.local.cmd` 读取（已 gitignore），并附 `.example` 模板；
  - `sql/*.java`（`InitDb`、`SeedDemoChain`、`EncCheck`、`FixDbEncoding`、`FixStockBalance`、`FixStockRecordSign`）改为读环境变量 `WUZI_DB_USER` / `WUZI_DB_PASSWORD`，**两项都必须显式提供**（无内置账号、无默认口令），缺失时给出明确报错并退出；
  - `docker-compose.yml` 拆分为 `${WUZI_DB_ROOT_PASSWORD}`（仅容器初始化）与 `${WUZI_DB_USER}` / `${WUZI_DB_PASSWORD}`（业务连接），三者均必填，缺失直接启动失败；
  - 本地真实凭据存放在仓库外的 `%USERPROFILE%\.wuzi\env`，项目目录内不留任何口令文件；
  - `scripts/check_arch.py` 的敏感词探测改为环境变量注入（`WUZI_LEAK_PROBES`），不再内置真实口令。
- `.gitignore` 补充 `start-backend.local.cmd`、`sql/.env.local`、`.workbuddy/`、`sql/out/` 等本地文件。

### 工程

- 新增 `.gitattributes`：SQL / Java / 脚本统一 LF，`.cmd` / `.ps1` 强制 CRLF，二进制文件标记 `binary`，避免 Windows 检出后批处理与 SQL 导入出错。
- 清理 `sql/out/` 编译残留（`.class` 由 `run-java-utf8.ps1` 按需重新生成）。

### 数据库能力（本基线已包含）

| 域 | 能力 |
|---|---|
| 主数据 | 物资分类 / 单位 / 别名、供应商与联系人、多仓多库位、标签模板、税率字典 |
| 采购 | 计划 → 申请 → 询价 → 订单 → 到货 → 入库，含税价七件套、行级税码快照、询价/计划回写 |
| 财务 | 预算与消耗日志、合同与付款节点、付款单、发票、结算 |
| 库存 | 批次 / 件码 / 流水、出入库、调拨、盘点、预警、红冲 |
| 作业 | 领用发料、长期借用与归还、退库、报废 |
| 流程 | 审批流定义与节点、审批实例扩展、站内信、按钮级权限 |
| 溯源 | SKU / BATCH / SERIAL 二维码、打印日志、扫码日志、对外脱敏查询 |
| 智能助手 | 会话 / 消息 / 工具调用审计，8 个只读工具 + 物资别名与向量检索 |

### 升级指引

**新库**（结构 + 基础种子，一次完成）：

```powershell
$env:WUZI_DB_PASSWORD = '你的数据库口令'
.\sql\run-java-utf8.ps1 init .\sql\wuzi_full.sql
```

**已有旧库**（保留业务数据，只补结构）：

按 `sql/README.md` 第四节顺序执行 `patches/` 中缺失的补丁，全部幂等。
最低要求：`patch_missing_tables` → `patch_indexes` → `patch_button_perms` → `patch_plan_budget_contract_payment` → 其余。

**演示数据**（可选）：

```powershell
.\sql\run-java-utf8.ps1 seed
```

### 已知事项

- `approval_instance.flow_id` / `current_node_no` 只在统一基线中存在；旧库由 Java 侧 `approval_instance_ext` 兜底，功能不受影响。
- 开发库中 5 张表的 `unit` 列默认值曾因历史编码问题写成 `涓�`，统一基线已修正为 `个`；旧库可用 `FixDbEncoding` 修复或重导。
- 开发库里存在 E2E 测试产生的脏数据（`E2E_TEST_POS2`、`UI测试项`、`TEST_U` 等），**未**纳入基线。
- 演示数据与基线分离：`wuzi_full.sql` 只含结构与基础种子，业务样例由 `SeedDemoChain` 生成。

---

## 数据库版本对照

| 版本 | 日期 | 表数 | 补丁数 | 主要变化 |
|---|---|---|---|---|
| v1.0.0 | 2026-10-22 | 85 | 27 | 统一基线公开发布；补丁合并、文档、密钥外置 |
| — | 2026-10-10 | 85 | 27 | 智能助手 3 表 + `ai:chat` 权限；物资别名词典 |
| — | 2026-09-29 | 81 | 25 | 申请/订单/领用回写关联字段（无新表） |
| — | 2026-09-28 | 81 | 22 | 计划/预算/合同/付款、单位/项目台账、标签模板与联系人、询价审批、导入回滚 |
| — | 2026-09-24 | 66 | 16 | 补表、索引、字典体系、审批流、税率快照、按钮权限、组织职位 |
| — | 基线 | 39 | 0 | `wuzi_test.sql` 原始库 |
