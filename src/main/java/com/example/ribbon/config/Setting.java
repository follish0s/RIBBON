package com.example.ribbon.config;

import java.util.List;

/**
 * 平台无关的单个配置项。
 *
 * <p>Ribbon 在 1.21.1 及之前的 26.2 初版直接使用 NeoForge 的 {@code ModConfigSpec.BooleanValue}
 * 等类型，导致 Fabric 侧加载 Config 类即抛 {@code NoClassDefFoundError}。本类把它抽象出来：
 * 只保存「值 + 元数据」，由各平台后端负责持久化。</p>
 *
 * <p>{@link #get()} 永不抛异常（未加载时返回默认值），因此 Config 里既有的
 * {@code try { ... } catch (IllegalStateException)} 写法保持可用。</p>
 */
public final class Setting<T> {

    public enum Type { BOOLEAN, INT, DOUBLE, STRING, ENUM }

    private final String group;
    private final String name;
    private final String path;
    private final Type type;
    private final T defaultValue;
    private final Object min;
    private final Object max;
    private final List<String> comments;

    private volatile T value;

    Setting(String group, String name, Type type, T defaultValue,
            Object min, Object max, List<String> comments) {
        this.group = group;
        this.name = name;
        this.path = group.isEmpty() ? name : group + "." + name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.comments = List.copyOf(comments);
    }

    public T get() {
        T v = this.value;
        return v != null ? v : this.defaultValue;
    }

    public void set(T v) {
        this.value = v == null ? this.defaultValue : v;
    }

    /** 后端灌值（已做过类型校验/转换）。 */
    @SuppressWarnings("unchecked")
    public void applyRaw(Object raw) {
        if (raw == null) {
            return;
        }
        try {
            switch (this.type) {
                case BOOLEAN -> this.set((T) Boolean.valueOf(String.valueOf(raw)));
                case INT -> this.set((T) Integer.valueOf(((Number) raw).intValue()));
                case DOUBLE -> this.set((T) Double.valueOf(((Number) raw).doubleValue()));
                case STRING -> this.set((T) String.valueOf(raw));
                case ENUM -> {
                    Class<?> ec = this.defaultValue.getClass();
                    Object parsed = raw;
                    if (raw instanceof String s) {
                        for (Object constant : ec.getEnumConstants()) {
                            if (((Enum<?>) constant).name().equals(s)) {
                                parsed = constant;
                                break;
                            }
                        }
                    }
                    this.set((T) parsed);
                }
            }
        } catch (Throwable ignored) {
            // 值非法时保持上一个值，绝不让配置读取影响启动
        }
    }

    public String group() { return this.group; }
    public String name() { return this.name; }
    public String path() { return this.path; }
    public Type type() { return this.type; }
    public T defaultValue() { return this.defaultValue; }
    public Object min() { return this.min; }
    public Object max() { return this.max; }
    public List<String> comments() { return this.comments; }
}
