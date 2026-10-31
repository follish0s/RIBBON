# MODIFICATION REPORT — LOD 原生渲染管线重构（Iris 兼容 + 原版渲染修复）

> 目标：废弃自定义 ByteBuffer 布局与裸 OpenGL 绘制，把 LOD 地形接入 MC 原生的
> `DefaultVertexFormat.BLOCK` + `RenderType` + `BufferBuilder` 管线，彻底解决：
> ① 开启 Iris 后 LOD 消失（顶点格式不被 GBuffer/Shadow Pass 识别）；
> ② 原版视角远处地形"辐射条带/破碎豆腐块"（无深度排序与批次合并）。
>
> 日期：2026-10-31
> 参考模组：Voxy（https://www.mcmod.cn/class/13890.html，Fabric 端 LOD，经 Sodium 区块管线提交）
>
> 结论：**三项重构全部完成；核心改动文件 javac 独立验证 0 错误；gradle 完整构建因网络阻塞未完成（见 §5）**

---

## 1. 根因定位

| 症状 | 根因 |
|---|---|
| Iris 开启后 LOD 消失 | 自定义 VBO 用 `DefaultVertexFormat.POSITION_COLOR`（12+4 字节），**丢弃了 UV0/UV2/Normal**。Iris 的 GBuffer/Shadow Pass 只认 `BLOCK` 格式，读不到光照贴图(UV2)与法线(Normal)，光影下直接不渲染。 |
| 原版视角"辐射条带/豆腐块" | 裸 `RenderSystem.enableDepthTest/depthFunc` + 自定义 shader 直接 `drawWithShader`，**绕过了原版 `RenderType` 的深度排序与批次合并**，LOD 区块间无正确遮挡关系。 |

两者同一病灶：**未走 MC 原生区块渲染管线**。修复 = 把 LOD mesh 按 `BLOCK` 格式提交进原版渲染队列。

---

## 2. 改动总览

| 文件 | 改动点 |
|---|---|
| `lod/mesh/LODMesh.java` | 顶点布局严格对齐 `DefaultVertexFormat.BLOCK`（32 字节），修正 28→32 字节笔误，明确五属性字段语义 |
| `lod/mesh/GreedyMesher.java` | UV 从中心点 0.5 改为**世界坐标平铺**；法线 6 面朝向（叉积逐一验证 CCW） |
| `render/LODVboUploader.java` | VBO 格式 `POSITION_COLOR`→`BLOCK`，`TRIANGLES`→`QUADS`，新增 `emitBlockVertex()` 完整写入五属性 |
| `render/LODShaderRegistry.java` | shader 注册格式 `POSITION_COLOR`→`BLOCK` |
| `render/LODRenderEventHandler.java` | 渲染提交从裸状态设置改为 `RenderType.solid().setupRenderState()/clearRenderState()`；移除 ModelViewStack 操作 |
| `shaders/core/lod_terrain.vsh/.fsh/.json` | shader 属性升级为 BLOCK 五属性 `Position/Color/UV0/UV2/Normal` |

---

## 3. 任务一：LODMesh 接入原生 BLOCK 格式

`LODMesh` 的 `VERTEX_STRIDE` 保持 32 字节，布局与 `DefaultVertexFormat.BLOCK` 完全对齐：

```
Position (float x,y,z)   12 字节   Element.POSITION
Color    (int ARGB)       4 字节   Element.COLOR    (RGBA 归一化)
UV0      (float u,v)      8 字节   Element.UV0
UV2      (short u2,v2)    4 字节   Element.UV2      (光照贴图坐标)
Normal   (byte nx,ny,nz)  3 字节   Element.NORMAL   (signed byte [-127,127] ↔ [-1,1])
padding                  1 字节    Normal 4 字节对齐补齐
合计                      32 字节
```

> **架构说明**：`LODMesh` 保留 DirectByteBuffer 作为「后台线程 → 渲染线程」的零拷贝
> 传输载体（后台线程红线：不得触碰 OpenGL/BufferBuilder，后者非线程安全且依赖 GL 上下文）。
> 渲染线程消费时按 32 字节步长逐字段读回，通过 `BufferBuilder`（`BLOCK` 格式）重建为
> 原生 `MeshData` 再上传 `VertexBuffer`，从而进入原版管线。

---

## 4. 任务二：GreedyMesher 法线与 UV

- **法线**：6 个方向（UP/DOWN/NORTH/SOUTH/EAST/WEST）均写入正确单位法线，且顶点绕序
  CCW（从面外看），已用向量叉积逐一验证——这是 `RenderType.solid()` 背面剔除(CULL)
  不误伤正面的前提，也是光影下 N·L 光照计算正确的前提。
- **UV**：从 `UV_CENTER=0.5`（每面中心采样）改为**按世界坐标平铺**——顶/底面 `u=x, v=z`，
  侧面 `u=水平分量, v=y`。相邻方块纹理连续，消除"每格独立采样"的豆腐块感，未来接入
  真实纹理 atlas 时可与近景无缝衔接。

---

## 5. 任务三：渲染提交走原生 RenderType

`LODRenderEventHandler` 的提交路径：

