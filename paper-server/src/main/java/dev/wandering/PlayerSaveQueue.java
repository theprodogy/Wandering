package dev.wandering;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;

/**
 * Writes player files (playerdata, stats, advancements) on one IO thread.
 * The data is serialized by the caller first, so only disk IO leaves the calling thread.
 * One thread keeps writes in submission order; readers call {@link #await(Path)} before reading a file.
 */
public final class PlayerSaveQueue {

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        final Thread thread = new Thread(r, "Wandering Player IO");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<Path, CompletableFuture<Void>> PENDING = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface Write {
        void run() throws Exception;
    }

    private PlayerSaveQueue() {
    }

    /** Runs the write on the IO thread, or inline when async saving is off. {@code onError} runs on failure. */
    public static void write(final Path file, final Write write, final java.util.function.Consumer<Exception> onError) {
        if (!WanderingConfig.asyncPlayerSave) {
            run(write, onError);
            return;
        }
        final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> run(write, onError), EXECUTOR);
        PENDING.put(file, future);
        future.whenComplete((ignored, throwable) -> PENDING.remove(file, future));
    }

    /** Blocks until the latest queued write to this file is on disk. */
    public static void await(final Path file) {
        final CompletableFuture<Void> future = PENDING.get(file);
        if (future != null) {
            future.join();
        }
    }

    /** Blocks until every write queued so far is on disk. */
    public static void awaitAll() {
        if (WanderingConfig.asyncPlayerSave) {
            CompletableFuture.runAsync(() -> {}, EXECUTOR).join();
        }
    }

    private static void run(final Write write, final java.util.function.Consumer<Exception> onError) {
        try {
            write.run();
        } catch (final Exception e) {
            onError.accept(e);
        } catch (final Throwable t) {
            LOGGER.error("Unexpected error writing player file", t);
        }
    }
}
