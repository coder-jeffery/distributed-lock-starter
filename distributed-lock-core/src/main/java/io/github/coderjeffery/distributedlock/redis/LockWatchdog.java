package io.github.coderjeffery.distributedlock.redis;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically renews held locks so long critical sections survive the original TTL.
 */
public final class LockWatchdog implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LockWatchdog.class);
    private static final long MIN_INTERVAL_MILLIS = 100L;

    private final ScheduledExecutorService executor;
    private final ConcurrentMap<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    /**
     * Creates a daemon single-thread watchdog.
     */
    public LockWatchdog() {
        this.executor = Executors.newSingleThreadScheduledExecutor(new WatchdogThreadFactory());
    }

    /**
     * Starts renewal for {@code watchKey} if not already watched.
     *
     * @param watchKey identity of this holder (lock key + owner field)
     * @param lease    full lease duration; renewal runs at lease/3
     * @param renew    renew callback; should be idempotent
     */
    public void watch(String watchKey, Duration lease, Runnable renew) {
        long interval = Math.max(lease.toMillis() / 3, MIN_INTERVAL_MILLIS);
        tasks.compute(watchKey, (key, existing) -> {
            if (existing != null && !existing.isDone()) {
                return existing;
            }
            return executor.scheduleAtFixedRate(() -> {
                try {
                    renew.run();
                } catch (RuntimeException ex) {
                    log.warn("Failed to renew distributed lock [{}]", key, ex);
                }
            }, interval, interval, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * Stops renewal for {@code watchKey}.
     *
     * @param watchKey identity previously passed to {@link #watch}
     */
    public void unwatch(String watchKey) {
        ScheduledFuture<?> future = tasks.remove(watchKey);
        if (future != null) {
            future.cancel(false);
        }
    }

    @Override
    public void close() {
        tasks.values().forEach(future -> future.cancel(false));
        tasks.clear();
        executor.shutdownNow();
    }

    private static final class WatchdogThreadFactory implements ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "distributed-lock-watchdog-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
