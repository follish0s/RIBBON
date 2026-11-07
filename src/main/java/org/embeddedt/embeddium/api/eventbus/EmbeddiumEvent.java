package org.embeddedt.embeddium.api.eventbus;

/**
 * The base class which all Embeddium-posted events are derived from.
 *
 * <p>26.2 双平台版：不再继承 {@code net.neoforged.bus.api.Event}，取消状态自持。
 * 继承平台事件类会让 Fabric 侧加载本类即 {@code NoClassDefFoundError}。</p>
 */
public abstract class EmbeddiumEvent {

    private boolean canceled;

    /** Subclasses must override and return true if they want the event to be canceled. */
    public boolean isCancelable() {
        return false;
    }

    public boolean isCanceled() {
        return this.canceled;
    }

    public void setCanceled(boolean cancel) {
        this.canceled = cancel;
    }
}
