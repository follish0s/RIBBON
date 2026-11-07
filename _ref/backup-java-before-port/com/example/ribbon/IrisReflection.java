package com.example.ribbon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 用反射访问 Iris，避免编译期依赖 Iris。
 * 如果 Iris 未安装或 API 变化，所有方法静默返回 null / false。
 */
public final class IrisReflection {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/IrisReflection");

    private static boolean initialized = false;
    private static boolean irisPresent = false;

    private static Method getIrisConfigMethod;
    private static Method getShaderpacksDirectoryMethod;
    private static Method setShadersEnabledStaticMethod;
    private static Method getShaderPackNameMethod;
    private static Method setShadersEnabledConfigMethod;
    private static Method saveConfigMethod;
    private static Method areShadersEnabledMethod;

    /** IrisConfig.allowUnknownShaders 字段（反射直写，避免 setUnknown 触发 save 写盘）。 */
    private static java.lang.reflect.Field allowUnknownShadersField;

    /** 是否已成功把 allowUnknownShaders 置 true。 */
    private static boolean unknownShadersAllowed = false;

    /** PipelineManager.getPipelineNullable() 方法（拿当前 WorldRenderingPipeline）。 */
    private static Method getPipelineNullableMethod;

    /** WorldRenderingPipeline.setIsMainBound(boolean) 方法。 */
    private static Method setIsMainBoundMethod;

    /** 是否已成功拿到 setIsMainBound 反射句柄。 */
    private static boolean setMainBoundAvailable = false;

    private IrisReflection() {}

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            irisPresent = true;

            try {
                getIrisConfigMethod = irisClass.getMethod("getIrisConfig");
            } catch (NoSuchMethodException ignored) {}

            try {
                getShaderpacksDirectoryMethod = irisClass.getMethod("getShaderpacksDirectory");
            } catch (NoSuchMethodException ignored) {}

            try {
                setShadersEnabledStaticMethod = irisClass.getMethod("setShadersEnabled", boolean.class);
            } catch (NoSuchMethodException ignored) {}

            if (getIrisConfigMethod != null) {
                Class<?> configClass = getIrisConfigMethod.getReturnType();

                try {
                    getShaderPackNameMethod = configClass.getMethod("getShaderPackName");
                } catch (NoSuchMethodException ignored) {}

                try {
                    setShadersEnabledConfigMethod = configClass.getMethod("setShadersEnabled", boolean.class);
                } catch (NoSuchMethodException ignored) {}

                try {
                    saveConfigMethod = configClass.getMethod("save");
                } catch (NoSuchMethodException ignored) {}

                // 反射拿 allowUnknownShaders 字段：用于绕过 Iris 对未知 shader 的
                // apply() 拦截（禁用颜色写入）。只设字段，不调 setUnknown（避免写盘）。
                try {
                    allowUnknownShadersField = configClass.getDeclaredField("allowUnknownShaders");
                    allowUnknownShadersField.setAccessible(true);
                } catch (NoSuchFieldException ignored) {}

                // 尝试多种方法名，Iris 不同版本可能不同
                for (String name : new String[]{"areShadersEnabled", "getShadersEnabled", "isShadersEnabled"}) {
                    try {
                        areShadersEnabledMethod = configClass.getMethod(name);
                        LOGGER.info("[Ribbon] 找到 Iris 光影启用状态方法: {}", name);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
            }

            LOGGER.info("[Ribbon] Iris 反射初始化完成");
        } catch (ClassNotFoundException e) {
            LOGGER.info("[Ribbon] Iris 未安装，光影兼容性检测已禁用");
        }
    }

    /** 延迟初始化 setIsMainBound 反射句柄（需要 Iris 已加载）。 */
    private static synchronized void initSetMainBound() {
        if (setMainBoundAvailable || !irisPresent) return;
        init();
        if (!irisPresent) return;
        try {
            Class<?> pipelineManagerClass = Class.forName(
                    "net.irisshaders.iris.pipeline.PipelineManager");
            getPipelineNullableMethod = pipelineManagerClass.getMethod("getPipelineNullable");

            Class<?> pipelineClass = Class.forName(
                    "net.irisshaders.iris.pipeline.WorldRenderingPipeline");
            setIsMainBoundMethod = pipelineClass.getMethod("setIsMainBound", boolean.class);
            setMainBoundAvailable = true;
            LOGGER.info("[Ribbon] Iris setIsMainBound 反射句柄就绪");
        } catch (Throwable t) {
            setMainBoundAvailable = false;
            LOGGER.debug("[Ribbon] 获取 Iris setIsMainBound 失败: {}", t.toString());
        }
    }

