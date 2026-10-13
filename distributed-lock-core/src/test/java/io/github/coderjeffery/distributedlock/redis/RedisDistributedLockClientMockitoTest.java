package io.github.coderjeffery.distributedlock.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.coderjeffery.distributedlock.DistributedLock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockClientMockitoTest {

    @Mock
    private RedisLockCommands commands;

    private RedisDistributedLockClient client;

    @BeforeEach
    void setUp() {
        client = new RedisDistributedLockClient(
                commands, "dlock", Duration.ofSeconds(1), Duration.ofSeconds(10), false);
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Test
    void getLockUsesPrefixedKey() {
        when(commands.eval(eq(RedisScripts.TRY_LOCK), eq(List.of("dlock:order:1")), anyString(), anyString()))
                .thenReturn(1L);

        DistributedLock lock = client.getLock("order:1");
        assertEquals("order:1", lock.name());
        assertTrue(lock.tryLock());
        verify(commands).eval(eq(RedisScripts.TRY_LOCK), eq(List.of("dlock:order:1")), anyString(), anyString());
    }

    @Test
    void blankNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> client.getLock(" "));
    }
}
