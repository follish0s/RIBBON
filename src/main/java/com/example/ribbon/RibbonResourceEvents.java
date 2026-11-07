/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.event;

import com.example.ribbon.Config;
import com.example.ribbon.util.TextureThreadPool;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public class RibbonResourceEvents {

    @SubscribeEvent
    public static void onResourceReload(AddReloadListenerEvent event) {
        if (!Config.isFastTextureLoadingEnabled()) return;

        // 在这里可以注册自定义的 ReloadListener 来清理缓存
        // event.addListener(new AsyncTextureReloadListener());

        if (Config.isTextureDebugLogEnabled()) {
            org.slf4j.LoggerFactory.getLogger("Ribbon/ResourceReload")
                    .info(com.example.ribbon.LangUtil.tr("log.texture.reload"));
        }

        // 重载时重新初始化线程池，清理卡死的任务
        TextureThreadPool.init();
    }
}