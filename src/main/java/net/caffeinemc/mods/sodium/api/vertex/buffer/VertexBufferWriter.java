package net.caffeinemc.mods.sodium.api.vertex.buffer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

/**
 * Sodium 0.8.x compatibility shim for the {@code VertexBufferWriter} API.
 *
 * <p>This interface mirrors the public Sodium 0.8 {@code net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter}
 * contract so that third-party mods compiled against the Sodium 0.8 API (such as Iceberg and Entity Texture Features)
 * can resolve the type at runtime when Ribbon is installed in place of Sodium. The {@link #push(MemoryStack, long, int, VertexFormat)}
 * method intentionally accepts the vanilla {@link VertexFormat} (matching the Sodium 0.8 signature), rather than
 * Embeddium's {@code VertexFormatDescription}.</p>
 */
public interface VertexBufferWriter {
    /**
     * Pushes {@code count} vertices described by {@code format} from {@code ptr} into this vertex buffer.
     *
     * @param stack  The memory stack which can be used as scratch memory
     * @param ptr    The pointer to read vertices from
     * @param count  The number of vertices to copy
     * @param format The format of the vertices (vanilla {@link VertexFormat})
     */
    void push(MemoryStack stack, long ptr, int count, VertexFormat format);

    /**
     * Whether this writer can use the intrinsics fast path.
     */
    default boolean canUseIntrinsics() {
        return true;
    }

    /**
     * Converts a {@link VertexConsumer} into a {@link VertexBufferWriter}.
     *
     * @param consumer The vertex consumer to create a writer for
     * @return An implementation of {@link VertexBufferWriter} which will write vertices into {@code consumer}
     * @throws IllegalArgumentException If the vertex consumer does not implement the necessary interface
     */
    static VertexBufferWriter of(VertexConsumer consumer) {
        if (consumer instanceof VertexBufferWriter writer && writer.canUseIntrinsics()) {
            return writer;
        }

        throw createUnsupportedVertexConsumerThrowable(consumer);
    }

    /**
     * Converts a {@link VertexConsumer} into a {@link VertexBufferWriter} if possible.
     *
     * @param consumer The vertex consumer to create a writer for
     * @return An implementation of {@link VertexBufferWriter} which will write vertices into {@code consumer}, or null
     * if the vertex consumer does not implement the necessary interface
     */
    @Nullable
    static VertexBufferWriter tryOf(VertexConsumer consumer) {
        if (consumer instanceof VertexBufferWriter writer && writer.canUseIntrinsics()) {
            return writer;
        }

        return null;
    }

    private static RuntimeException createUnsupportedVertexConsumerThrowable(VertexConsumer consumer) {
        var clazz = consumer.getClass();
        var name = clazz.getName();

        return new IllegalArgumentException(("The class %s does not implement interface VertexBufferWriter, " +
                "which is required for compatibility with Sodium").formatted(name));
    }
}
