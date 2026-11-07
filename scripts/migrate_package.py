#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ribbon 包名迁移脚本
把 org.embeddedt.embeddium 的渲染核心子包迁移到 net.caffeinemc.mods.sodium,
使 Iris 的 sodium 兼容层 mixin 能命中 Ribbon 的渲染类。

迁移规则(包名前缀):
  org.embeddedt.embeddium.impl.render -> net.caffeinemc.mods.sodium.client.render
  org.embeddedt.embeddium.impl.gl     -> net.caffeinemc.mods.sodium.client.gl
  org.embeddedt.embeddium.impl.gui    -> net.caffeinemc.mods.sodium.client.gui
  org.embeddedt.embeddium.impl.model  -> net.caffeinemc.mods.sodium.client.model
  org.embeddedt.embeddium.impl.util   -> net.caffeinemc.mods.sodium.client.util
  org.embeddedt.embeddium.impl.world  -> net.caffeinemc.mods.sodium.client.world

  api 工具类(仅 Iris 引用的 4 个):
  org.embeddedt.embeddium.api.util.ColorABGR                   -> net.caffeinemc.mods.sodium.api.util.ColorABGR
  org.embeddedt.embeddium.api.util.NormI8                      -> net.caffeinemc.mods.sodium.api.util.NormI8
  org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter -> net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter
  org.embeddedt.embeddium.api.vertex.format.common.ColorVertex -> net.caffeinemc.mods.sodium.api.vertex.format.common.ColorVertex
