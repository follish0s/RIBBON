package com.example.ribbon;

public enum ShaderpackFormat {
    IRIS_OPTIFINE("Iris/OptiFine", "shader.format.IRIS_OPTIFINE"),
    CANVAS("Canvas", "shader.format.CANVAS"),
    VULKAN("Vulkan", "shader.format.VULKAN"),
    VERSION_MISMATCH("Version mismatch", "shader.format.VERSION_MISMATCH"),
    UNKNOWN("Unknown", "shader.format.UNKNOWN");

    private final String displayName;
    private final String langKey;

    ShaderpackFormat(String displayName, String langKey) {
        this.displayName = displayName;
        this.langKey = langKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLangKey() {
        return langKey;
    }

    public String getLocalizedName() {
        return LangUtil.tr(langKey);
    }

    /**
     * 是否由 Ribbon 主动禁用。
     * VERSION_MISMATCH 是"被 Iris 禁用"的标记，不算主动禁用。
     */
    public boolean isIncompatible() {
        return this == CANVAS || this == VULKAN;
    }
}