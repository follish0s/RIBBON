/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.render;

import com.example.ribbon.lod.mesh.LODMesh;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class LODVboUploader {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/LOD-Vbo");

    private static final boolean FORCE_REUPLOAD_EVERY_FRAME = false;

    /**
     * 【颜色验证开关】
     * 已确认通路打通，改为 false 以使用 CPU 侧真实颜色。
     * 若后续需要再次验证颜色属性绑定，可临时改回 true。
     */
    private static final boolean FORCE_DEBUG_COLOR = false;

    private static volatile boolean diagnoseBytesPrinted = false;
    private static volatile boolean diagnoseIndexPrinted = false;
    private static volatile long uploadCount = 0L;

    private LODVboUploader() {
    }

    public static VertexBuffer ensureUploaded(LODMesh mesh) {
        if (mesh == null || mesh.vertexCount() == 0) return null;
        if (!FORCE_REUPLOAD_EVERY_FRAME) {
            if (mesh.gpuUploaded(mesh.sourceVersion())) {
                return mesh.gpuBuffer();
            }
        }
        return upload(mesh);
    }

    private static VertexBuffer upload(LODMesh mesh) {
        try {
            int verts = mesh.vertexCount();
            int quadCount = verts / 4;
            if (quadCount == 0) return null;

            // 【关键重构】VBO 格式升级为 DefaultVertexFormat.BLOCK —— Iris 唯一能识别的
            // 标准地形顶点格式。此前用 POSITION_COLOR 丢掉了 UV0/UV2/Normal，导致 Iris 的
            // GBuffer/Shadow Pass 无法正确读取光照贴图与法线，光影下 LOD 直接消失。
            // QUADS：与原版区块渲染一致，交给原生管线做深度排序与批次合并（不再裸画）。
            Tesselator tess = Tesselator.getInstance();
            BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

            // 源 buffer 默认 BIG_ENDIAN，必须显式小端，否则 getFloat 读出反序浮点。
            ByteBuffer data = mesh.vertexData().duplicate().order(ByteOrder.LITTLE_ENDIAN);
            data.rewind();

            for (int q = 0; q < quadCount; q++) {
                // 每个 QUAD 4 个顶点，逐顶点读出完整 BLOCK 属性并写回 BufferBuilder。
                for (int i = 0; i < 4; i++) {
                    float x = data.getFloat();
                    float y = data.getFloat();
                    float z = data.getFloat();
                    int argb = data.getInt();       // Color (ARGB, alpha=0xFF)
                    float u = data.getFloat();      // UV0
                    float v = data.getFloat();
                    int lightU = data.getShort() & 0xFFFF; // UV2/Lightmap
                    int lightV = data.getShort() & 0xFFFF;
                    float nx = data.get() / 127f;   // Normal (signed byte [-127,127] → [-1,1])
                    float ny = data.get() / 127f;
                    float nz = data.get() / 127f;
                    data.get();                     // padding

                    emitBlockVertex(bb, x, y, z, argb, u, v, lightU, lightV, nx, ny, nz);
                }
            }

            MeshData meshData = bb.buildOrThrow();

            ribbon$diagnoseBytes(mesh, meshData, verts, quadCount);
            ribbon$diagnoseIndex(meshData, verts, quadCount);

            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(meshData);
            VertexBuffer.unbind();

            VertexBuffer old = mesh.gpuBuffer();
            mesh.setGpuBuffer(vb, mesh.sourceVersion());
            if (old != null && old != vb) {
                try {
                    old.close();
                } catch (Throwable ignored) {
                }
            }

            uploadCount++;
            return vb;
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon/LOD] upload mesh({},{}) failed: {}",
                    mesh.chunkX(), mesh.chunkZ(), t.toString());
            return null;
        }
    }

    /**
     * 按 {@code DefaultVertexFormat.BLOCK} 写入一个完整顶点。
     * <p>必须严格覆盖 5 个属性：Position(XYZ)、Color(RGBA)、UV0(XY)、UV2(Lightmap)、
     * Normal(3×signed byte)。任一缺失都会导致 {@code BufferBuilder} 在 build 时抛
     * {@code IllegalStateException: Not filled all elements of the vertex}。</p>
     *
     * @param lightU,lightV 光照贴图 UV（整型，满亮 240）
     * @param nx,ny,nz      单位法线（面朝向，光影光照计算依赖）
     */
    private static void emitBlockVertex(BufferBuilder bb, float x, float y, float z, int argb,
                                        float u, float v, int lightU, int lightV,
                                        float nx, float ny, float nz) {
        int a, r, g, b;
        if (FORCE_DEBUG_COLOR) {
            r = 0; g = 255; b = 0; a = 255; // 醒目绿诊断
        } else {
            // CPU 侧是 ARGB：A=bits24-31, R=16-23, G=8-15, B=0-7
            a = (argb >>> 24) & 0xFF;
            r = (argb >>> 16) & 0xFF;
            g = (argb >>> 8) & 0xFF;
            b = argb & 0xFF;
        }
        bb.addVertex(x, y, z)
                .setColor(r / 255f, g / 255f, b / 255f, a / 255f)
                .setUv(u, v)
                .setUv2(lightU, lightV)
                .setNormal(nx, ny, nz);
    }

    private static void ribbon$diagnoseBytes(LODMesh mesh, MeshData meshData, int verts, int quadCount) {
        if (diagnoseBytesPrinted) return;
        diagnoseBytesPrinted = true;
        try {
            ByteBuffer srcRaw = mesh.vertexData();
            int srcLimit = srcRaw.limit();
            int srcPosition = srcRaw.position();
            ByteBuffer src = srcRaw.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            src.rewind();

            float sx = Float.NaN, sy = Float.NaN, sz = Float.NaN;
            StringBuilder srcHex = new StringBuilder();
            if (src.limit() >= 12) {
                for (int k = 0; k < 12; k++) {
                    srcHex.append(String.format("%02X", src.get(k) & 0xFF));
                    if (k < 11) srcHex.append(' ');
                }
                sx = src.getFloat();
                sy = src.getFloat();
                sz = src.getFloat();
            }

            ByteBuffer dstRaw = ribbon$meshDataByteBuffer(meshData);
            ByteOrder dstOrder = (dstRaw != null) ? dstRaw.order() : null;
            int dstLimit = (dstRaw != null) ? dstRaw.limit() : -1;

            float dx = Float.NaN, dy = Float.NaN, dz = Float.NaN;
            StringBuilder dstHex = new StringBuilder();
            if (dstRaw != null) {
                ByteBuffer dst = dstRaw.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                dst.rewind();
                if (dst.limit() >= 12) {
                    for (int k = 0; k < 12; k++) {
                        dstHex.append(String.format("%02X", dst.get(k) & 0xFF));
                        if (k < 11) dstHex.append(' ');
                    }
                    dx = dst.getFloat();
                    dy = dst.getFloat();
                    dz = dst.getFloat();
                }
            } else {
                dstHex.append("<reflect read byteBuffer() failed>");
            }

            int stateVerts = meshData.drawState().vertexCount();

            boolean srcReadable = srcLimit >= 12 && !Float.isNaN(sx);
            boolean dstReadable = dstRaw != null && dstLimit >= 12 && !Float.isNaN(dx);
            boolean srcDstMatch = srcReadable && dstReadable
                    && (Float.compare(sx, dx) == 0)
                    && (Float.compare(sy, dy) == 0)
                    && (Float.compare(sz, dz) == 0);

            boolean flipMissing = verts > 0 && srcLimit == 0;

            LOGGER.info("[Ribbon/LOD-Vbo][bytes] mesh=({},{}) vertexCount={} quad={} "
                            + "srcLimit={}B srcPos={} dstLimit={}B dstOrder={} "
                            + "| srcFirst=({},{},{}) hex[{}] | dstFirst=({},{},{}) hex[{}] "
                            + "| drawStateVerts={} | match={} | flipMissing={}",
                    mesh.chunkX(), mesh.chunkZ(), verts, quadCount,
                    srcLimit, srcPosition, dstLimit, dstOrder,
                    sx, sy, sz, srcHex,
                    dx, dy, dz, dstHex,
                    stateVerts, srcDstMatch, flipMissing);
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon/LOD-Vbo][bytes] diagnose failed: {}", t.toString());
        }
    }

    private static ByteBuffer ribbon$meshDataByteBuffer(MeshData meshData) {
        if (meshData == null) return null;
        try {
            Method acc = MeshData.class.getMethod("vertexBuffer");
            Object result = acc.invoke(meshData);
            if (result != null) {
                ByteBuffer bb = ribbon$firstByteBuffer(result);
                if (bb != null) return bb;
            }
        } catch (Throwable ignored) {
        }
        ByteBuffer fromSelf = ribbon$firstByteBuffer(meshData);
        if (fromSelf != null) return fromSelf;
        return null;
    }

    private static ByteBuffer ribbon$firstByteBuffer(Object holder) {
        if (holder == null) return null;
        Class<?> cls = holder.getClass();
        for (Method m : cls.getMethods()) {
            if (m.getParameterCount() == 0
                    && ByteBuffer.class.isAssignableFrom(m.getReturnType())
                    && m.getDeclaringClass() != Object.class) {
                try {
                    m.setAccessible(true);
                    Object b = m.invoke(holder);
                    if (b instanceof ByteBuffer bb) return bb;
                } catch (Throwable ignored) {
                }
            }
        }
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (ByteBuffer.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object b = f.get(holder);
                        if (b instanceof ByteBuffer bb) return bb;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static void ribbon$diagnoseIndex(MeshData meshData, int verts, int quadCount) {
        if (diagnoseIndexPrinted) return;
        diagnoseIndexPrinted = true;
        try {
            MeshData.DrawState drawState = meshData.drawState();
            int indexCount = drawState.indexCount();
            VertexFormat.Mode mode = drawState.mode();
            VertexFormat.IndexType indexType = drawState.indexType();

            int candidateA = verts;          // QUADS：每顶点一个 sequential index
            int candidateB = quadCount * 4;  // QUADS：4 顶点 = 1 quad

            String capacityInfo = "reflect failed";
            boolean indexStorageInsufficient = false;
            Object autoStorage = null;
            for (Class<?>[] sig : new Class<?>[][]{
                    {VertexFormat.IndexType.class},
                    {int.class},
                    {}
            }) {
                try {
                    Method getter = VertexBuffer.class.getDeclaredMethod("getSequentialBuffer", sig);
                    getter.setAccessible(true);
                    Object candidate = (sig.length == 0)
                            ? getter.invoke(null)
                            : (sig[0] == VertexFormat.IndexType.class
                            ? getter.invoke(null, indexType)
                            : getter.invoke(null, indexCount));
                    if (candidate != null && candidate.getClass().getSimpleName().contains("AutoStorageIndexBuffer")) {
                        autoStorage = candidate;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }

            if (autoStorage != null) {
                try {
                    Field f = autoStorage.getClass().getDeclaredField("indexCount");
                    f.setAccessible(true);
                    int allocated = f.getInt(autoStorage);
                    capacityInfo = "allocated=" + allocated;
                    try {
                        Method hasStorage = autoStorage.getClass().getDeclaredMethod("hasStorage", int.class);
                        hasStorage.setAccessible(true);
                        boolean fits = (boolean) hasStorage.invoke(autoStorage, indexCount);
                        capacityInfo += " hasStorage(" + indexCount + ")=" + fits;
                        if (!fits) indexStorageInsufficient = true;
                    } catch (Throwable ignored) {
                    }
                    if (allocated < indexCount) indexStorageInsufficient = true;
                } catch (Throwable t) {
                    capacityInfo = "got AutoStorage but read field failed: " + t;
                }
            }

            boolean shortOverflow = (indexType == VertexFormat.IndexType.SHORT) && (verts > 65536);
            if (shortOverflow) indexStorageInsufficient = true;

            LOGGER.info("[Ribbon/LOD-Vbo][index] mode={} indexType={} indexCount={} "
                            + "candidateA={} candidateB={} verts={} quad={} seqIdx={} "
                            + "insufficient={} shortOverflow={}",
                    mode, indexType, indexCount, candidateA, candidateB,
                    verts, quadCount, capacityInfo, indexStorageInsufficient, shortOverflow);
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon/LOD-Vbo][index] diagnose failed: {}", t.toString());
        }
    }

    public static long getUploadCount() {
        return uploadCount;
    }
}