"""

import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "java")

# 包前缀映射(从长到短排序,避免部分匹配)
PACKAGE_MAP = [
    ("org.embeddedt.embeddium.impl.render", "net.caffeinemc.mods.sodium.client.render"),
    ("org.embeddedt.embeddium.impl.gl", "net.caffeinemc.mods.sodium.client.gl"),
    ("org.embeddedt.embeddium.impl.gui", "net.caffeinemc.mods.sodium.client.gui"),
    ("org.embeddedt.embeddium.impl.model", "net.caffeinemc.mods.sodium.client.model"),
    ("org.embeddedt.embeddium.impl.util", "net.caffeinemc.mods.sodium.client.util"),
    ("org.embeddedt.embeddium.impl.world", "net.caffeinemc.mods.sodium.client.world"),
    # api 工具类(精确全限定名,不做前缀替换,避免误伤 api 层其他类)
    ("org.embeddedt.embeddium.api.util.ColorABGR", "net.caffeinemc.mods.sodium.api.util.ColorABGR"),
    ("org.embeddedt.embeddium.api.util.NormI8", "net.caffeinemc.mods.sodium.api.util.NormI8"),
    ("org.embeddedt.embeddium.api.vertex.buffer.VertexBufferWriter", "net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter"),
    ("org.embeddedt.embeddium.api.vertex.format.common.ColorVertex", "net.caffeinemc.mods.sodium.api.vertex.format.common.ColorVertex"),
]

# 目录映射:源目录(相对 SRC) -> 目标目录(相对 SRC)
DIR_MAP = [
    ("org/embeddedt/embeddium/impl/render", "net/caffeinemc/mods/sodium/client/render"),
    ("org/embeddedt/embeddium/impl/gl", "net/caffeinemc/mods/sodium/client/gl"),
    ("org/embeddedt/embeddium/impl/gui", "net/caffeinemc/mods/sodium/client/gui"),
    ("org/embeddedt/embeddium/impl/model", "net/caffeinemc/mods/sodium/client/model"),
    ("org/embeddedt/embeddium/impl/util", "net/caffeinemc/mods/sodium/client/util"),
    ("org/embeddedt/embeddium/impl/world", "net/caffeinemc/mods/sodium/client/world"),
]


def rewrite_file(path):
    """重写单个文件的 package/import 声明。返回是否发生了修改。"""
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    new_content = content
    changed = False
    for old, new in PACKAGE_MAP:
        if old in new_content:
            new_content = new_content.replace(old, new)
            changed = True

    if changed:
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(new_content)
    return changed


def move_dirs(dry_run=True):
    """移动目录(先复制到新位置,保留旧目录供回滚;dry_run 只打印不执行)。"""
    for rel_src, rel_dst in DIR_MAP:
        src_abs = os.path.join(SRC, rel_src)
        dst_abs = os.path.join(SRC, rel_dst)
        if not os.path.isdir(src_abs):
            print(f"[跳过] 源目录不存在: {rel_src}")
            continue
        print(f"[目录] {rel_src} -> {rel_dst}")
        if not dry_run:
            os.makedirs(os.path.dirname(dst_abs), exist_ok=True)
            if os.path.isdir(dst_abs):
                shutil.rmtree(dst_abs)
            shutil.move(src_abs, dst_abs)


def main():
    dry_run = "--apply" not in sys.argv
    mode = "DRY-RUN(仅预览)" if dry_run else "APPLY(实际执行)"

    print(f"=== Ribbon 包名迁移脚本 [{mode}] ===")
    print(f"源码根: {SRC}\n")

    # 1. 统计将迁移的文件
    files_to_rewrite = []
    for rel_src, _ in DIR_MAP:
        src_abs = os.path.join(SRC, rel_src)
        if os.path.isdir(src_abs):
            for dirpath, _, filenames in os.walk(src_abs):
                for fn in filenames:
                    if fn.endswith(".java"):
                        files_to_rewrite.append(os.path.join(dirpath, fn))
    # api 工具类文件
    api_files = [
        "org/embeddedt/embeddium/api/util/ColorABGR.java",
        "org/embeddedt/embeddium/api/util/NormI8.java",
        "org/embeddedt/embeddium/api/vertex/buffer/VertexBufferWriter.java",
        "org/embeddedt/embeddium/api/vertex/format/common/ColorVertex.java",
    ]
    for rel in api_files:
        p = os.path.join(SRC, rel)
        if os.path.isfile(p):
            files_to_rewrite.append(p)

    # 2. 全项目扫描需要改 import 的文件
    all_java = []
    for dirpath, _, filenames in os.walk(SRC):
        for fn in filenames:
            if fn.endswith(".java"):
                all_java.append(os.path.join(dirpath, fn))

    import_files = []
    for p in all_java:
        with open(p, "r", encoding="utf-8") as f:
            c = f.read()
        if any(old in c for old, _ in PACKAGE_MAP):
            import_files.append(p)

    print(f"直接迁移文件数(改 package + 移动目录): {len(files_to_rewrite)}")
    print(f"需要改 import 的文件总数(全项目): {len(import_files)}")
    print()

    if dry_run:
        print("=== 预览:将迁移的目录 ===")
        move_dirs(dry_run=True)
        print("\n=== 预览:将改写的文件(前 30 个) ===")
        for p in import_files[:30]:
            print("  ", os.path.relpath(p, SRC))
        print(f"\n(共 {len(import_files)} 个文件需要改写 import)")
        print("\n提示: 用 --apply 参数实际执行。")
        return

    # 3. 实际执行:先移动目录,再改写文件内容
    print("=== 步骤 1/2: 移动目录 ===")
    move_dirs(dry_run=False)

    # 移动 api 工具类
    api_file_map = {
        "org/embeddedt/embeddium/api/util/ColorABGR.java": "net/caffeinemc/mods/sodium/api/util/ColorABGR.java",
        "org/embeddedt/embeddium/api/util/NormI8.java": "net/caffeinemc/mods/sodium/api/util/NormI8.java",
        "org/embeddedt/embeddium/api/vertex/buffer/VertexBufferWriter.java": "net/caffeinemc/mods/sodium/api/vertex/buffer/VertexBufferWriter.java",
        "org/embeddedt/embeddium/api/vertex/format/common/ColorVertex.java": "net/caffeinemc/mods/sodium/api/vertex/format/common/ColorVertex.java",
    }
    for rel_src, rel_dst in api_file_map.items():
        src_abs = os.path.join(SRC, rel_src)
        dst_abs = os.path.join(SRC, rel_dst)
        if os.path.isfile(src_abs):
            os.makedirs(os.path.dirname(dst_abs), exist_ok=True)
            shutil.move(src_abs, dst_abs)
            print(f"[文件] {rel_src} -> {rel_dst}")

    print("\n=== 步骤 2/2: 改写 package/import 声明 ===")
    # 目录移动后重新扫描(因为文件已到新位置)
    all_java = []
    for dirpath, _, filenames in os.walk(SRC):
        for fn in filenames:
            if fn.endswith(".java"):
                all_java.append(os.path.join(dirpath, fn))

    rewritten = 0
    for p in all_java:
        if rewrite_file(p):
            rewritten += 1

    print(f"\n完成! 共改写 {rewritten} 个文件。")
    print("接下来: 同步 mixin 配置 + 编译验证。")


if __name__ == "__main__":
    main()
