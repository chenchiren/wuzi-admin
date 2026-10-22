package com.relex.wuzi.service.cache;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 轻量进程内缓存（字典/权限树/税率等读多写少数据）。
 * 多实例部署时可将实现替换为 Redis；本类保证无 Redis 也能启动。
 */
@Service
public class CacheService {

    private static final class Entry {
        final Object value;
        final long expireAt;

        Entry(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Duration ttl, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        Entry e = store.get(key);
        if (e != null && e.expireAt > now) {
            return (T) e.value;
        }
        T val = loader.get();
        if (val != null) {
            store.put(key, new Entry(val, now + Math.max(1, ttl.toMillis())));
        }
        return val;
    }

    public void evict(String key) {
        store.remove(key);
    }

    public void evictPrefix(String prefix) {
        store.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void clear() {
        store.clear();
    }
}
