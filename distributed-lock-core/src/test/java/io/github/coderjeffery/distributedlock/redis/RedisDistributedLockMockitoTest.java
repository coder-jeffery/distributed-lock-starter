package io.github.coderjeffery.distributedlock.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.coderjeffery.distributedlock.LockTimeoutException;
import io.github.coderjeffery.distributedlock.UnlockException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockMockitoTest {

    @Mock
    private RedisLockCommands commands;

    @Mock
    private LockWatchdog watchdog;

    private RedisDistributedLock lock;

    @BeforeEach
    void setUp() {
        lock = new RedisDistributedLock(
                commands,
                "order",
                "dlock:order",
                "client-1",
                Duration.ofMillis(40),
                Duration.ofSeconds(30),
                watchdog,
                true);
    }

    @Test
    void tryLockAcquiresAndStartsWatchdog() {
        when(commands.eval(eq(RedisScripts.TRY_LOCK), eq(List.of("dlock:order")), eq("30000"), anyString()))
                .thenReturn(1L);

        assertTrue(lock.tryLock());
        assertEquals("order", lock.name());
        verify(watchdog).watch(anyString(), eq(Duration.ofSeconds(30)), any());
    }

    @Test
    void tryLockReturnsFalseWithoutWatching() {
        when(commands.eval(eq(RedisScripts.TRY_LOCK), anyList(), anyString(), anyString())).thenReturn(0L);

        assertFalse(lock.tryLock());
        verify(watchdog, never()).watch(anyString(), any(), any());
    }

    @Test
    void tryLockRetriesUntilSuccess() throws InterruptedException {
        when(commands.eval(eq(RedisScripts.TRY_LOCK), anyList(), anyString(), anyString()))
                .thenReturn(0L, 1L);

        assertTrue(lock.tryLock(200, 1, TimeUnit.SECONDS));
        verify(commands, times(2)).eval(eq(RedisScripts.TRY_LOCK), anyList(), anyString(), anyString());
    }

    @Test
    void lockTimesOutWhenRedisRejectsAcquire() {
        when(commands.eval(eq(RedisScripts.TRY_LOCK), anyList(), anyString(), anyString())).thenReturn(0L);

        assertThrows(LockTimeoutException.class, lock::lock);
    }

    @Test
    void unlockThrowsWhenNotOwner() {
        when(commands.eval(eq(RedisScripts.UNLOCK), eq(List.of("dlock:order")), eq("30000"), anyString()))
                .thenReturn(0L);

        assertThrows(UnlockException.class, lock::unlock);
        verify(watchdog, never()).unwatch(anyString());
    }

    @Test
    void unlockStopsWatchdogWhenFullyReleased() {
        when(commands.eval(eq(RedisScripts.UNLOCK), anyList(), anyString(), anyString())).thenReturn(2L);

        lock.unlock();
        verify(watchdog).unwatch(anyString());
    }

    @Test
    void reentrantUnlockKeepsWatchdog() {
        when(commands.eval(eq(RedisScripts.UNLOCK), anyList(), anyString(), anyString())).thenReturn(1L);

        lock.unlock();
        verify(watchdog, never()).unwatch(anyString());
    }

    @Test
    void isHeldByCurrentThreadReadsHoldCount() {
        when(commands.eval(eq(RedisScripts.HOLDS), eq(List.of("dlock:order")), anyString())).thenReturn(2L);

        assertTrue(lock.isHeldByCurrentThread());
    }

    @Test
    void isLockedReadsExists() {
        when(commands.eval(eq(RedisScripts.EXISTS), eq(List.of("dlock:order")))).thenReturn(1L);

        assertTrue(lock.isLocked());
    }

    @Test
    void rejectsNonPositiveLease() {
        assertThrows(IllegalArgumentException.class, () -> lock.tryLock(0, 0, TimeUnit.SECONDS));
    }
}
