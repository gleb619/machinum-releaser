package machinum.cache;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.Util.CheckedSupplier;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class CacheService implements AutoCloseable {

    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final Duration defaultExpire;

    public <U> U get(String key, CheckedSupplier<U> dataSupplier) {
        return get(key, dataSupplier, defaultExpire.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Synchronized
    public <U> U get(String key, CheckedSupplier<U> dataSupplier, long expirationTime, TimeUnit timeUnit) {
        if (cache.containsKey(key) && !cache.get(key).isExpired()) {
            log.debug("Return value from cache for: {}", key);
            return (U) cache.get(key).getValue();
        }

        var cacheEntry = cache.computeIfAbsent(key, k -> {
            log.debug("Cache miss for key: {}", key);
            var value = dataSupplier.resolve();
            return new CacheEntry<>(value, System.currentTimeMillis() + timeUnit.toMillis(expirationTime))
                    .checkResult();
        });

        return (U) cacheEntry.getValue();
    }

    public void evict(String key) {
        cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    public void scheduleCleanup(long period, TimeUnit timeUnit) {
        scheduler.scheduleAtFixedRate(this::removeExpiredEntries, period, period, timeUnit);
    }

    private void removeExpiredEntries() {
        var expiredCount = cache.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired())
                .map(Map.Entry::getKey)
                .toList();

        expiredCount.forEach(cache::remove);
        log.debug("Removed {} expired cache entries", expiredCount.size());
    }

    @Override
    public void close() throws Exception {
        clear();
        scheduler.shutdown();
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    private static class CacheEntry<T> {

        T value;

        long expiryTime;

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }

        public CacheEntry<T> checkResult() {
            if (Objects.isNull(value)) {
                setExpiryTime(System.currentTimeMillis() - 1);
            } else if (value instanceof Collection<?> c && c.isEmpty()) {
                setExpiryTime(System.currentTimeMillis() - 1);
            } else if (value instanceof Map<?, ?> m && m.isEmpty()) {
                setExpiryTime(System.currentTimeMillis() - 1);
            } else if (value instanceof byte[] b && b.length == 0) {
                setExpiryTime(System.currentTimeMillis() - 1);
            }

            return this;
        }

    }

}
