package io.github.coderjeffery.distributedlock.autoconfigure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@code distributed-lock.*}.
 */
@ConfigurationProperties(prefix = "distributed-lock")
public class DistributedLockProperties {

    /**
     * Whether auto-configuration is active.
     */
    private boolean enabled = true;

    /**
     * Redis key prefix. A colon is appended when missing.
     */
    private String prefix = "dlock";

    /**
     * Default wait used by {@code lock()} and {@code @DistributedLocked} when waitTime is omitted.
     */
    private Duration defaultWaitTime = Duration.ofSeconds(3);

    /**
     * Default lock TTL / lease.
     */
    private Duration defaultLeaseTime = Duration.ofSeconds(30);

    /**
     * Renew held locks before TTL expiry.
     */
    private boolean watchdogEnabled = true;

    /**
     * Spring AOP advisor order. Lower runs outside {@code @Transactional} by default.
     */
    private int aopOrder = 0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Duration getDefaultWaitTime() {
        return defaultWaitTime;
    }

    public void setDefaultWaitTime(Duration defaultWaitTime) {
        this.defaultWaitTime = defaultWaitTime;
    }

    public Duration getDefaultLeaseTime() {
        return defaultLeaseTime;
    }

    public void setDefaultLeaseTime(Duration defaultLeaseTime) {
        this.defaultLeaseTime = defaultLeaseTime;
    }

    public boolean isWatchdogEnabled() {
        return watchdogEnabled;
    }

    public void setWatchdogEnabled(boolean watchdogEnabled) {
        this.watchdogEnabled = watchdogEnabled;
    }

    public int getAopOrder() {
        return aopOrder;
    }

    public void setAopOrder(int aopOrder) {
        this.aopOrder = aopOrder;
    }
}
