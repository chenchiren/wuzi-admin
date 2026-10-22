#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""前后端接口对齐审计 v2：
匹配语义对齐 Spring：
1) 剥 query string
2) 字面量路径优先，其后才是 {var} 占位符段对齐（每段：字面量相等 或 双方均为占位符）
3) 匹配键必须声明对应 HTTP 方法
输出：缺失 / 方法不匹配 / 对齐统计
"""
import re
import sys
from pathlib import Path
from collections import defaultdict

ROOT = Path(__file__).resolve().parents[1]
FE_PC = ROOT / "frontend" / "src"
FE_MB = ROOT / "frontend" / "mobile" / "src"
BE = ROOT / "backend" / "src" / "main" / "java"

def strip_query(p: str) -> str:
    return p.split("?", 1)[0]

def norm(p: str) -> str:
    # 先替换模板占位（三元表达式里可能含 ?，须在剥 query 之前完成）
    p = re.sub(r"\$\{[^}]*\}", "{p}", p.strip())
    return strip_query(p)

def add_prefix_api(p: str) -> str:
    p = "/" + p.strip("/")
    return p if p.startswith("/api") else "/api" + p

# ---------- 前端提取 ----------
fe_calls = defaultdict(set)  # path -> {(method, src)}

def record(path: str, method: str, src: str):
    if not path or not path.startswith("/"):
        return
    np = norm(path)
    if np.startswith("//") or np.startswith("http"):
        return
    if not np.startswith("/api"):
        np = add_prefix_api(np)
    fe_calls[np].add((method.upper(), src))

for f in list(FE_PC.rglob("*.vue")) + list(FE_PC.rglob("*.js")):
    text = f.read_text(encoding="utf-8", errors="ignore")
    rel = str(f.relative_to(ROOT))
    # api.* 调用：模板串（反引号）优先，再单/双引号串（避免内容引号截断）
    for m in re.finditer(r"\bapi\.(get|post|put|delete|patch)\(\s*`([^`]+)`", text, re.S):
        record(m.group(2), m.group(1), rel)
    for m in re.finditer(r"\bapi\.(get|post|put|delete|patch)\(\s*\"([^\"]+)\"", text):
        record(m.group(2), m.group(1), rel)
    for m in re.finditer(r"\bapi\.(get|post|put|delete|patch)\(\s*'([^']+)'", text):
        record(m.group(2), m.group(1), rel)
    # fetch：先摘除带 method 的调用，剩余兜底记 GET
    methoded = []
    def _pull(mm):
        methoded.append((mm.group(2), mm.group(3)))
        return ""
    text2 = re.sub(r"fetch\(\s*(['\"`])(/api/[^'\"`]+)\1\s*,\s*\{[^}]*method:\s*['\"](\w+)", _pull, text, flags=re.S)
    for p, meth in methoded:
        record(p, meth, rel)
    for m in re.finditer(r"fetch\(\s*(['\"`])(/api/[^'\"`]+)\1", text2):
        record(m.group(2), "GET", rel)

for f in list(FE_MB.rglob("*.vue")) + list(FE_MB.rglob("*.js")):
    text = f.read_text(encoding="utf-8", errors="ignore")
    rel = str(f.relative_to(ROOT))
    for m in re.finditer(r"\brequest\(\s*(['\"`])(/api/[^'\"`]+)\1\s*(?:,\s*\{([^}]*)\})?", text, re.S):
        path, opts = m.group(2), m.group(3) or ""
        mm = re.search(r"method:\s*['\"](\w+)", opts)
        record(path, mm.group(1) if mm else "GET", rel)

# ---------- 后端提取 ----------
be_routes = defaultdict(set)

for f in BE.rglob("*.java"):
    text = f.read_text(encoding="utf-8", errors="ignore")
    cm = re.search(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"', text)
    cls = cm.group(1) if cm else ""
    for m in re.finditer(r'@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(([^)]*)\))?', text):
        arg = m.group(2) or ""
        pm = re.search(r'"([^"]*)"', arg)
        sub = pm.group(1) if pm else ""
        key = "/" + (cls + sub).strip("/").lstrip("/")
        be_routes[key].add(m.group(1).upper())

# ---------- 匹配（Spring 语义：字面量优先，且必须声明对应方法） ----------
def segs(p: str):
    return p.strip("/").split("/") if p.strip("/") else []

def match_segments(fe: str, be: str) -> bool:
    a, b = segs(fe), segs(be)
    if len(a) != len(b):
        return False
    for x, y in zip(a, b):
        if x.startswith("{") or y.startswith("{"):
            continue  # 双方占位或一方占位均可（运行时占位符可匹配任意字面量）
        if x != y:
            return False
    return True

missing, method_mismatch = [], []
aligned = 0
for path in sorted(fe_calls):
    entries = sorted(fe_calls[path])
    # 字面量精确键
    lit = be_routes.get(path)
    # 占位符候选
    ph = [bp for bp in be_routes if "{" in bp and match_segments(path, bp)]
    for method, src in entries:
        ok = False
        if lit and method in be_routes[path]:
            ok = True
        else:
            for bp in ph:
                if method in be_routes[bp]:
                    ok = True
                    break
        if ok:
            aligned += 1
        else:
            if lit or ph:
                cands = sorted(be_routes[path]) if lit else sorted(set().union(*(be_routes[b] for b in ph)))
                method_mismatch.append(f"{method:6s} {path}   后端该方法仅有: {cands}   <- {src}")
            else:
                missing.append(f"{method:6s} {path}   <- {src}")

print("=" * 72)
print(f"前端调用点: {sum(len(v) for v in fe_calls.values())} 个，去重路径 {len(fe_calls)} 条")
print(f"后端路由:   {len(be_routes)} 条路径")
print(f"对齐通过:   {aligned}/{sum(len(v) for v in fe_calls.values())}")
print("=" * 72)
print(f"\n【缺失接口】前端调用但后端无此路径: {len(missing)}")
for line in missing:
    print("  " + line)
print(f"\n【方法不匹配】路径存在但方法对不上: {len(method_mismatch)}")
for line in method_mismatch:
    print("  " + line)
if not missing and not method_mismatch:
    print("\n=== 全部对齐 ===")
