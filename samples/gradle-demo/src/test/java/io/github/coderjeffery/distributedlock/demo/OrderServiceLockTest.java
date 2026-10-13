package io.github.coderjeffery.distributedlock.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.coderjeffery.distributedlock.DistributedLock;
import io.github.coderjeffery.distributedlock.DistributedLockClient;
import io.github.coderjeffery.distributedlock.LockTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(InMemoryLockTestConfig.class)
class OrderServiceLockTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private DistributedLockClient lockClient;

    @Test
    void starterWiresClientAndAnnotationLock() throws InterruptedException {
        assertThat(lockClient).isNotNull();
        assertThat(orderService.pay("A-1")).isEqualTo(1);
        assertThat(orderService.tryPay("A-2")).isTrue();
    }

    @Test
    void annotationLockTimesOutWhenAnotherHolderExists() throws InterruptedException {
        DistributedLock lock = lockClient.getLock("order:B-1");
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            try {
                if (lock.tryLock(0, 5, TimeUnit.SECONDS)) {
                    held.countDown();
                    release.await(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });
        holder.start();
        assertThat(held.await(1, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> orderService.pay("B-1")).isInstanceOf(LockTimeoutException.class);
        release.countDown();
        holder.join(1_000);
    }
}