    /**
     * 临时把 Iris 的 {@code isMainBound} 设为 {@code false}，使
     * {@code shouldOverrideShaders() = isRenderingWorld && isMainBound} 返回 false。
     *
     * <h2>根因（字节码级铁证）</h2>
     * <p>即使 {@code allowUnknownShaders=true} 让 {@code iris$shouldSkipThis()} 返回 false，
     * {@code MixinShaderInstance.onTail()}（apply 后回调）仍会在
     * {@code shouldSkipThis()==false && isKnownShader()==false && shouldOverrideShaders()==true}
     * 时调用 {@code pipeline.bindDefault()}，把 framebuffer 切到 Iris 的默认 framebuffer，
     * 导致 LOD 画进去后被 composite 覆盖。置 {@code isMainBound=false} 后
     * {@code shouldOverrideShaders()==false}，{@code onTail}/{@code unlockDepthColorState}
     * 直接 return，LOD 就能画在当前（主）framebuffer。</p>
     *
     * @return true 表示已置 false（需要调用方在 draw 后调 restoreMainBound 恢复）
     */
    public static boolean clearMainBound() {
        initSetMainBound();
        if (!setMainBoundAvailable) return false;
        try {
            Object pipelineManager = Class.forName(
                    "net.irisshaders.iris.Iris").getMethod("getPipelineManager").invoke(null);
            Object pipeline = getPipelineNullableMethod.invoke(pipelineManager);
            if (pipeline == null) return false;
            setIsMainBoundMethod.invoke(pipeline, false);
            return true;
        } catch (Throwable t) {
            LOGGER.debug("[Ribbon] clearMainBound 失败: {}", t.toString());
            return false;
        }
    }

    /**
     * 恢复 Iris 的 {@code isMainBound} 为 {@code true}（与 {@link #clearMainBound()} 配对）。
     */
    public static boolean restoreMainBound() {
        initSetMainBound();
        if (!setMainBoundAvailable) return false;
        try {
            Object pipelineManager = Class.forName(
                    "net.irisshaders.iris.Iris").getMethod("getPipelineManager").invoke(null);
            Object pipeline = getPipelineNullableMethod.invoke(pipelineManager);
            if (pipeline == null) return false;
            setIsMainBoundMethod.invoke(pipeline, true);
            return true;
        } catch (Throwable t) {
            LOGGER.debug("[Ribbon] restoreMainBound 失败: {}", t.toString());
            return false;
        }
    }

    public static boolean isIrisPresent() {
        init();
        return irisPresent;
    }

    /**
     * 获取当前选中的光影包路径。
     * 无法获取时返回 null。
     */
    public static Path getCurrentShaderpackPath() {
        init();
        if (!irisPresent || getIrisConfigMethod == null) return null;

        try {
            Object config = getIrisConfigMethod.invoke(null);
            if (config == null) return null;

            String name = null;
            if (getShaderPackNameMethod != null) {
                Object result = getShaderPackNameMethod.invoke(config);
                if (result instanceof Optional<?> opt) {
                    if (opt.isPresent()) {
                        name = String.valueOf(opt.get());
                    }
                } else if (result instanceof String s) {
                    name = s;
                }
            }

            if (name == null || name.isEmpty()) return null;

            if (getShaderpacksDirectoryMethod != null) {
                Object dirObj = getShaderpacksDirectoryMethod.invoke(null);
                if (dirObj instanceof Path dir) {
                    return dir.resolve(name);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Ribbon] 获取当前光影包路径失败", e);
        }
        return null;
    }

    /**
     * 查询 Iris 当前是否启用光影。
     * Iris 未安装或 API 不可用时返回 false。
     */
    public static boolean areShadersEnabled() {
        init();
        if (!irisPresent || getIrisConfigMethod == null || areShadersEnabledMethod == null) {
            return false;
        }
        try {
            Object config = getIrisConfigMethod.invoke(null);
            if (config == null) return false;
            Object result = areShadersEnabledMethod.invoke(config);
            if (result instanceof Boolean b) return b;
        } catch (Exception e) {
            LOGGER.debug("[Ribbon] 获取光影启用状态失败", e);
        }
        return false;
    }

    /**
     * Iris 的 batched entity rendering 用 {@code FullyBufferedMultiBufferSource}
     * 替换了 {@code Minecraft.renderBuffers().bufferSource()}，并把它继承的
     * {@code endBatch(RenderType)} 重写成了<b>空实现</b>（直接 return，不 flush）。
     *
     * <p>这意味着任何 mod 在光影开启期间调用 {@code bufferSource().endBatch(renderType)}
     * 都会静默失效 —— 顶点写进了缓冲但永远不会被 flush 成 draw call。这正是
     * LOD 远景地形"数据全对却看不见"的根因。</p>
     *
     * <p>正确做法是调用 Iris 自己的 {@code endBatchWithType(TransparencyType.OPAQUE)}
     * 按透明类型 flush。本方法通过反射完成，避免编译期依赖 Iris。</p>
     *
     * @param buffers 从 {@code Minecraft.renderBuffers().bufferSource()} 取得的实例
     * @return true 表示已通过 Iris 路径 flush；false 表示不是 Iris 的 batched buffer
     *         或反射失败，调用方应回退到原版 {@code endBatch(renderType)}。
     */
    public static boolean flushOpaqueViaIris(Object buffers) {
        init();
        if (!irisPresent || buffers == null) return false;

        try {
            Class<?> cls = buffers.getClass();
            if (!cls.getName().equals("net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource")) {
                return false;
            }

            Class<?> transparencyType = Class.forName(
                    "net.irisshaders.batchedentityrendering.impl.TransparencyType");
            Object opaque = null;
            for (Object constant : transparencyType.getEnumConstants()) {
                if ("OPAQUE".equals(((Enum<?>) constant).name())) {
                    opaque = constant;
                    break;
                }
            }
            if (opaque == null) return false;

            java.lang.reflect.Method endBatchWithType =
                    cls.getMethod("endBatchWithType", transparencyType);
            endBatchWithType.invoke(buffers, opaque);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon] Iris batched flush 失败，回退原版 endBatch: {}", t.toString());
            return false;
        }
    }

