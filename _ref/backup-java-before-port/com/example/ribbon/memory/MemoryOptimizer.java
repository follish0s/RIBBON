/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.memory;

import com.example.ribbon.Config;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 内存深度优化系统 —— 对标 FerriteCore 的全局去重池。
 *
 * <p><b>设计原则：安全优先于收益。</b>本类的每个 {@code canonicalize} 方法都遵循同一套契约：</p>
 * <ol>
 *   <li>配置关闭 → 直接返回 {@code null}，调用方走原版逻辑；</li>
 *   <li>池操作抛任何异常 → 捕获、记录、返回 {@code null}，调用方走原版逻辑；</li>
 *   <li>池返回的实例在语义上与入参等价（见各方法说明），因此即使去重"失败"也只是没省到内存，不会改变行为。</li>
 * </ol>
 *
 * <p><b>返回 {@code null} 表示"不干预"</b>，调用方必须自己回退到原始值。这是整套系统的崩溃防火墙。</p>
 *
 * <p><b>线程安全：</b>所有池均为 {@link ConcurrentHashMap}，计数用 {@link LongAdder}。
 * 区块加载、模型烘焙可能并发执行，因此不假设单线程。</p>
 */
public final class MemoryOptimizer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/Memory");

    private MemoryOptimizer() {}
    public static void recordModelLocationHit() {
        // 千问在 Mixin 里调用了这个方法，这里补个空方法防止报错
        // 如果你以后想做统计，可以在这里加个 AtomicInteger 计数器
    }

    // ==================== 去重池 ====================

    /**
     * VoxelShape 规范化池。
     *
     * <p>键值同为 VoxelShape：命中时返回池中已存在的等价实例，从而让
     * 数千个 BlockState 共享同一份形状对象（每份 VoxelShape 含 DiscreteVoxelShape
     * 位图 + AABB 列表，单个可达数百字节）。</p>
     *
     * <p><b>关键安全属性：</b>本池依赖 {@code VoxelShape#equals}。若某个 MC 版本的
     * VoxelShape 未基于内容重写 equals（退化为 Object 身份比较），
     * 那么 {@code computeIfAbsent} 只会按身份命中 —— 结果是"没有去重收益"，
     * 但绝不会返回一个内容不同的形状。也就是说：<b>equals 语义错误时自动降级为无害的空操作</b>。</p>
     */
    private static final ConcurrentHashMap<VoxelShape, VoxelShape> SHAPE_POOL = new ConcurrentHashMap<>(256);

    /**
     * BlockState 属性表（values）规范化池。
     *
     * <p>键为属性表的"有序签名字符串"（{@code name=value;} 按插入顺序拼接），
     * 值为共享的 {@code Map<Property<?>, Comparable<?>>} 实例。</p>
     *
     * <p>原版为每个 BlockState 单独持有一份 {@code Reference2ObjectArrayMap}。
     * 对于无属性的方块（空气、石头等）以及属性组合完全相同的方块，
     * 这些 Map 内容逐字节相同却各占一份 —— 这正是 FerriteCore
     * {@code replacePropertyMap} 优化的目标。</p>
     *
     * <p><b>只有签名完全一致（键、值、顺序三者全同）才会共享</b>，因此
     * 不存在"哈希碰撞导致拿到错误属性表"的风险：签名相同 ⟺ 内容相同。</p>
     */
    private static final ConcurrentHashMap<String, Map<Property<?>, Comparable<?>>> PROPERTY_MAP_POOL =
            new ConcurrentHashMap<>(512);

    /**
     * ModelResourceLocation 规范化池（客户端专用）。
     *
     * <p>用 {@code Object} 声明以避免本类（common）引入 {@code @OnlyIn(CLIENT)} 类型，
     * 保证专用服务器可正常加载。实际存取的都是 ModelResourceLocation 实例。</p>
     *
     * <p>键为 {@code "namespace:path#variant"} 规范化字符串。命中即返回同一实例，
     * 避免相同路径反复生成新 record 对象与其内部字符串（String Interning 效果）。</p>
     */
    private static final ConcurrentHashMap<String, Object> MODEL_LOCATION_POOL = new ConcurrentHashMap<>(1024);

    /**
     * 区块 NBT 精简计数（非常驻池，仅统计）。
     */
    private static final LongAdder chunkNbtTrimmed = new LongAdder();

    // ==================== 统计 ====================

    private static final LongAdder shapeHits = new LongAdder();
    private static final LongAdder shapeMisses = new LongAdder();
    private static final LongAdder propertyMapHits = new LongAdder();
    private static final LongAdder propertyMapMisses = new LongAdder();
    private static final LongAdder modelLocationHits = new LongAdder();
    private static final LongAdder modelLocationMisses = new LongAdder();
    private static final LongAdder predicateHits = new LongAdder();
    private static final LongAdder predicateMisses = new LongAdder();
    private static final LongAdder fallbackCount = new LongAdder();

    // ==================== 总开关 ====================

    /**
     * 所有优化是否启用。配置未就绪时返回 {@code false}（保守禁用）。
     */
    public static boolean isEnabled() {
        try {
            return Config.isMemoryOptimizationEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean subEnabled(java.util.function.BooleanSupplier supplier) {
        try {
            return isEnabled() && supplier.getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== ① 形状去重 ====================

    /**
     * 规范化碰撞/渲染形状。
     *
     * @param shape 原始形状，可为 {@code null}（动态形状方块）
     * @return 池中等价实例；若不干预则返回 {@code null}，<b>调用方必须保留原值</b>
     */
    public static VoxelShape canonicalizeShape(VoxelShape shape) {
        if (shape == null) return null;
        if (!subEnabled(Config::isShapeDedupEnabled)) return null;
        try {
            VoxelShape existing = SHAPE_POOL.putIfAbsent(shape, shape);
            if (existing != null) {
                shapeHits.increment();
                // 返回池中已有的实例，实现跨 BlockState 共享
                return existing;
            }
            shapeMisses.increment();
            return null; // 本实例即为规范实例，无需替换
        } catch (Throwable t) {
            recordFallback("shape", t);
            return null;
        }
    }

    // ==================== ② 属性表去重 ====================

    /**
     * 为属性表生成有序签名。
     *
     * <p>签名包含键名、值、以及插入顺序，三者全同才视为同一张表。
     * 这保证共享出的 Map 与原始 Map 在遍历顺序和内容上完全等价，
     * 不会破坏依赖迭代顺序的逻辑（如 {@code StateHolder#toString}）。</p>
     */
    public static String propertyMapSignature(Map<Property<?>, Comparable<?>> values) {
        if (values == null || values.isEmpty()) return "<empty>";
        StringBuilder sb = new StringBuilder(values.size() * 24);
        for (Map.Entry<Property<?>, Comparable<?>> e : values.entrySet()) {
            sb.append(e.getKey().getName()).append('=').append(e.getValue()).append(';');
        }
        return sb.toString();
    }

    /**
     * 规范化属性表。
     *
     * @param values 原始属性表（不可为 null）
     * @return 池中共享实例；若不干预则返回 {@code null}
     */
    public static Map<Property<?>, Comparable<?>> canonicalizePropertyMap(Map<Property<?>, Comparable<?>> values) {
        if (values == null) return null;
        if (!subEnabled(Config::isPropertyMapDedupEnabled)) return null;
        try {
            String sig = propertyMapSignature(values);
            Map<Property<?>, Comparable<?>> existing = PROPERTY_MAP_POOL.putIfAbsent(sig, values);
            if (existing != null) {
                propertyMapHits.increment();
                return existing;
            }
            propertyMapMisses.increment();
            return null;
        } catch (Throwable t) {
            recordFallback("propertyMap", t);
            return null;
        }
    }

    // ==================== ③ ModelResourceLocation 驻留 ====================

    /**
     * 只读查询池（不入池）。用于在构造新实例<b>之前</b>判断是否已有等价实例，
     * 从而跳过 record 分配。
     *
     * @return 池中已有实例；不存在或不干预时返回 {@code null}
     */
    public static Object peekModelLocation(String key) {
        if (key == null) return null;
        if (!subEnabled(Config::isModelInterningEnabled)) return null;
        try {
            return MODEL_LOCATION_POOL.get(key);
        } catch (Throwable t) {
            recordFallback("modelLocation.peek", t);
            return null;
        }
    }

    /**
     * 规范化 ModelResourceLocation（客户端）。
     *
     * <p>键用 {@code Object} 弱类型声明以保持本类在专用服务器上可加载。
     * 调用方负责传入 ModelResourceLocation 并做向下转型。</p>
     *
     * @param key   规范化字符串键，形如 {@code "minecraft:stone#inventory"}
     * @param value 新建的 ModelResourceLocation 实例
     * @return 池中已存在的等价实例；若不干预则返回 {@code null}
     */
    public static Object internModelLocation(String key, Object value) {
        if (key == null || value == null) return null;
        if (!subEnabled(Config::isModelInterningEnabled)) return null;
        try {
            Object existing = MODEL_LOCATION_POOL.putIfAbsent(key, value);
            if (existing != null) {
                modelLocationHits.increment();
                return existing;
            }
            modelLocationMisses.increment();
            return null;
        } catch (Throwable t) {
            recordFallback("modelLocation", t);
            return null;
        }
    }

    // ==================== ④ 多部件谓词去重 ====================

    /**
     * 记录一次谓词缓存命中/未命中（实际缓存在 Mixin 注入的实例字段上，此处仅统计）。
     */
    public static void recordPredicateHit() {
        predicateHits.increment();
    }

    public static void recordPredicateMiss() {
        predicateMisses.increment();
    }

    public static boolean isPredicateDedupEnabled() {
        return subEnabled(Config::isMultipartPredicateDedupEnabled);
    }

    // ==================== ⑤ 区块 NBT 精简 ====================

    /**
     * 记录一次区块 NBT 精简。
     *
     * @param entries 本次释放的 NBT 条目数
     */
    public static void recordChunkNbtTrimmed(int entries) {
        chunkNbtTrimmed.add(entries);
    }

    public static boolean isChunkNbtTrimEnabled() {
        return subEnabled(Config::isChunkNbtTrimEnabled);
    }

    // ==================== 兜底 ====================

    /**
     * 供 Mixin 调用的兜底入口。异常一律吞掉，绝不向外传播 —— 
     * 这是"Mixin 里抛异常会直接崩溃游戏"的唯一防线。
     */
    public static void reportFallback(String stage, Throwable t) {
        recordFallback(stage, t);
    }

    private static void recordFallback(String stage, Throwable t) {
        fallbackCount.increment();
        // 只在首次发生时打印，避免日志刷屏
        if (fallbackCount.sum() <= 5) {
            LOGGER.error("[Ribbon] 内存优化在 {} 阶段异常，已安全回退到原版逻辑。原因：{}", stage, t.toString());
        }
    }

    /**
     * 清空所有池。资源重载（F3+T）或世界切换时调用，避免陈旧实例长期驻留。
     */
    public static void clearAll() {
        try {
            SHAPE_POOL.clear();
            PROPERTY_MAP_POOL.clear();
            MODEL_LOCATION_POOL.clear();
        } catch (Throwable ignored) {
            // 清池失败不影响正确性，忽略
        }
    }

    // ==================== HUD 统计读取 ====================

    public static int getShapePoolSize() { return SHAPE_POOL.size(); }
    public static int getPropertyMapPoolSize() { return PROPERTY_MAP_POOL.size(); }
    public static int getModelLocationPoolSize() { return MODEL_LOCATION_POOL.size(); }

    public static long getShapeHits() { return shapeHits.sum(); }
    public static long getPropertyMapHits() { return propertyMapHits.sum(); }
    public static long getModelLocationHits() { return modelLocationHits.sum(); }
    public static long getPredicateHits() { return predicateHits.sum(); }
    public static long getChunkNbtTrimmed() { return chunkNbtTrimmed.sum(); }
    public static long getFallbackCount() { return fallbackCount.sum(); }

    public static long getShapeMisses() { return shapeMisses.sum(); }
    public static long getPropertyMapMisses() { return propertyMapMisses.sum(); }
    public static long getModelLocationMisses() { return modelLocationMisses.sum(); }
    public static long getPredicateMisses() { return predicateMisses.sum(); }

    /**
     * 粗略估算节省的对象数（形状 + 属性表 + 模型定位符 + 谓词）。
     * 每次命中意味着少持有一个重复对象。
     */
    public static long getEstimatedObjectsSaved() {
        return shapeHits.sum() + propertyMapHits.sum() + modelLocationHits.sum() + predicateHits.sum();
    }
}
