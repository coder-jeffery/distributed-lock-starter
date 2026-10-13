package io.github.coderjeffery.distributedlock.autoconfigure;

import io.github.coderjeffery.distributedlock.DistributedLockClient;
import io.github.coderjeffery.distributedlock.redis.RedisDistributedLockClient;
import io.github.coderjeffery.distributedlock.redis.RedisLockCommands;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Wires a Redis {@link DistributedLockClient} and the {@code @DistributedLocked} aspect.
 *
 * <p>{@code afterName} is used instead of {@code after = SomeClass.class} so this
 * configuration still loads on Spring Boot 4, where Redis auto-config moved to
 * {@code DataRedisAutoConfiguration}.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"
})
@ConditionalOnClass({StringRedisTemplate.class, RedisConnectionFactory.class})
@ConditionalOnBean(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "distributed-lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DistributedLockProperties.class)
public class DistributedLockAutoConfiguration {

    /**
     * @param connectionFactory Redis connection
     * @return string template used by Lua scripts
     */
    @Bean
    @ConditionalOnMissingBean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * @param template Redis string template
     * @return EVAL adapter
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisLockCommands redisLockCommands(StringRedisTemplate template) {
        return new StringRedisTemplateLockCommands(template);
    }

    /**
     * @param commands   Redis adapter
     * @param properties lock defaults
     * @return lock factory, closed with the Spring context
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public DistributedLockClient distributedLockClient(
            RedisLockCommands commands, DistributedLockProperties properties) {
        return new RedisDistributedLockClient(
                commands,
                properties.getPrefix(),
                properties.getDefaultWaitTime(),
                properties.getDefaultLeaseTime(),
                properties.isWatchdogEnabled());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class AspectConfiguration {

        /**
         * @param client     lock factory
         * @param properties advisor order
         * @return method interceptor
         */
        @Bean
        @ConditionalOnMissingBean
        DistributedLockAspect distributedLockAspect(
                DistributedLockClient client, DistributedLockProperties properties) {
            return new DistributedLockAspect(client, properties);
        }
    }
}
