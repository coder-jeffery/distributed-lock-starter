package io.github.coderjeffery.distributedlock.autoconfigure;

import io.github.coderjeffery.distributedlock.redis.RedisLockCommands;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * {@link RedisLockCommands} backed by {@link StringRedisTemplate}.
 */
public final class StringRedisTemplateLockCommands implements RedisLockCommands {

    private final StringRedisTemplate template;
    private final Map<String, DefaultRedisScript<Long>> scripts = new ConcurrentHashMap<>();

    /**
     * @param template Spring Redis template
     */
    public StringRedisTemplateLockCommands(StringRedisTemplate template) {
        this.template = template;
    }

    @Override
    public Long eval(String script, List<String> keys, String... args) {
        DefaultRedisScript<Long> redisScript = scripts.computeIfAbsent(script, text -> {
            DefaultRedisScript<Long> compiled = new DefaultRedisScript<>();
            compiled.setScriptText(text);
            compiled.setResultType(Long.class);
            return compiled;
        });
        Long result = template.execute(redisScript, keys, (Object[]) args);
        return result == null ? 0L : result;
    }
}
