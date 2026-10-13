package io.github.coderjeffery.distributedlock;

import java.util.concurrent.TimeUnit;

/**
 * A reentrant distributed mutex identified by {@link #name()}.
 *
 * <p>Unlock must be called by the same thread that acquired the lock. The Redis
 * implementation stores an owner token, so a process cannot delete another
 * holder's key.
 */
public interface DistributedLock {

    /**
     * Returns the logical lock name without the configured Redis key prefix.
     *
     * @return lock name
     */
    String name();

    /**
     * Tries to acquire the lock immediately using the default lease time.
     *
     * @return {@code true} if this thread now holds the lock
     */
    boolean tryLock();

    /**
     * Tries to acquire the lock, waiting up to {@code waitTime} and holding it
     * for {@code leaseTime}.
     *
     * @param waitTime  maximum time to wait for the lock; {@code 0} means try once
     * @param leaseTime how long to hold the lock before it expires
     * @param unit      time unit for both values
     * @return {@code true} if this thread now holds the lock
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * Acquires the lock, waiting as long as necessary, using the default lease time.
     *
     * @throws LockException if interrupted while waiting
     */
    void lock();

    /**
     * Releases one hold of the lock.
     *
     * @throws UnlockException if the current thread does not hold the lock
     */
    void unlock();

    /**
     * @return {@code true} if the current thread holds this lock
     */
    boolean isHeldByCurrentThread();

    /**
     * @return {@code true} if any owner currently holds this lock
     */
    boolean isLocked();
}
