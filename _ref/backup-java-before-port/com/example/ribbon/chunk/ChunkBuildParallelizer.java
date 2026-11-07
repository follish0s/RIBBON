package com.example.ribbon.chunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * 区块构建并行化器。
 * 将区块构建任务拆分到多个线程并行执行。
 */
public final class ChunkBuildParallelizer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/ChunkBuild");

    private static ForkJoinPool buildPool;
    private static volatile boolean initialized = false;

    private ChunkBuildParallelizer() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
        buildPool = new ForkJoinPool(parallelism);

        LOGGER.info("[Ribbon] 区块构建线程池初始化完成，并行度: {}", parallelism);
    }

    public static void shutdown() {
        if (buildPool != null) {
            buildPool.shutdownNow();
        }
        initialized = false;
    }

    /**
     * 提交一个区块构建任务到并行池。
     */
    public static void submitBuildTask(Runnable task) {
        if (buildPool == null || buildPool.isShutdown()) {
            task.run(); // 降级为同步执行
            return;
        }
        buildPool.submit(task);
    }

    /**
     * 并行执行一组构建任务。
     */
    public static void executeAll(Runnable... tasks) {
        if (buildPool == null || buildPool.isShutdown()) {
            for (Runnable task : tasks) task.run();
            return;
        }

        ForkJoinTask<?>[] futures = new ForkJoinTask<?>[tasks.length];
        for (int i = 0; i < tasks.length; i++) {
            futures[i] = buildPool.submit(tasks[i]);
        }
        for (ForkJoinTask<?> future : futures) {
            future.join();
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }
}