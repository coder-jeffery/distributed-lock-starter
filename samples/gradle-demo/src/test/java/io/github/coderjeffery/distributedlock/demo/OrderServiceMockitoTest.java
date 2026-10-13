package io.github.coderjeffery.distributedlock.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.coderjeffery.distributedlock.DistributedLock;
import io.github.coderjeffery.distributedlock.DistributedLockClient;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceMockitoTest {

    @Mock
    private DistributedLockClient lockClient;

    @Mock
    private DistributedLock lock;

    @InjectMocks
    private OrderService orderService;

    @Test
    void tryPayAcquiresAndReleasesLock() throws Exception {
        when(lockClient.getLock("order:1001")).thenReturn(lock);
        when(lock.tryLock(0, 5, TimeUnit.SECONDS)).thenReturn(true);

        assertThat(orderService.tryPay("1001")).isTrue();
        assertThat(orderService.payCount()).isEqualTo(1);
        verify(lock).unlock();
    }

    @Test
    void tryPayReturnsFalseWhenBusy() throws Exception {
        when(lockClient.getLock("order:1001")).thenReturn(lock);
        when(lock.tryLock(eq(0L), eq(5L), eq(TimeUnit.SECONDS))).thenReturn(false);

        assertThat(orderService.tryPay("1001")).isFalse();
        assertThat(orderService.payCount()).isZero();
        verify(lock, never()).unlock();
    }
}
