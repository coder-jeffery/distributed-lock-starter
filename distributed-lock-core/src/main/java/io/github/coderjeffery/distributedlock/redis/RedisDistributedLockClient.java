package io.github.coderjeffery.distributedlock.redis;

import io.github.coderjeffery.distributedlock.DistributedLock;
import io.github.coderjeffery.distributedlock.DistributedLockClient;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Factory for {@link RedisDistributedLock}. One client id is shared so reentrancy
 * works across lock handles obtained for the same name.
 */
public final class RedisDistributedLockClient implements DistributedLockClient {

    private final RedisLockCommands commands;
    private final String keyPrefix;
    private final Duration defaultWaitTime;
    private final Duration defaultLeaseTime;
    private final LockWatchdog watchdog;
    private final boolean watchdogEnabled;
    private final String clientId = UUID.randomUUID().toString();

    /**
     * @param commands          Redis EVAL adapter
     * @param keyPrefix         Redis key prefix, for example {@code dlock:}
     * @param defaultWaitTime   default wait used by {@link DistributedLock#lock()}
     * @param defaultLeaseTime  TTL of a newly acquired lock
     * @param watchdogEnabled   whether to renew held locks
     */
    public RedisDistributedLockClient(
            RedisLockCommands commands,
            String keyPrefix,
            Duration defaultWaitTime,
            Duration defaultLeaseTime,
            boolean watchdogEnabled) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.keyPrefix = normalizePrefix(keyPrefix);
        this.defaultWaitTime = Objects.requireNonNull(defaultWaitTime, "defaultWaitTime");
        this.defaultLeaseTime = Objects.requireNonNull(defaultLeaseTime, "defaultLeaseTime");
        if (defaultWaitTime.isNegative()) {
            throw new IllegalArgumentException("defaultWaitTime must be >= 0");
        }
        if (defaultLeaseTime.isZero() || defaultLeaseTime.isNegative()) {
            throw new IllegalArgumentException("defaultLeaseTime must be positive");
        }
        this.watchdogEnabled = watchdogEnabled;
        this.watchdog = watchdogEnabled ? new LockWatchdog() : null;
    }

    @Override
    public DistributedLock getLock(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("lock name must not be blank");
        }
        return new RedisDistributedLock(
                commands,
                name,
                keyPrefix + name,
                clientId,
                defaultWaitTime,
                defaultLeaseTime,
                watchdog,
                watchdogEnabled);
    }

    @Override
    public void close() {
        if (watchdog != null) {
            watchdog.close();
        }
    }

    private static String normalizePrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return "dlock:";
        }
        return keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
    }
}
