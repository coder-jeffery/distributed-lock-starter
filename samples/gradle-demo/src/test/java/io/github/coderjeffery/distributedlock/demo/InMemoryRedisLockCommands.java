package io.github.coderjeffery.distributedlock.demo;

import io.github.coderjeffery.distributedlock.redis.RedisLockCommands;
import io.github.coderjeffery.distributedlock.redis.RedisScripts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryRedisLockCommands implements RedisLockCommands {

    private static final class HashLock {
        private final Map<String, Long> fields = new HashMap<>();
        private long expireAtMillis;
    }

    private final ConcurrentHashMap<String, HashLock> store = new ConcurrentHashMap<>();
    private final Object mutex = new Object();

    @Override
    public Long eval(String script, List<String> keys, String... args) {
        Objects.requireNonNull(script, "script");
        String key = keys.get(0);
        synchronized (mutex) {
            if (RedisScripts.TRY_LOCK.equals(script)) {
                return tryLock(key, Long.parseLong(args[0]), args[1]);
            }
            if (RedisScripts.UNLOCK.equals(script)) {
                return unlock(key, Long.parseLong(args[0]), args[1]);
            }
            if (RedisScripts.RENEW.equals(script)) {
                return renew(key, Long.parseLong(args[0]), args[1]);
            }
            if (RedisScripts.HOLDS.equals(script)) {
                return holds(key, args[0]);
            }
            if (RedisScripts.EXISTS.equals(script)) {
                return live(key) == null ? 0L : 1L;
            }
            throw new IllegalArgumentException("Unknown script");
        }
    }

    private Long tryLock(String key, long leaseMillis, String field) {
        HashLock lock = live(key);
        if (lock == null) {
            lock = new HashLock();
            lock.fields.put(field, 1L);
            lock.expireAtMillis = now() + leaseMillis;
            store.put(key, lock);
            return 1L;
        }
        if (lock.fields.containsKey(field)) {
            lock.fields.merge(field, 1L, Long::sum);
            lock.expireAtMillis = now() + leaseMillis;
            return 1L;
        }
        return 0L;
    }

    private Long unlock(String key, long leaseMillis, String field) {
        HashLock lock = live(key);
        if (lock == null || !lock.fields.containsKey(field)) {
            return 0L;
        }
        long counter = lock.fields.merge(field, -1L, Long::sum);
        if (counter > 0L) {
            lock.expireAtMillis = now() + leaseMillis;
            return 1L;
        }
        lock.fields.remove(field);
        if (!lock.fields.isEmpty()) {
            lock.expireAtMillis = now() + leaseMillis;
            return 2L;
        }
        store.remove(key);
        return 2L;
    }

    private Long renew(String key, long leaseMillis, String field) {
        HashLock lock = live(key);
        if (lock != null && lock.fields.containsKey(field)) {
            lock.expireAtMillis = now() + leaseMillis;
            return 1L;
        }
        return 0L;
    }

    private Long holds(String key, String field) {
        HashLock lock = live(key);
        return lock == null ? 0L : lock.fields.getOrDefault(field, 0L);
    }

    private HashLock live(String key) {
        HashLock lock = store.get(key);
        if (lock == null) {
            return null;
        }
        if (lock.expireAtMillis > 0L && now() >= lock.expireAtMillis) {
            store.remove(key, lock);
            return null;
        }
        return lock;
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
