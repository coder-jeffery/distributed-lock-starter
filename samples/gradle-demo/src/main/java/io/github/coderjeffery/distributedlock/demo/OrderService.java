package io.github.coderjeffery.distributedlock.demo;

import io.github.coderjeffery.distributedlock.DistributedLock;
import io.github.coderjeffery.distributedlock.DistributedLockClient;
import io.github.coderjeffery.distributedlock.annotation.DistributedLocked;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final DistributedLockClient lockClient;
    private final AtomicInteger payCount = new AtomicInteger();

    public OrderService(DistributedLockClient lockClient) {
        this.lockClient = lockClient;
    }

    @DistributedLocked(key = "'order:' + #orderId", waitTime = 200, leaseTime = 5_000, timeUnit = TimeUnit.MILLISECONDS)
    public int pay(String orderId) {
        return payCount.incrementAndGet();
    }

    @DistributedLocked(key = "'order:' + #orderId", waitTime = 150, leaseTime = 5_000, timeUnit = TimeUnit.MILLISECONDS)
    public void paySlow(String orderId) throws InterruptedException {
        payCount.incrementAndGet();
        Thread.sleep(400);
    }

    public boolean tryPay(String orderId) throws InterruptedException {
        DistributedLock lock = lockClient.getLock("order:" + orderId);
        if (!lock.tryLock(0, 5, TimeUnit.SECONDS)) {
            return false;
        }
        try {
            payCount.incrementAndGet();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public int payCount() {
        return payCount.get();
    }
}
