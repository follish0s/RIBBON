/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin;

import com.example.ribbon.util.Vec3Pool;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 实体剔除包围盒 {@code getBoundingBoxForCulling} 的引用脏检查缓存。
 *
 * <h2>性能收益分析（为什么这样写能提升性能）</h2>
 *
 * <p>该方法在 {@code EntityRenderer.shouldRender} 中<b>每帧对每个可见实体</b>调用
 * （含拴绳检查的第二次调用）。其基类实现只是 {@code return this.getBoundingBox()}
 * （零分配），但覆写者会<b>每帧分配新 AABB</b>：</p>
 * <ul>
 *   <li>{@link Illusioner#getBoundingBoxForCulling()}：
 *       {@code this.getBoundingBox().inflate(3.0, 0.0, 3.0)}—— raided 服务器上
 *       每个幻术师每帧一次分配 + 一次老年代晋升压力；</li>
 *   <li>{@code AbstractMinecart}（刻意<b>不</b>纳入目标，见下）。</li>
 * </ul>
 *
 * <h2>为什么必须多目标（@Mixin 目标不能只写 Entity）</h2>
 *
 * Java 的虚方法分派意味着：只注入 {@link Entity} 的方法体时，{@code Illusioner}
 * 实例的调用<b>永远不会进入</b> {@code Entity} 里被注入的代码（它分派到子类覆写）。
 * 所以本 Mixin 同时指向基类声明与真正的分配点覆写，两处共享同一套缓存逻辑。
 * Mixin 会把 {@code @Unique} 字段合并进每个目标类（Illusioner 已从 Entity 继承时
 * 会自动跳过，不会重复添加）。
 *
 * <h2>脏检查为什么是零成本的"引用相等"</h2>
 *
 * 原版 {@code Entity.setPos(...)} 每次移动都会通过
 * {@code makeBoundingBox()} 分配<b>新的</b> AABB 实例并整体替换 {@code this.bb}
 * 字段——实例从不被原地修改（AABB 字段是 final 的，想原地改也改不了）。
 * 因此「实体没动」⇔「{@code this.getBoundingBox()} 返回同一个引用」。
 * 缓存键就用一次指针比较（约 1ns），无需任何字段逐一比对、无需脏标记、无需
 * 位置快照。实体一动，引用立刻变化，缓存自动失效——不存在陈旧数据窗口。
 *
 * <h2>安全边界（与禁令的对应关系）</h2>
 * <ul>
 *   <li><b>不触碰 OpenGL/渲染逻辑</b>：只缓存剔除判定输入的不可变值对象，
 *       不改任何渲染流程；</li>
 *   <li><b>不影响 PvP 时序</b>：已核实（反编译全量检索）{@code getBoundingBoxForCulling}
 *       在原版中<b>仅</b>被客户端视锥剔除与拴绳可见性检查调用，与伤害判定、
 *       命中检测、发包顺序完全无关；</li>
 *   <li><b>线程模型</b>：两处调用点都在渲染线程。字段为普通读写，即便某个模组
 *       在别的线程调用，引用读写在 JMM 下是原子的，最坏结果是该实体一帧的剔除盒
 *       略旧——仅影响视锥裁剪的边缘可见性，绝无崩溃可能。</li>
 * </ul>
 *
 * <h2>为什么排除 AbstractMinecart（严谨性取舍）</h2>
 *
 * 矿车的覆写还依赖 {@code getDisplayOffset()}——它可以在 <b>bb 引用不变</b>的
 * 情况下变化（如更换展示物品/重命名）。引用脏检查会漏掉这种变化，返回过期尺寸的
 * 剔除盒。虽然后果只是边缘 pop-in，但不符合"陈旧数据零容忍"的标准，故不纳入。
 */
@Mixin(value = {Entity.class, Illusioner.class})
public abstract class MixinEntityHit {

    /**
     * 上一次缓存剔除盒时的实体包围盒引用（脏检查键）。
     * Mixin 会把它合并进 Entity；Illusioner 已从 Entity 继承同名字段，自动跳过。
     */
    @Unique
    private AABB ribbon$cullingBoxSource;

    /** 缓存的剔除盒本体。AABB 不可变，直接持有原实例即是安全的零拷贝共享。 */
    @Unique
    private AABB ribbon$cullingBoxCache;

    /**
     * HEAD：缓存命中时直接返回缓存的剔除盒，完全跳过方法体——
     * 对 Illusioner 这意味着每帧省掉一次 {@code inflate(3, 0, 3)} 分配，
     * 对基类实现则省掉一次普通方法调用。整体仅一次指针比较。
     */
    @Inject(method = "getBoundingBoxForCulling", at = @At("HEAD"), cancellable = true)
    private void ribbon$serveCachedCullingBox(CallbackInfoReturnable<AABB> cir) {
        AABB cache = this.ribbon$cullingBoxCache;
        if (cache != null
                // 引用脏检查：bb 实例没换 ⇔ 实体没动 ⇔ 缓存依然有效
                && this.ribbon$cullingBoxSource == ((Entity) (Object) this).getBoundingBox()) {
            cir.setReturnValue(cache);
            cir.cancel();
        }
    }

    /**
     * RETURN：缓存失效后重建。把旧缓存盒归还 {@link Vec3Pool}（同线程等值复用），
     * 新结果直接持有（不可变对象，无复制必要）。
     *
     * <p>注意 HEAD 命中时方法体被 cancel，不会走到这里；能到达本注入点
     * 意味着缓存为空或实体已移动，无条件重建即可。</p>
     */
    @Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"))
    private void ribbon$recycleCullingBox(CallbackInfoReturnable<AABB> cir) {
        AABB result = cir.getReturnValue();
        AABB source = ((Entity) (Object) this).getBoundingBox();

        if (result != this.ribbon$cullingBoxCache) {
            if (this.ribbon$cullingBoxCache != null) {
                // 旧缓存盒（如幻术师上一帧的 inflate 结果）归还线程本地回收池，
                // 供其他等值 acquire 复用——这是与 Vec3Pool 的"归还"闭环。
                Vec3Pool.releaseAABB(this.ribbon$cullingBoxCache);
            }
            this.ribbon$cullingBoxCache = result;
            this.ribbon$cullingBoxSource = source;
        }
    }
}
