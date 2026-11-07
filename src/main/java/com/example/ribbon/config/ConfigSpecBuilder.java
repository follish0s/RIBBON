package com.example.ribbon.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 与 {@code ModConfigSpec.Builder} 调用形态一致的平台无关构建器。
 *
 * <p>Config.java 的 132 个定义语句（{@code BUILDER.comment(...).define(...)}）
 * 可以原样保留，只把字段类型和 Builder 类型换掉。</p>
 */
public final class ConfigSpecBuilder {

    private final Deque<String> sections = new ArrayDeque<>();
    private final List<Setting<?>> settings = new ArrayList<>();
    private List<String> pendingComments = List.of();

    public ConfigSpecBuilder push(String section) {
        this.sections.addLast(section);
        return this;
    }

    public ConfigSpecBuilder pop() {
        if (!this.sections.isEmpty()) {
            this.sections.removeLast();
        }
        return this;
    }

    public Entry comment(String... lines) {
        this.pendingComments = List.of(lines);
        return new Entry();
    }

    private String group() {
        return String.join(".", this.sections);
    }

    private List<String> takeComments() {
        List<String> c = this.pendingComments;
        this.pendingComments = List.of();
        return c;
    }

    public ConfigSpec build() {
        return new ConfigSpec(List.copyOf(this.settings));
    }

    public final class Entry {

        public Setting<Boolean> define(String name, boolean def) {
            Setting<Boolean> s = new Setting<>(ConfigSpecBuilder.this.group(), name,
                    Setting.Type.BOOLEAN, def, null, null, ConfigSpecBuilder.this.takeComments());
            ConfigSpecBuilder.this.settings.add(s);
            return s;
        }

        public Setting<String> define(String name, String def) {
            Setting<String> s = new Setting<>(ConfigSpecBuilder.this.group(), name,
                    Setting.Type.STRING, def, null, null, ConfigSpecBuilder.this.takeComments());
            ConfigSpecBuilder.this.settings.add(s);
            return s;
        }

        public Setting<Integer> defineInRange(String name, int def, int min, int max) {
            Setting<Integer> s = new Setting<>(ConfigSpecBuilder.this.group(), name,
                    Setting.Type.INT, def, min, max, ConfigSpecBuilder.this.takeComments());
            ConfigSpecBuilder.this.settings.add(s);
            return s;
        }

        public Setting<Double> defineInRange(String name, double def, double min, double max) {
            Setting<Double> s = new Setting<>(ConfigSpecBuilder.this.group(), name,
                    Setting.Type.DOUBLE, def, min, max, ConfigSpecBuilder.this.takeComments());
            ConfigSpecBuilder.this.settings.add(s);
            return s;
        }

        public <E extends Enum<E>> Setting<E> defineEnum(String name, E def) {
            Setting<E> s = new Setting<>(ConfigSpecBuilder.this.group(), name,
                    Setting.Type.ENUM, def, null, null, ConfigSpecBuilder.this.takeComments());
            ConfigSpecBuilder.this.settings.add(s);
            return s;
        }
    }
}
