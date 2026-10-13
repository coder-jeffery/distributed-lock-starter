package io.github.coderjeffery.distributedlock.redis;

/**
 * Lua scripts for a Redisson-style reentrant hash lock.
 *
 * <p>Key layout: {@code HASH lockKey -> { ownerField: holdCount }} with a PX TTL.
 * {@code ownerField} is {@code clientId:threadId} so another process cannot unlock
 * this holder's key.
 */
public final class RedisScripts {

    /**
     * KEYS[1] lock key; ARGV[1] lease millis; ARGV[2] owner field.
     * Returns 1 on acquire / reentrant acquire, otherwise 0.
     */
    public static final String TRY_LOCK = """
            if (redis.call('exists', KEYS[1]) == 0) then
              redis.call('hset', KEYS[1], ARGV[2], 1)
              redis.call('pexpire', KEYS[1], ARGV[1])
              return 1
            end
            if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
              redis.call('hincrby', KEYS[1], ARGV[2], 1)
              redis.call('pexpire', KEYS[1], ARGV[1])
              return 1
            end
            return 0
            """;

    /**
     * KEYS[1] lock key; ARGV[1] lease millis; ARGV[2] owner field.
     * Returns 0 if not owner, 1 if still held after decrement, 2 if fully released.
     */
    public static final String UNLOCK = """
            if (redis.call('hexists', KEYS[1], ARGV[2]) == 0) then
              return 0
            end
            local counter = redis.call('hincrby', KEYS[1], ARGV[2], -1)
            if (counter > 0) then
              redis.call('pexpire', KEYS[1], ARGV[1])
              return 1
            end
            redis.call('hdel', KEYS[1], ARGV[2])
            if (redis.call('hlen', KEYS[1]) > 0) then
              redis.call('pexpire', KEYS[1], ARGV[1])
              return 2
            end
            redis.call('del', KEYS[1])
            return 2
            """;

    /**
     * KEYS[1] lock key; ARGV[1] lease millis; ARGV[2] owner field.
     * Returns 1 if renewed, otherwise 0.
     */
    public static final String RENEW = """
            if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
              redis.call('pexpire', KEYS[1], ARGV[1])
              return 1
            end
            return 0
            """;

    /**
     * KEYS[1] lock key; ARGV[1] owner field.
     * Returns the hold count, or 0.
     */
    public static final String HOLDS = """
            if (redis.call('hexists', KEYS[1], ARGV[1]) == 1) then
              return tonumber(redis.call('hget', KEYS[1], ARGV[1]))
            end
            return 0
            """;

    /**
     * KEYS[1] lock key. Returns 1 if the lock exists.
     */
    public static final String EXISTS = """
            return redis.call('exists', KEYS[1])
            """;

    private RedisScripts() {
    }
}
