package io.github.coderjeffery.distributedlock.redis;

import io.github.coderjeffery.distributedlock.DistributedLock;
import io.github.coderjeffery.distributedlock.LockException;
import io.github.coderjeffery.distributedlock.UnlockException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redis hash lock with owner token, reentrancy and optional watchdog renewal.
 */
public final class RedisDistributedLock implements DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);
    private static final long MIN_SLEEP_NANOS = TimeUnit.MILLISECONDS.toNanos(20);
    private static final long MAX_SLEEP_NANOS = TimeUnit.MILLISECONDS.toNanos(80);

    private final RedisLockCommands commands;
    private final String name;
    private final String redisKey;
    private final String clientId;
    private final Duration defaultWaitTime;
    private final Duration defaultLeaseTime;
    private final LockWatchdog watchdog;
    private final boolean watchdogEnabled;

    RedisDistributedLock(
            RedisLockCommands commands,
            String name,
            String redisKey,
            String clientId,
            Duration defaultWaitTime,
            Duration defaultLeaseTime,
            LockWatchdog watchdog,
            boolean watchdogEnabled) {
        this.commands = commands;
        this.name = name;
        this.redisKey = redisKey;
        this.clientId = clientId;
        this.defaultWaitTime = defaultWaitTime;
        this.defaultLeaseTime = defaultLeaseTime;
        this.watchdog = watchdog;
        this.watchdogEnabled = watchdogEnabled;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean tryLock() {
        try {
            return tryLock(0L, defaultLeaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LockException("Interrupted while acquiring lock [" + name + "]", ex);
        }
    }

    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit");
        if (leaseTime <= 0) {
            throw new IllegalArgumentException("leaseTime must be positive");
        }
        if (waitTime < 0) {
            throw new IllegalArgumentException("waitTime must be >= 0");
        }
        long leaseMillis = Math.max(unit.toMillis(leaseTime), 1L);
        long waitNanos = unit.toNanos(waitTime);
        long deadline = waitNanos == 0L ? 0L : System.nanoTime() + waitNanos;
        while (true) {
            if (Thread.interrupted()) {
                throw new InterruptedException("Interrupted while acquiring lock [" + name + "]");
            }
            if (tryAcquire(leaseMillis)) {
                return true;
            }
            if (waitNanos == 0L || System.nanoTime() >= deadline) {
                return false;
            }
            park(deadline);
        }
    }

    @Override
    public void lock() {
        try {
            if (!tryLock(defaultWaitTime.toMillis(), defaultLeaseTime.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new io.github.coderjeffery.distributedlock.LockTimeoutException(
                        "Timed out acquiring lock [" + name + "] after " + defaultWaitTime);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LockException("Interrupted while acquiring lock [" + name + "]", ex);
        }
    }

    @Override
    public void unlock() {
        Long result = commands.eval(
                RedisScripts.UNLOCK,
                List.of(redisKey),
                String.valueOf(defaultLeaseTime.toMillis()),
                ownerField());
        if (result == null || result == 0L) {
            throw new UnlockException("Current thread does not hold lock [" + name + "]");
        }
        if (result == 2L) {
            stopWatchdog();
        }
        log.debug("Unlocked [{}], result={}", name, result);
    }

    @Override
    public boolean isHeldByCurrentThread() {
        Long holds = commands.eval(RedisScripts.HOLDS, List.of(redisKey), ownerField());
        return holds != null && holds > 0L;
    }

    @Override
    public boolean isLocked() {
        Long exists = commands.eval(RedisScripts.EXISTS, List.of(redisKey));
        return exists != null && exists > 0L;
    }

    private boolean tryAcquire(long leaseMillis) {
        Long result = commands.eval(
                RedisScripts.TRY_LOCK, List.of(redisKey), String.valueOf(leaseMillis), ownerField());
        boolean acquired = result != null && result == 1L;
        if (acquired && watchdogEnabled) {
            startWatchdog(leaseMillis);
        }
        if (acquired) {
            log.debug("Acquired lock [{}] for {} ms", name, leaseMillis);
        }
        return acquired;
    }

    private void startWatchdog(long leaseMillis) {
        String field = ownerField();
        Duration lease = Duration.ofMillis(leaseMillis);
        watchdog.watch(watchKey(field), lease, () -> {
            Long renewed = commands.eval(
                    RedisScripts.RENEW, List.of(redisKey), String.valueOf(leaseMillis), field);
            if (renewed == null || renewed == 0L) {
                log.debug("Watchdog stopped for [{}]; lock no longer held", name);
                watchdog.unwatch(watchKey(field));
            }
        });
    }

    private void stopWatchdog() {
        if (watchdogEnabled) {
            watchdog.unwatch(watchKey(ownerField()));
        }
    }

    private String ownerField() {
        return clientId + ":" + Thread.currentThread().getId();
    }

    private String watchKey(String field) {
        return redisKey + ":" + field;
    }

    private static void park(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) {
            return;
        }
        long jitter = ThreadLocalRandom.current().nextLong(MIN_SLEEP_NANOS, MAX_SLEEP_NANOS + 1);
        LockSupport.parkNanos(Math.min(remaining, jitter));
    }
}
