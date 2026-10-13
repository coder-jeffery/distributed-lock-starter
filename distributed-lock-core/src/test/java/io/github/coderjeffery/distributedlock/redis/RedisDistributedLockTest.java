package io.github.coderjeffery.distributedlock.redis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coderjeffery.distributedlock.DistributedLock;
import io.github.coderjeffery.distributedlock.DistributedLockClient;
import io.github.coderjeffery.distributedlock.LockTimeoutException;
import io.github.coderjeffery.distributedlock.UnlockException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisDistributedLockTest {

    private DistributedLockClient client;

    @BeforeEach
    void setUp() {
        client = new RedisDistributedLockClient(
                new InMemoryRedisLockCommands(),
                "dlock",
                Duration.ofMillis(200),
                Duration.ofSeconds(30),
                true);
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Test
    void tryLockAndUnlock() {
        DistributedLock lock = client.getLock("order");
        assertTrue(lock.tryLock());
        assertTrue(lock.isHeldByCurrentThread());
        assertTrue(lock.isLocked());
        lock.unlock();
        assertFalse(lock.isHeldByCurrentThread());
        assertFalse(lock.isLocked());
    }

    @Test
    void reentrantLockRequiresMatchingUnlocks() {
        DistributedLock lock = client.getLock("reentrant");
        assertTrue(lock.tryLock());
        assertTrue(lock.tryLock());
        lock.unlock();
        assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();
        assertFalse(lock.isHeldByCurrentThread());
    }

    @Test
    void otherThreadCannotAcquireOrUnlock() throws Exception {
        DistributedLock lock = client.getLock("exclusive");
        assertTrue(lock.tryLock());
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean acquired = new AtomicBoolean();
        Thread other = new Thread(() -> {
            started.countDown();
            acquired.set(lock.tryLock());
            assertThrows(UnlockException.class, lock::unlock);
        });
        other.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));
        other.join(1_000);
        assertFalse(acquired.get());
        lock.unlock();
    }

    @Test
    void tryLockWaitsUntilTimeout() throws Exception {
        DistributedLock lock = client.getLock("wait");
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            assertTrue(lock.tryLock());
            held.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        assertTrue(held.await(1, TimeUnit.SECONDS));
        long start = System.nanoTime();
        boolean acquired = lock.tryLock(80, 1_000, TimeUnit.MILLISECONDS);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertFalse(acquired);
        assertTrue(elapsedMs >= 70, "elapsed=" + elapsedMs);
        release.countDown();
        holder.join(1_000);
    }

    @Test
    void lockThrowsWhenDefaultWaitExpires() throws Exception {
        DistributedLockClient shortWait = new RedisDistributedLockClient(
                new InMemoryRedisLockCommands(),
                "dlock",
                Duration.ofMillis(50),
                Duration.ofSeconds(5),
                false);
        try {
            DistributedLock lock = shortWait.getLock("timeout");
            CountDownLatch held = new CountDownLatch(1);
            Thread holder = new Thread(() -> {
                lock.lock();
                held.countDown();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                }
            });
            holder.start();
            assertTrue(held.await(1, TimeUnit.SECONDS));
            assertThrows(LockTimeoutException.class, lock::lock);
            holder.join(1_000);
        } finally {
            shortWait.close();
        }
    }

    @Test
    void watchdogRenewsLease() throws Exception {
        DistributedLock lock = client.getLock("watchdog");
        assertTrue(lock.tryLock(0, 200, TimeUnit.MILLISECONDS));
        Thread.sleep(500);
        assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();
    }

    @Test
    void lockExpiresWithoutWatchdog() throws Exception {
        DistributedLockClient noWatchdog = new RedisDistributedLockClient(
                new InMemoryRedisLockCommands(),
                "dlock",
                Duration.ofMillis(50),
                Duration.ofMillis(150),
                false);
        try {
            DistributedLock lock = noWatchdog.getLock("expire");
            assertTrue(lock.tryLock(0, 150, TimeUnit.MILLISECONDS));
            Thread.sleep(250);
            assertFalse(lock.isHeldByCurrentThread());
            assertFalse(lock.isLocked());
        } finally {
            noWatchdog.close();
        }
    }

    @Test
    void blankNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> client.getLock(" "));
    }

    @Test
    void sameClientReentrantAcrossHandles() {
        DistributedLock first = client.getLock("shared");
        DistributedLock second = client.getLock("shared");
        assertTrue(first.tryLock());
        assertTrue(second.tryLock());
        second.unlock();
        assertTrue(first.isHeldByCurrentThread());
        first.unlock();
        assertFalse(first.isLocked());
    }
}
