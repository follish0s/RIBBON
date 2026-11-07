package org.embeddedt.embeddium.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 26.2 附加 mod 版：不再注册独立的 Embeddium mod，仅保留 options/GUI 层
 * 需要的常量与日志入口。渲染器由官方 Sodium 提供。
 */
public final class Embeddium {
    public static final String MODID = "ribbon";
    public static final String MODNAME = "Ribbon";

    private static final Logger LOGGER = LoggerFactory.getLogger(MODNAME);

    private Embeddium() {
    }

    public static Logger logger() {
        return LOGGER;
    }
}
