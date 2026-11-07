/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin.memory;

import com.example.ribbon.memory.MemoryOptimizer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 区块 NBT 数据精简（对标 FerriteCore 的区块 NBT 清理）。
 *
 * <p><b>优化原理：</b>已核对 1.21.1 的 {@code LevelChunk} 含字段
 * {@code pendingBlockEntities : Map<BlockPos, CompoundTag>} 与方法
 * {@code registerAllBlockEntitiesAfterLevelLoad}。
 * 原版在区块从磁盘加载时，会把所有方块实体的 NBT 先暂存进
 * {@code pendingBlockEntities}；待区块完全加载后，
 * {@code registerAllBlockEntitiesAfterLevelLoad} 把这些 NBT
 * 转换成真正的 BlockEntity 实例。</p>
 *
 * <p><b>问题在于：</b>转换完成后，那些已经"消费掉"的
 * {@code CompoundTag} 仍然被 Map 强引用着，无法被 GC 回收。
 * 一个含箱子/漏斗/告示牌的区块，其 NBT 可达数 KB 到数十 KB，
 * 加载数百个区块后累积成可观的常驻内存。本 Mixin 在注册完成后
 * 清空该 Map，让这批 NBT 立即可回收。</p>
 *
 * <p><b>为什么选这个时机：</b>{@code registerAllBlockEntitiesAfterLevelLoad}
 * 的语义就是"把所有待处理 NBT 提升为正式方块实体"，执行完毕后
 * {@code pendingBlockEntities} 已完成使命。清空它不会影响
 * {@code blockEntities}（正式实例表）里的任何数据。</p>
 *
 * <p><b>为什么安全：</b></p>
 * <ul>
 *   <li>只调用 {@code Map#clear()}，<b>不替换 Map 引用本身</b>。
 *       任何已持有该 Map 引用的代码（如 {@code getBlockEntityNbts} 的返回值）
 *       看到的仍是同一个 Map 对象，不会遇到 null 或类型错误；</li>
 *   <li>{@code promotePendingBlockEntity} 在 Map 为空时本就走
 *       "无待处理数据"的正常分支，返回 null，调用方已有处理逻辑；</li>
 *   <li>TAIL 注入，此时所有 BlockEntity 都已注册完毕，不存在
 *       "清空了还没被读取的 NBT"的竞态；</li>
 *   <li>任何异常 → 捕获、记录、保留原 Map 内容（最坏情况只是没省到内存）。</li>
 * </ul>
 *
 * <p><b>诚实的风险提示：</b>这是本系统中<b>风险最高</b>的一项。
 * 若某个模组通过 {@code getBlockEntityNbts()} 拿引用并期望在
 * 区块完全加载后仍能读到原始 NBT（非标准用法），会读到空 Map。
 * 因此该项在 Config 中<b>默认关闭</b>，需用户显式开启。
 * 已核对的真实成员（来自 1.21.1 NeoForm raw.jar 常量池）：
 * {@code pendingBlockEntities : Map}、{@code blockEntities : Map}、
 * {@code getBlockEntityNbts}、{@code registerAllBlockEntitiesAfterLevelLoad}、
 * {@code promotePendingBlockEntity}。</p>
 */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk {

    /**
     * 待处理方块实体 NBT 表。用原始类型 Map 声明以匹配擦除后描述符。
     */
    @Shadow
    @SuppressWarnings("rawtypes")
    private Map pendingBlockEntities;

    /**
     * 在所有待处理方块实体注册完成后清空 NBT 暂存表，使其可被 GC 回收。
     */
    @Inject(method = "registerAllBlockEntitiesAfterLevelLoad", at = @At("TAIL"), require = 0)
    @SuppressWarnings("rawtypes")
    private void ribbon$trimPendingChunkNbt(CallbackInfo ci) {
        try {
            if (!MemoryOptimizer.isChunkNbtTrimEnabled()) return;

            Map map = this.pendingBlockEntities;
            if (map == null || map.isEmpty()) return;

            int freed = map.size();
            // 只清空内容，不替换引用 —— 保证已持有该 Map 的代码不会拿到 null
            map.clear();

            MemoryOptimizer.recordChunkNbtTrimmed(freed);
        } catch (Throwable t) {
            // 兜底：清空失败不影响区块可用性，仅记录
            MemoryOptimizer.reportFallback("LevelChunk.pendingBlockEntities", t);
        }
    }
}