    /**
     * 把 Iris 的 {@code allowUnknownShaders} 置为 true，绕开 Iris 对"未知 shader"的
     * apply() 拦截。
     *
     * <h2>根因</h2>
     * <p>Iris 开启光影时（{@code isRenderingWorld=true}），会 hook {@code ShaderInstance.apply()}：
     * 对任何"非它认识"的普通 shader（非 {@code ExtendedShader}/{@code FallbackShader}），
     * {@code MixinShaderInstance.iris$shouldSkipThis()} 返回 true，进而调用
     * {@code DepthColorStorage.disableDepthColor()} <b>禁用颜色写入</b>，导致该 shader
     * 画出的几何完全不可见。LOD 自定义 shader 正是普通 ShaderInstance，因此被静默丢弃。</p>
     *
     * <p>而 {@code shouldSkipThis()} 在 {@code allowUnknownShaders=true} 时，对
     * {@code shouldSkip==NONE}（SkipList 对未知类默认 NONE）的 shader 直接返回 false，
     * 不再拦截。故只需把该字段置 true 即可让 LOD 正常显示。</p>
     *
     * <h2>实现注意</h2>
     * <p>直接反射写字段，<b>不</b>调 {@code IrisConfig.setUnknown(true)}——后者会触发
     * {@code save()} 写配置文件，有持久化副作用。这里只在内存改，不改用户配置。</p>
     *
     * @return true 表示已成功置位（本次或之前）；false 表示无法完成
     */
    public static boolean allowUnknownShaders() {
        init();
        if (unknownShadersAllowed) return true;
        if (!irisPresent || getIrisConfigMethod == null || allowUnknownShadersField == null) {
            return false;
        }
        try {
            Object config = getIrisConfigMethod.invoke(null);
            if (config == null) return false;
            allowUnknownShadersField.setBoolean(config, true);
            unknownShadersAllowed = true;
            LOGGER.info("[Ribbon] 已置 Iris allowUnknownShaders=true，绕过未知 shader 拦截");
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon] 置 allowUnknownShaders 失败: {}", t.toString());
            return false;
        }
    }

    /**
     * 尝试禁用 Iris 光影。
     * 成功返回 true，所有方案失败返回 false。
     */
    public static boolean disableShaders() {
        init();
        if (!irisPresent) return false;

        // 方案 1：Iris.setShadersEnabled(false)
        if (setShadersEnabledStaticMethod != null) {
            try {
                setShadersEnabledStaticMethod.invoke(null, false);
                LOGGER.info("[Ribbon] 已通过 Iris.setShadersEnabled(false) 禁用光影");
                return true;
            } catch (Exception e) {
                LOGGER.debug("[Ribbon] Iris.setShadersEnabled 调用失败", e);
            }
        }

        // 方案 2：IrisConfig.setShadersEnabled(false) + save()
        if (getIrisConfigMethod != null && setShadersEnabledConfigMethod != null) {
            try {
                Object config = getIrisConfigMethod.invoke(null);
                setShadersEnabledConfigMethod.invoke(config, false);
                if (saveConfigMethod != null) {
                    saveConfigMethod.invoke(config);
                }
                LOGGER.info("[Ribbon] 已通过 IrisConfig 禁用光影");
                return true;
            } catch (Exception e) {
                LOGGER.debug("[Ribbon] IrisConfig 禁用光影失败", e);
            }
        }

        LOGGER.warn("[Ribbon] 无法禁用光影，所有反射方案均失败");
        return false;
    }
}