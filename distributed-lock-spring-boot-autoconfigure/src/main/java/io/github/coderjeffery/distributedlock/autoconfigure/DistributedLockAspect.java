package io.github.coderjeffery.distributedlock.autoconfigure;

import io.github.coderjeffery.distributedlock.DistributedLock;
import io.github.coderjeffery.distributedlock.DistributedLockClient;
import io.github.coderjeffery.distributedlock.LockException;
import io.github.coderjeffery.distributedlock.LockTimeoutException;
import io.github.coderjeffery.distributedlock.annotation.DistributedLocked;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;

/**
 * Acquires a distributed lock around methods annotated with {@link DistributedLocked}.
 */
@Aspect
public class DistributedLockAspect implements Ordered {

    private final DistributedLockClient client;
    private final DistributedLockProperties properties;
    private final LockKeyEvaluator keyEvaluator = new LockKeyEvaluator();

    /**
     * @param client     lock factory
     * @param properties wait / lease defaults and advisor order
     */
    public DistributedLockAspect(DistributedLockClient client, DistributedLockProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * @param joinPoint  intercepted call
     * @param annotation lock metadata
     * @return method result
     * @throws Throwable if the target throws, or lock acquisition fails
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLocked annotation) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String key = keyEvaluator.resolve(annotation, method, joinPoint.getArgs(), joinPoint.getTarget());
        DistributedLock lock = client.getLock(key);
        long wait = annotation.waitTime() < 0
                ? properties.getDefaultWaitTime().toMillis()
                : annotation.timeUnit().toMillis(annotation.waitTime());
        long lease = annotation.leaseTime() < 0
                ? properties.getDefaultLeaseTime().toMillis()
                : annotation.timeUnit().toMillis(annotation.leaseTime());
        boolean acquired;
        try {
            acquired = lock.tryLock(wait, lease, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LockException("Interrupted while acquiring lock [" + key + "]", ex);
        }
        if (!acquired) {
            throw new LockTimeoutException(
                    "Timed out acquiring lock [" + key + "] for " + method.getName());
        }
        try {
            return joinPoint.proceed();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getOrder() {
        return properties.getAopOrder();
    }
}
