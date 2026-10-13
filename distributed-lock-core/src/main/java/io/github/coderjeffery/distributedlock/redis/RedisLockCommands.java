package io.github.coderjeffery.distributedlock.redis;

import java.util.List;

/**
 * Minimal Redis EVAL adapter. Spring Data Redis and tests provide implementations.
 */
public interface RedisLockCommands {

    /**
     * Executes a Lua script.
     *
     * @param script Lua source
     * @param keys   KEYS table
     * @param args   ARGV table
     * @return numeric Redis result, never {@code null}
     */
    Long eval(String script, List<String> keys, String... args);
}
