#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""重命名 ChunkModelVertexConsumer -> ChunkVertexConsumer, IndigoBlockRenderContext -> AbstractBlockRenderContext。"""
import os
import re
import shutil

ROOT = "src/main/java"

renames = [
    ("ChunkModelVertexConsumer", "ChunkVertexConsumer"),
    ("IndigoBlockRenderContext", "AbstractBlockRenderContext"),
]

# 文件名重命名
file_map = [
    ("net/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkModelVertexConsumer.java",
     "net/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkVertexConsumer.java"),
    ("net/caffeinemc/mods/sodium/client/render/frapi/IndigoBlockRenderContext.java",
     "net/caffeinemc/mods/sodium/client/render/frapi/AbstractBlockRenderContext.java"),
]
for src, dst in file_map:
    s = os.path.join(ROOT, src)
    d = os.path.join(ROOT, dst)
    if os.path.isfile(s):
        os.makedirs(os.path.dirname(d), exist_ok=True)
        shutil.move(s, d)
        print(f"[file] {src.split('/')[-1]} -> {dst.split('/')[-1]}")

# 类名引用改写
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
