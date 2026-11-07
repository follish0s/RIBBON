package org.embeddedt.embeddium.api.eventbus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds a list of event handlers and handles event dispatching.
 *
 * <p>26.2 双平台版：移除向平台事件总线的二次分发（{@code NeoForge.EVENT_BUS}），
 * 否则 Fabric 侧加载本类即 {@code NoClassDefFoundError}。</p>
 */
public class EventHandlerRegistrar<T extends EmbeddiumEvent> {
    private final List<Handler<T>> handlerList = new CopyOnWriteArrayList<>();

    public EventHandlerRegistrar() {}

    public void addListener(Handler<T> listener) {
        handlerList.add(listener);
    }

    /**
     * Post the given event to all registered listeners.
     * @param event The event to post
     * @return true if the event is cancelable and was canceled, false otherwise
     */
    public boolean post(T event) {
        boolean canceled = false;

        // Skip doing work if the handler list is empty
        if(!handlerList.isEmpty()) {
            boolean isCancelable = event.isCancelable();
            for(Handler<T> handler : handlerList) {
                handler.acceptEvent(event);
                if(isCancelable && event.isCanceled()) {
                    canceled = true;
                }
            }
        }

        return canceled;
    }

    @FunctionalInterface
    public interface Handler<T extends EmbeddiumEvent> {
        void acceptEvent(T event);
    }
}
