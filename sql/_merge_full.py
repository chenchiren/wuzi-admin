# -*- coding: utf-8 -*-
"""合并 wuzi_test.sql + 27 个补丁 -> wuzi_full.sql（UTF-8 无 BOM，字节级原样拷贝各段）"""
import io, os

SQL_DIR = r"E:/aicode/relex/物资管理系统/sql"
OUT = os.path.join(SQL_DIR, "wuzi_full.sql")

ORDER = [
    "patch_missing_tables", "patch_indexes", "patch_dict", "patch_dict_v2",
    "patch_dict_price_source", "patch_dict_resubmit", "patch_dict_status_labels",
    "patch_dict_use_and_source", "patch_approval_msg", "patch_approval_flows",
    "patch_finance", "patch_alert_return", "patch_org_position",
    "patch_purchase_line_tax", "patch_button_perms", "patch_flow_config_perms",
    "patch_org_tree_dept_type", "patch_plan_budget_contract_payment",
    "patch_unit_project_ledger", "patch_label_template_supplier_contact",
    "patch_inquiry_approval", "patch_import_batch_biz", "patch_apply_plan_link",
    "patch_order_inquiry_link", "patch_use_issue_warehouse", "patch_ai_assistant", "patch_material_alias",
]

HEADER = """-- =====================================================================
-- 物资全链路管理系统 · 统一初始化 SQL（结构与数据）
-- 本文件 = 原 wuzi_test.sql（建库/建表/种子）+ 全部 27 个增量补丁，按序合并。
-- 由来：初始化此前需「主库 + patch_* 按序执行」，现统一为本文件一次执行。
--   历史基线与增量补丁归档于 sql/patches/（已有旧库按需增量升级时使用）。
-- 基线：85 张业务表 / 435 条语句 / 27 个补丁，UTF-8 无 BOM，单文件导入零报错（MySQL 8.0 实测）。
-- 用法：
--   PowerShell: .\\sql\\run-java-utf8.ps1 init .\\sql\\wuzi_full.sql
--   或 mysql --default-character-set=utf8mb4 < sql/wuzi_full.sql
-- 特性：
--   1. 可重复执行：主库段 DROP+CREATE；补丁段 information_schema 幂等判断；种子 ON DUPLICATE KEY。
--   2. 补丁效果已含：字典/审批/预算合同付款/单位项目/标签联系人/询价审批/导入回滚/
--      plan 关联/询价关联/领用发料仓库/智能助手/物资别名等全部 27 项。
--   3. 全链路演示数据不在此文件，另行执行 .\\sql\\run-java-utf8.ps1 seed（SeedDemoChain）。
--   4. 编码：UTF-8 无 BOM；导入工具见 run-java-utf8.ps1 / InitDb.java。
-- 合并顺序（与 README 补丁表一致）：
--   missing_tables -> indexes -> dict -> dict_v2 -> dict_price_source -> dict_resubmit
--   -> dict_status_labels -> dict_use_and_source -> approval_msg -> approval_flows
--   -> finance -> alert_return -> org_position -> purchase_line_tax -> button_perms
--   -> flow_config_perms -> org_tree_dept_type -> plan_budget_contract_payment
--   -> unit_project_ledger -> label_template_supplier_contact -> inquiry_approval
--   -> import_batch_biz -> apply_plan_link -> order_inquiry_link -> use_issue_warehouse
--   -> ai_assistant -> material_alias
-- =====================================================================

"""

def read_raw(path):
    with open(path, "rb") as f:
        return f.read()

# patch_indexes 中 3 条 ALTER 依赖后续补丁才建的表（approval_instance_ext /
# sys_message / return_store_order），原按序执行时必然失败（InitDb 失败跳过）。
# 统一文件中移至末尾「补建索引段」重放，保证全部生效。
DEFERRED_INDEX_LINES = [
    "ALTER TABLE approval_instance_ext ADD KEY idx_flow_node (flow_id, current_node_no);",
    "ALTER TABLE sys_message ADD KEY idx_receiver_time (receiver_id, created_at);",
    "ALTER TABLE return_store_order ADD KEY idx_status (status);",
]
# patch_approval_msg 中两条单行多语句（PREPARE;EXECUTE;DEALLOCATE 挤一行），
# InitDb 按行切分会整行当一条执行而语法报错，导致 approval_instance 主表
# flow_id/current_node_no 两列从未落库（Java 侧以 approval_instance_ext 兜底）。
# 统一文件中拆为三行，使两列正常补齐（ext 表路径不变，向后兼容）。
SINGLE_LINE_PREPARE = "PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;"
SPLIT_PREPARE = ("PREPARE stmt FROM @sql;\n"
                 "EXECUTE stmt;\n"
                 "DEALLOCATE PREPARE stmt;")

parts = [HEADER.encode("utf-8")]
# 基线与补丁均已归档于 sql/patches/
parts.append(read_raw(os.path.join(SQL_DIR, "patches", "wuzi_test.sql")))
deferred = []
for p in ORDER:
    src = os.path.join(SQL_DIR, "patches", p + ".sql")
    raw = read_raw(src).decode("utf-8")
    if p == "patch_indexes":
        for line in DEFERRED_INDEX_LINES:
            raw = raw.replace(line + "\n", "")
            deferred.append(line)
    if p == "patch_approval_msg":
        raw = raw.replace(SINGLE_LINE_PREPARE, SPLIT_PREPARE)
    parts.append(("\n-- =====================================================================\n"
                  "-- 增量补丁：%s\n"
                  "-- =====================================================================\n" % p).encode("utf-8"))
    parts.append(raw.encode("utf-8"))

parts.append(("\n-- =====================================================================\n"
              "-- 补建索引段：patch_indexes 中三条 ALTER 依赖后续补丁建的表，\n"
              "-- 原按序执行时失败跳过；此处统一补建（幂等前请确认列已存在）。\n"
              "-- =====================================================================\n").encode("utf-8"))
for line in deferred:
    parts.append((line + "\n").encode("utf-8"))

data = b"".join(parts)
with open(OUT, "wb") as f:
    f.write(data)

print("written:", OUT)
print("bytes:", len(data))
print("lines:", data.count(b"\n") + 1)
print("patch markers:", data.count("-- 增量补丁：".encode("utf-8")))
print("first3:", data[:3])
