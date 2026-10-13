package io.github.coderjeffery.distributedlock.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.coderjeffery.distributedlock.DistributedLockClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class DistributedLockAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DistributedLockAutoConfiguration.class));

    @Test
    void backsOffWithoutRedisConnectionFactory() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DistributedLockClient.class));
    }

    @Test
    void createsClientWhenRedisConnectionFactoryPresent() {
        runner.withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLockClient.class);
                    assertThat(context).hasSingleBean(DistributedLockAspect.class);
                });
    }

    @Test
    void canBeDisabled() {
        runner.withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withPropertyValues("distributed-lock.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DistributedLockClient.class));
    }
}
