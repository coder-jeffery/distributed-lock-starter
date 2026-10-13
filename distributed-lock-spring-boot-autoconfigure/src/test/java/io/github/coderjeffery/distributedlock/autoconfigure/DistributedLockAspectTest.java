package io.github.coderjeffery.distributedlock.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.coderjeffery.distributedlock.DistributedLock;
import io.github.coderjeffery.distributedlock.DistributedLockClient;
import io.github.coderjeffery.distributedlock.LockTimeoutException;
import io.github.coderjeffery.distributedlock.annotation.DistributedLocked;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributedLockAspectTest {

    @Mock
    private DistributedLockClient client;

    @Mock
    private DistributedLock lock;

    @Mock
    private ProceedingJoinPoint joinPoint;

    private DistributedLockAspect aspect;

    @BeforeEach
    void setUp() {
        DistributedLockProperties properties = new DistributedLockProperties();
        aspect = new DistributedLockAspect(client, properties);
    }

    @Test
    void evaluatesSpelLocksAndUnlocks() throws Throwable {
        Method method = Sample.class.getMethod("pay", String.class);
        stubJoinPoint(method, new Object[] {"7"});
        when(client.getLock("order:7")).thenReturn(lock);
        when(lock.tryLock(3_000L, 30_000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.around(joinPoint, method.getAnnotation(DistributedLocked.class));

        assertEquals("ok", result);
        verify(lock).unlock();
    }

    @Test
    void unlocksWhenTargetThrows() throws Throwable {
        Method method = Sample.class.getMethod("pay", String.class);
        stubJoinPoint(method, new Object[] {"7"});
        when(client.getLock("order:7")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class,
                () -> aspect.around(joinPoint, method.getAnnotation(DistributedLocked.class)));
        verify(lock).unlock();
    }

    @Test
    void doesNotInvokeTargetWhenLockTimesOut() throws Throwable {
        Method method = Sample.class.getMethod("pay", String.class);
        stubJoinPoint(method, new Object[] {"7"});
        when(client.getLock("order:7")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        assertThrows(LockTimeoutException.class,
                () -> aspect.around(joinPoint, method.getAnnotation(DistributedLocked.class)));
        verify(joinPoint, never()).proceed();
        verify(lock, never()).unlock();
    }

    @Test
    void defaultKeyUsesClassAndMethodName() throws Throwable {
        Method method = Sample.class.getMethod("noKey");
        stubJoinPoint(method, new Object[0]);
        when(client.getLock(Sample.class.getName() + ".noKey")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(joinPoint.proceed()).thenReturn(null);

        aspect.around(joinPoint, method.getAnnotation(DistributedLocked.class));
        verify(lock).unlock();
    }

    @Test
    void usesAnnotationWaitAndLease() throws Throwable {
        Method method = Sample.class.getMethod("custom", String.class);
        stubJoinPoint(method, new Object[] {"1"});
        when(client.getLock("order:1")).thenReturn(lock);
        when(lock.tryLock(2_000L, 10_000L, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.around(joinPoint, method.getAnnotation(DistributedLocked.class));
        verify(lock).tryLock(2_000L, 10_000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void interruptedAcquireBecomesLockException() throws Throwable {
        Method method = Sample.class.getMethod("pay", String.class);
        stubJoinPoint(method, new Object[] {"7"});
        when(client.getLock("order:7")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("stop"));

        assertThrows(io.github.coderjeffery.distributedlock.LockException.class,
                () -> aspect.around(joinPoint, method.getAnnotation(DistributedLocked.class)));
        assertTrue(Thread.interrupted());
        verify(joinPoint, never()).proceed();
    }

    private void stubJoinPoint(Method method, Object[] args) {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.getTarget()).thenReturn(new Sample());
    }

    static class Sample {
        @DistributedLocked(key = "'order:' + #orderId")
        public String pay(String orderId) {
            return orderId;
        }

        @DistributedLocked
        public void noKey() {
        }

        @DistributedLocked(key = "'order:' + #id", waitTime = 2, leaseTime = 10, timeUnit = TimeUnit.SECONDS)
        public String custom(String id) {
            return id;
        }
    }
}
