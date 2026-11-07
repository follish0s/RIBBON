#!/usr/bin/env python3
"""审计 LangUtil 翻译完整性：RibbonOptions 里用到的每个 tr() 键必须在 EN/ZH 两个表里都存在。

用法: python scripts/validate_lang_keys.py
退出码 0 = 全部通过；1 = 有缺失（打印缺失清单）。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/java/com/example/ribbon/LangUtil.java"
# 扫描所有可能调用 LangUtil.tr 的源码
SRC_DIRS = [
    ROOT / "src/main/java/com/example/ribbon",
    ROOT / "src/main/java/org/embeddedt/embeddium",
]


def extract_map_entries(text: str, marker: str) -> dict:
    """marker 形如 'EN.put' / 'ZH.put'；返回 key -> 行号。"""
    entries = {}
    for m in re.finditer(marker + r'\("([^"]+)"', text):
        entries[m.group(1)] = text[: m.start()].count("\n") + 1
    return entries


def extract_used_keys() -> set:
    keys = set()
    for d in SRC_DIRS:
        for f in d.rglob("*.java"):
            text = f.read_text(encoding="utf-8", errors="replace")
            for m in re.finditer(r'(?:LangUtil|this|context|drawContext)?\.tr\(\s*"([^"]+)"', text):
                keys.add(m.group(1))
            # tr(key, arg) 重载同样匹配上面；动态拼接的键（trHoliday 等）人工保证
    return keys


def main() -> int:
    text = LANG.read_text(encoding="utf-8")
    en = extract_map_entries(text, "EN.put")
    zh = extract_map_entries(text, "ZH.put")
    used = extract_used_keys()

    # 前缀展开：源码里可能有 "ribbon.holiday." + id 这类动态键，跳过（以 . 结尾视为前缀）
    missing_en = sorted(k for k in used if k not in en and not k.endswith("."))
    missing_zh = sorted(k for k in used if k not in zh and not k.endswith("."))

    print(f"使用中的翻译键: {len(used)} 个 | EN 表: {len(en)} 条 | ZH 表: {len(zh)} 条")
    ok = True
    if missing_en:
        ok = False
        print(f"缺失于 EN 表 ({len(missing_en)}):")
        for k in missing_en:
            print(f"  - {k}")
    if missing_zh:
        ok = False
        print(f"缺失于 ZH 表（未汉化） ({len(missing_zh)}):")
        for k in missing_zh:
            print(f"  - {k}")
    # EN/ZH 表内部互相校验（一边有一边没有 = 漏翻译）
    en_only = sorted(set(en) - set(zh))
    zh_only = sorted(set(zh) - set(en))
    if en_only:
        ok = False
        print(f"EN 有而 ZH 缺 ({len(en_only)}): " + ", ".join(en_only[:20]))
    if zh_only:
        ok = False
        print(f"ZH 有而 EN 缺 ({len(zh_only)}): " + ", ".join(zh_only[:20]))

    if ok:
        print("结果: PASS - 所有界面文案中英文齐备")
        return 0
    print("结果: FAIL - 存在缺失/未汉化文案")
    return 1


if __name__ == "__main__":
    sys.exit(main())
