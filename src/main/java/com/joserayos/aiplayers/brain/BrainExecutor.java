package com.joserayos.aiplayers.brain;

import com.joserayos.aiplayers.config.AiPlayersConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** Runs model calls on virtual threads, capped by a global semaphore. Never touches the world. */
public final class BrainExecutor {
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static volatile Semaphore permits;

    private BrainExecutor() {}

    private static Semaphore permits() {
        Semaphore s = permits;
        if (s == null) {
            synchronized (BrainExecutor.class) {
                if (permits == null) permits = new Semaphore(AiPlayersConfig.get(AiPlayersConfig.MAX_CONCURRENT_REQUESTS));
                s = permits;
            }
        }
        return s;
    }

    /** Waits for a permit (on a virtual thread), runs the call, and releases the permit when it finishes. */
    public static <T> CompletableFuture<T> submit(Supplier<CompletableFuture<T>> call) {
        CompletableFuture<T> result = new CompletableFuture<>();
        EXECUTOR.execute(() -> {
            Semaphore s = permits();
            try {
                s.acquire();
            } catch (InterruptedException e) {
                result.completeExceptionally(e);
                return;
            }
            try {
                call.get().whenComplete((v, err) -> {
                    s.release();
                    if (err != null) result.completeExceptionally(err); else result.complete(v);
                });
            } catch (Throwable t) {
                s.release();
                result.completeExceptionally(t);
            }
        });
        return result;
    }
}
