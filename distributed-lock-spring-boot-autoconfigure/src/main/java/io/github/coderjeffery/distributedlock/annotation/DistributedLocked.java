package io.github.coderjeffery.distributedlock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Marks a method that must run while holding a distributed lock.
 *
 * <p>{@link #key()} is a SpEL expression. String literals must be quoted:
 * {@code 'order:pay'} or {@code 'order:' + #orderId}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLocked {

    /**
     * SpEL lock key. Empty means {@code ClassName.methodName}.
     *
     * @return SpEL expression
     */
    String key() default "";

    /**
     * Alias for {@link #key()}.
     *
     * @return SpEL expression
     */
    String value() default "";

    /**
     * How long to wait for the lock. {@code -1} uses {@code distributed-lock.default-wait-time}.
     *
     * @return wait duration in {@link #timeUnit()}
     */
    long waitTime() default -1L;

    /**
     * How long to hold the lock. {@code -1} uses {@code distributed-lock.default-lease-time}.
     *
     * @return lease duration in {@link #timeUnit()}
     */
    long leaseTime() default -1L;

    /**
     * Unit for {@link #waitTime()} and {@link #leaseTime()}.
     *
     * @return time unit
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;
}
