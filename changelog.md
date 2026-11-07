# 🎀 Ribbon 0.20.7 — 液态玻璃 UI 与实用功能扩展

- **液态玻璃视频设置界面**：增强背景模糊、半透明圆角玻璃底板、漂移镜面高光；按钮/标签/选项行/搜索框/滚动条整体玻璃化，悬停带平滑发光过渡，按钮间距与呼吸感重做。
- **动态光源**：视觉页完整开关组（含光影自动禁用、半径、亮度、偏移、平滑过渡等 11 项）。
- **新增功能**：帧时间曲线 + 内存/实体计数的增强悬浮层、药水效果计时 HUD、夜视增强（Fullbright）、窗口标题 FPS。
- **性能**：自适应区块重建预算、volatile 配置快照、零分配距离判定、剔除缓存治理、静止降频扫描、实体分级剔除与白名单、粒子生成预算。
- **稳定性**：LOD 降级为实验性功能（默认关闭）；GUI 滑杆安全工厂 + 静态校验；中英文文案全量审计（255 键齐备）。

> 历史版本说明见下方。

---

## 🎀 历史版本：Ribbon 0.10.8 Beta — 极致性能与视觉增强

**【中文简介】**
Ribbon 是基于 Embeddium 21.1 分支深度重构的综合性客户端增强模组。我们不仅在底层渲染和内存管理上进行了极端的压榨，更集成了光影兼容的 FSR 超分、内置音乐播放器以及一整套硬核 HUD 系统，旨在为你提供顶级的沉浸式游戏体验。

**【English Introduction】**
Ribbon is a comprehensive client-side enhancement mod deeply refactored from the Embeddium 21.1 branch. We have pushed the limits of low-level rendering and memory management, while integrating shader-compatible FSR upscaling, a built-in music player, and a full suite of hardcore HUDs to provide a top-tier immersive experience.

---

## 🚀 核心更新亮点 | Core Highlights

### 💾 史诗级底层优化：深度内存去重 | Deep Memory Optimizer
**【中文】**
- 新增对标 **FerriteCore** 的内存优化系统，通过极底层的 Mixin 注入实现内存大幅缩减。
- **全局去重**：对 BlockState 的形状、属性表、以及 ModelResourceLocation 字符串进行全局池化复用。
- 极大地降低了大型整合包的内存占用，并有效减少因 GC（垃圾回收）引起的卡顿。

**【English】**
- Added a **FerriteCore-like** memory optimization system via deep Mixin injections.
- **Global Deduplication**: Pools and reuses block shapes, property maps, and ModelResourceLocation strings.
- Drastically reduces RAM usage in large modpacks and minimizes GC (garbage collection) stutters.

### 🖥️ 完美兼容光影：FSR 1.0 超分 | Shader-Compatible FSR 1.0
**【中文】**
- **光影救星**：完美兼容 Iris/Oculus，彻底解决开启光影时的 HDR 雪花屏与画面变暗问题。
- **Luma 锐化**：摒弃暴力的 RGB 锐化，采用亮度拉普拉斯算子，边缘清晰且拒绝“大理石色带”。
- 极低性能开销，在极高画质下依然保持 144Hz+ 的流畅体验。

**【English】**
- **Shader Savior**: Perfectly compatible with Iris/Oculus, completely eliminating HDR snow screens and darkening when shaders are active.
- **Luma Sharpening**: Uses brightness Laplacian sharpening instead of aggressive RGB sharpening, keeping edges crisp without "marble-like color banding".
- Extremely low overhead, maintaining a 144Hz+ experience even at ultra settings.

### 📊 硬核信息 HUD 与 音乐 | Advanced Info HUD & Music
**【中文】**
- **信息 HUD**：屏幕实时显示 XYZ 坐标、生物群系、CPS 以及装备耐久（四角可调，支持缩放）。
- **音乐播放器**：内置 MP3 播放，独立纯黑极简 UI（快捷键：`P` 界面 / `K` 播放暂停 / `,` `.` 切歌）。
- **战斗特效**：神仙武器专属星空 `∞` 伤害数字，连击合并防卡顿。

**【English】**
- **Info HUD**: Displays real-time XYZ coords, biome, CPS, and armor durability (configurable corners & scale).
- **Music Player**: Built-in MP3 playback with a standalone minimalist UI (Hotkeys: `P` UI / `K` Play/Pause / `,` `.` Switch).
- **Combat Visuals**: Endgame weapons trigger starry `∞` damage numbers, with anti-lag combo merging.

---

## ⚠️ 重要须知与兼容性 | Important Notes & Compatibility

**【中文】**
- **环境要求**：Minecraft 1.21.1, NeoForge 21.1.259+, Java 21。
- **禁止同时安装**：Ribbon 已内置 Embeddium 分支，**请勿**再同时安装 Embeddium 或 Sodium。
- **🚨 致命冲突警告**：**绝对不要安装 Radium**，它会导致游戏多线程崩溃。请使用官方 **Lithium (NeoForge版)**。
- **开源协议**：遵循 **LGPL-3.0-or-later** 协议，基于 embeddedt 的 Embeddium 开发。

**【English】**
- **Requirements**: Minecraft 1.21.1, NeoForge 21.1.259+, Java 21.
- **Do NOT install together**: Ribbon bundles the Embeddium fork. **Do NOT** install Embeddium or Sodium alongside it.
- **🚨 Fatal Conflict Warning**: **DO NOT install Radium**; it causes multithreading crashes. Please use official **Lithium (NeoForge version)**.
- **License**: Licensed under **LGPL-3.0-or-later**. Based on embeddedt's Embeddium.

---
*如果你遇到任何 Bug 或有好的建议，欢迎在 Issues 反馈！ | If you encounter any bugs or have suggestions, please leave an issue!*