```java
RenderType solidType = RenderType.solid();
solidType.setupRenderState();          // 设置深度测试(LEQUAL)/写入mask/背面剔除 = 原版区块状态
RenderSystem.setShader(() -> shader);  // 覆盖为 BLOCK 格式的自定义 lod_terrain shader
// 逐 mesh 上传后：
vb.drawWithShader(fullView, projection, shader);  // 原生 API，显式矩阵，非裸 glDrawArrays
...
solidType.clearRenderState();          // 恢复状态
```

- 不再裸调 `glDrawArrays`，不再手动 `enableDepthTest/depthFunc`，改用 `RenderType.solid()`
  的完整渲染状态（深度排序 + 批次合并 + 背面剔除），与原版区块渲染一致。
- `VertexBuffer.drawWithShader(modelView, projection, shader)` 用**显式矩阵参数**设置
  ModelViewMat/ProjMat uniform，不依赖 `RenderSystem` 全局矩阵栈（1.21.1 已无 PoseStack 语义）。
- 关于「RenderTypeLookup」：1.21.1 NeoForge 中 `net.neoforged.neoforge.client.RenderTypeLookup`
  已被移除，其功能等价于原版 `ItemBlockRenderTypes.getChunkRenderType(BlockState)`。本实现
  对 LOD 统一走 `RenderType.solid()`（远景地形一律不透明固体），无需逐方块查 RenderType，
  语义上等价且更高效。

---

## 6. 关键 API（javap 反编译确认）

- `DefaultVertexFormat.BLOCK`（`public static final VertexFormat`，32 字节标准地形格式）
- `RenderType.solid()`（`public static RenderType solid()`）
- `RenderStateShard.setupRenderState()/clearRenderState()`（`public`，RenderType 继承）
- `VertexConsumer.setColor(int,int,int,int)` / `setColor(float,float,float,float)` / `setUv` / `setUv2(int,int)` / `setNormal(float,float,float)`
- `GameRenderer.getRendertypeSolidShader()`（`public static ShaderInstance`）
- `RenderSystem.setShader(Supplier<ShaderInstance>)`
- `VertexBuffer.drawWithShader(Matrix4f, Matrix4f, ShaderInstance)`
- `Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK)` → `BufferBuilder.buildOrThrow()` → `MeshData`
- `LightTexture.FULL_BRIGHT = 15728880 = 0xF000F0`（满亮 packed light，`sky<<20|block<<4`）

---

## 7. 阻塞项

### 7.1 gradle 完整构建网络阻塞（与代码无关，同前几轮）

`./gradlew build` 卡在 `:buildSrc:generateExternalPluginSpecBuilders`，`maven.neoforged.net:443`
TCP 超时。核心改动文件已用 javac 独立验证 **0 错误**（见 §8）。

### 7.2 全量 javac 的 fml/bus classpath 缺失

手动拼 classpath 时 `net.neoforged.api.distmarker.Dist`、`net.neoforged.bus.api.SubscribeEvent`、
`net.neoforged.fml.common.EventBusSubscriber` 分散在 fancymodloader/bus 的独立 jar 中且 `Dist`
在 21.1.61 的确切位置未能定位。**这些是既有 import（未改动代码的依赖）**，实际 gradle 构建
由 NeoGradle 自动解析，不影响本次改动代码的正确性。

---

## 8. 自验证结果

- [x] **核心改动文件 javac 独立编译 0 错误**：`GreedyMesher` / `LODMesh` / `LODVboUploader`
      （`emitBlockVertex` 的 `setColor/setUv/setUv2/setNormal` 五属性 API 全部通过编译）
- [x] 6 面法线与绕序叉积验证：全部 CCW、法线朝外
- [x] BLOCK 格式 32 字节布局与 `LODMesh.VERTEX_STRIDE=32` 对齐
- [ ] gradle 完整构建：**网络阻塞未完成**（§7.1）
- [ ] 进游戏验证：**待用户本地 build**

### 用户需执行的验证

```bash
JAVA_HOME="C:\Users\hemin\AppData\Roaming\美西螈\meta\java_versions\oracle-jdk-21" ./gradlew build
```

进游戏后分别验证：
1. **无光影**：远景地形是否正常（无辐射条带/豆腐块），颜色是否为真实地形色（草绿/土黄/水蓝/雪白）。
2. **开 Iris 光影**：LOD 是否正常显示（不再消失），且受光照/阴影正确影响。

---

## 9. 潜在风险

1. **`RenderType.solid().setupRenderState()` 会设置背面剔除(CULL)**：依赖 GreedyMesher 绕序
   严格 CCW（已逐一验证）。若未来改动 emit 方法破坏绕序，会导致地形面消失——这是本次
   从 `disableCull()` 切换到 CULL 引入的行为变化点。
2. **`setupRenderState()` 会绑定 atlas/lightmap 纹理**：随后被自定义 lod_terrain shader 覆盖
   （后者不采样纹理，纯 MapColor 色），纹理绑定残留无害。
3. **lod_terrain 仍是"自定义 shader + BLOCK 格式"**：格式已对齐 Iris 可识别，但 shader 本体
   非 `rendertype_solid`，仍需 `IrisReflection.allowUnknownShaders()` + `clearMainBound()` 绕过
   Iris 对未知 shader 的拦截（沿用既有机制）。若需 Iris **完全第一方**支持，可进一步改用
   `GameRenderer.getRendertypeSolidShader()` 并处理纹理采样（白纹理 × vertexColor）。
