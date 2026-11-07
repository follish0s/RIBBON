#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""精确重命名 3 个类名(词边界,避免误伤 EmbeddiumOptionsStorage 等)。"""
import os
import re

ROOT = "src/main/java"

renames = [
    ("EmbeddiumWorldRenderer", "SodiumWorldRenderer"),
    ("EmbeddiumOptions", "SodiumGameOptions"),
    ("WorldSlice", "LevelSlice"),
]

count = 0
for dp, _, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith(".java"):
            continue
        p = os.path.join(dp, fn)
        with open(p, encoding="utf-8") as f:
            c = f.read()
        nc = c
        for old, new in renames:
            nc = re.sub(r"\b" + re.escape(old) + r"\b", new, nc)
        if nc != c:
            with open(p, "w", encoding="utf-8", newline="") as f:
                f.write(nc)
            count += 1

print(f"改写 {count} 个文件")
