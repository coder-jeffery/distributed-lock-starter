package io.github.coderjeffery.distributedlock.demo;

import io.github.coderjeffery.distributedlock.redis.RedisLockCommands;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class InMemoryLockTestConfig {

    @Bean
    @Primary
    RedisLockCommands redisLockCommands() {
        return new InMemoryRedisLockCommands();
    }
}
