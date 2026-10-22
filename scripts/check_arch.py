#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
架构与工程门禁（CI 必过）：
1. Controller 不得直接注入 *Mapper（新代码强制；存量白名单逐步清空）
2. 不得存在私有 changeStock 旁路（必须走 StockService）
3. application.yml 不得含生产默认密钥/密码（仅允许 ${ENV:...} 占位）
4. 写接口必须有 @RequirePerm 或 @LoginOnly（语法级粗检）
失败退出码 1。
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend" / "src" / "main" / "java"
FAIL = []

# 存量 Controller 允许暂留 Mapper 的白名单 —— 目标清空；新增类禁止进入
MAPPER_ALLOWLIST = {
    "AdvancedReportController.java",
    "AlertController.java",
    "ApprovalController.java",
    "AttachmentController.java",
    "BasicDataController.java",
    "BorrowController.java",
    "BudgetController.java",
    "CategoryController.java",
    "ContractController.java",
    "DeptController.java",
    "ExcelCenterController.java",
    "ExcelImportController.java",
    "ExpiryAlertController.java",
    "FlowApprovalController.java",
    "InventoryCheckController.java",
    "InventoryCheckEnhanceController.java",
    "InvoiceController.java",
    "LabelPrintController.java",
    "LabelTemplateController.java",
    "ListExportController.java",
    "LogController.java",
    "MaterialUnitController.java",
    "MaterialUseController.java",
    "MessageController.java",
    "MobileHomeController.java",
    "MobileWorkController.java",
    "OutboundController.java",
    "PaymentController.java",
    "PositionController.java",
    "PriceTrendController.java",
    "PriceTrendDetailController.java",
    "ProjectController.java",
    "PurchaseApplyController.java",
    "PurchaseArrivalController.java",
    "PurchaseInquiryController.java",
    "PurchaseOrderController.java",
    "PurchasePlanController.java",
    "PurchaseReturnController.java",
    "RedFlushController.java",
    "ReturnStoreController.java",
    "ReturnStoreOrderController.java",
    "RoleController.java",
    "ScrapController.java",
    "SettleController.java",
    "StatsController.java",
    "StockController.java",
    "StockLedgerController.java",
    "SupplierContactController.java",
    "SupplierEvalApiController.java",
    "SupplierEvalController.java",
    "SysConfigController.java",
    "SysDictController.java",
    "TaxPriceController.java",
    "TracePrintController.java",
    "TraceQrController.java",
    "TransferController.java",
    "UserController.java",
    "WarehouseController.java",
}

def fail(msg: str) -> None:
    FAIL.append(msg)


def check_controller_mappers() -> None:
    auth_like = (
        "AuthController.java",
        "WeComAuthController.java",
        "DingTalkAuthController.java",
        "MobileAuthController.java",
        "MobileScanController.java",
        "MobileSyncController.java",
    )
    for p in (BACKEND / "com" / "relex" / "wuzi" / "web").rglob("*Controller.java"):
        text = p.read_text(encoding="utf-8", errors="ignore")
        if re.search(r"private\s+final\s+\w+Mapper\s+\w+", text):
            name = p.name
            if name in auth_like:
                continue
            if name not in MAPPER_ALLOWLIST:
                fail(f"Controller 注入 Mapper: {p.relative_to(ROOT)}")


def check_no_stock_bypass() -> None:
    for p in BACKEND.rglob("*.java"):
        if p.name in ("StockService.java", "StockMapper.java"):
            continue
        text = p.read_text(encoding="utf-8", errors="ignore")
        # 真正旁路：在非 StockService 中 insert/updateById stock
        if re.search(r"stockMapper\.(insert|updateById|update)\s*\(", text):
            fail(f"库存旁路调用 stockMapper.insert/update: {p.relative_to(ROOT)}")
        if re.search(r"private\s+(void|Map<[^>]+>)\s+changeStock\s*\(", text) and "StockService.changeAuto" not in text and "stockService.changeAuto" not in text:
            # 允许薄封装调用 changeAuto
            body = text
            if "changeAuto" not in body:
                fail(f"存在非收口 changeStock: {p.relative_to(ROOT)}")


def check_no_default_secrets() -> None:
    yml = ROOT / "backend" / "src" / "main" / "resources" / "application.yml"
    if not yml.exists():
        return
    text = yml.read_text(encoding="utf-8", errors="ignore")
    # 默认密码/密钥不允许写死明文（允许 ${ENV:default} 但默认值不得是弱口令）
    # 具体口令不写进仓库：需要额外探测时用环境变量 WUZI_LEAK_PROBES=a,b,c 传入
    bad_values = {"admin123", "123456", "password", "root123"}
    bad_values.update(x.strip() for x in os.environ.get("WUZI_LEAK_PROBES", "").split(",") if x.strip())
    for bad in bad_values:
        if bad in text and "${" + bad not in text:
            # 允许作为注释说明，不允许作为默认值
            for line in text.splitlines():
                if bad in line and not line.strip().startswith("#") and f":{bad}" in line.replace(" ", ""):
                    fail(f"application.yml 含硬编码敏感默认值: {line.strip()}")


def check_write_endpoints_declare_perm() -> None:
    for p in (BACKEND / "com" / "relex" / "wuzi" / "web").rglob("*.java"):
        text = p.read_text(encoding="utf-8", errors="ignore")
        lines = text.splitlines()
        for i, line in enumerate(lines):
            if re.search(r"@(Post|Put|Delete|Patch)Mapping", line):
                window = "\n".join(lines[max(0, i - 6) : i + 6])
                if "@RequirePerm" not in window and "@LoginOnly" not in window:
                    # 公开认证端点白名单
                    if any(x in p.name for x in ("AuthController", "WeComAuthController", "DingTalkAuthController", "MobileAuthController")):
                        continue
                    if "/login" in window or "/status" in window:
                        continue
                    fail(f"写接口缺少 @RequirePerm/@LoginOnly: {p.name}:{i+1}")


def main() -> int:
    check_controller_mappers()
    check_no_stock_bypass()
    check_no_default_secrets()
    check_write_endpoints_declare_perm()
    if FAIL:
        print("架构门禁失败:")
        for f in FAIL:
            print("  -", f)
        return 1
    print("架构门禁通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
