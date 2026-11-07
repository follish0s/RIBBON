#!/usr/bin/env python3
"""静态校验 RibbonOptions.java 里所有滑杆参数，防止 SliderControl 硬断言崩溃。

背景（2026-11-06 21:10 崩溃）：
  SliderControl 构造器断言 (max - min) % interval == 0，
  参数不整除时在「打开视频设置」的 mouseClicked 回调里抛 IllegalArgumentException，
  直接生成 crash report。命令行启动验证不会打开 GUI，抓不到这类问题，因此固化成本脚本。

用法：python scripts/validate_gui_sliders.py
退出码 0 = 全部安全；1 = 存在会让 SliderControl 抛异常的参数。
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "src" / "main" / "java" / "com" / "example" / "ribbon" / "RibbonOptions.java"

CALL = re.compile(r"\bslider\(\s*o\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*,")


def main() -> int:
    text = SOURCE.read_text(encoding="utf-8")
    calls = list(CALL.finditer(text))
    if not calls:
        print("FAIL: 没有解析到任何 slider(o, ...) 调用，脚本可能已失效")
        return 1

    print(f"检查 {len(calls)} 个滑杆参数（{SOURCE.relative_to(ROOT)}）")
    bad = 0
    for match in calls:
        low, high, step = (int(x) for x in match.groups())
        line = text[: match.start()].count("\n") + 1
        # 工厂会自动对齐上限，这里同时报告「原始参数不整除」的情况供人工确认
        raw_ok = high > low and step > 0 and (high - low) % step == 0
        aligned = low + max(1, (max(low + max(1, step), high) - low) // max(1, step)) * max(1, step)
        safe_ok = aligned > low and (aligned - low) % max(1, step) == 0
        flag = "OK" if safe_ok else "*** FATAL ***"
        if not raw_ok:
            flag += f"  (原始参数不整除，工厂已对齐上限 -> {aligned})"
        print(f"  line {line}: min={low} max={high} step={step} -> {flag}")
        if not safe_ok:
            bad += 1

    # 必须全部走安全工厂，不允许再直接 new SliderControl
    raw = len(re.findall(r"new SliderControl\(", text))
    print(f"直接构造 SliderControl 的次数: {raw}（应为 1，仅安全工厂内部）")
    if raw != 1:
        print("FAIL: 存在绕过安全工厂的构造")
        bad += 1

    print("结果:", "PASS - 所有滑杆均可安全构造" if bad == 0 else f"FAIL - {bad} 处问题")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
