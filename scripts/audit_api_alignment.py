#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""API 对齐审计：前端权限码与后端 @RequirePerm 交集检查（CI 必过）。"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend" / "src" / "main" / "java"
PERM_JS = ROOT / "frontend" / "src" / "utils" / "perm-codes.js"


def backend_codes() -> set[str]:
    codes = set()
    for p in BACKEND.rglob("*.java"):
        text = p.read_text(encoding="utf-8", errors="ignore")
        for m in re.finditer(r'@RequirePerm\(\s*"([^"]+)"', text):
            codes.add(m.group(1))
    return codes


def frontend_codes() -> set[str]:
    if not PERM_JS.exists():
        return set()
    text = PERM_JS.read_text(encoding="utf-8", errors="ignore")
    return set(re.findall(r"['\"]([a-z][a-z0-9-]*:[a-z0-9:-]+)['\"]", text))


def main() -> int:
    be = backend_codes()
    fe = frontend_codes()
    # 前端码必须能在后端找到（避免前端隐藏按钮但后端无对应权限）
    orphan_fe = sorted(fe - be)
    if orphan_fe:
        print("前端权限码在后端无对应 @RequirePerm:")
        for c in orphan_fe:
            print("  -", c)
        return 1
    print(f"API 权限对齐通过（后端 {len(be)} / 前端 {len(fe)}，前端子集合法）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
