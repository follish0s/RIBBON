
package com.example.ribbon.compat;

/** 26.2 移除了 {@code ChunkPosCompat.asLong(int, int)}，保留等价位打包语义。 */
public final class ChunkPosCompat {
    private ChunkPosCompat() {
    }

    public static long asLong(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }
}
