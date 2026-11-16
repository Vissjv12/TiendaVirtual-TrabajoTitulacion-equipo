package com.example.msvc_producto.infrastructure.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * String-only Redis primitive for the local A17 catalog-cache pilot.
 * Cache publication is atomic with the generation check so a stale reader
 * cannot repopulate a value after an invalidation has advanced the generation.
 */
public final class RedisProductoCatalogoStore {

    private static final DefaultRedisScript<String> GET_IF_GENERATION_MATCHES = new DefaultRedisScript<>(
            "local current = redis.call('GET', KEYS[1])\n"
                    + "if not current or current ~= ARGV[1] then return nil end\n"
                    + "return redis.call('GET', KEYS[2])",
            String.class);

    private static final DefaultRedisScript<Long> PUT_IF_GENERATION_MATCHES = new DefaultRedisScript<>(
            "local current = redis.call('GET', KEYS[1])\n"
                    + "if not current or current ~= ARGV[1] then return 0 end\n"
                    + "redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])\n"
                    + "return 1",
            Long.class);

    private final StringRedisTemplate template;

    public RedisProductoCatalogoStore(StringRedisTemplate template) {
        this.template = Objects.requireNonNull(template, "template");
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(template.opsForValue().get(requireKey(key, "key")));
    }

    public String getGeneration(String generationKey) {
        String safeKey = requireKey(generationKey, "generationKey");
        for (int attempt = 0; attempt < 5; attempt++) {
            String generation = template.opsForValue().get(safeKey);
            if (generation != null) {
                return generation;
            }

            template.opsForValue().setIfAbsent(safeKey, UUID.randomUUID().toString());
        }
        throw new IllegalStateException("Redis generation could not be initialized");
    }

    /**
     * Starts a new namespace so cached entries from the prior generation can
     * never become visible again, including after Redis evicts the metadata.
     */
    public String advanceGeneration(String generationKey) {
        String generation = UUID.randomUUID().toString();
        template.opsForValue().set(requireKey(generationKey, "generationKey"), generation);
        return generation;
    }

    public Optional<String> getIfGenerationMatches(
            String generationKey,
            String expectedGeneration,
            String cacheKey) {
        String safeGenerationKey = requireKey(generationKey, "generationKey");
        String safeCacheKey = requireKey(cacheKey, "cacheKey");
        String safeExpectedGeneration = Objects.requireNonNull(expectedGeneration, "expectedGeneration");

        String cached = template.execute(
                GET_IF_GENERATION_MATCHES,
                List.of(safeGenerationKey, safeCacheKey),
                safeExpectedGeneration);
        return Optional.ofNullable(cached);
    }

    public boolean putIfGenerationMatches(
            String generationKey,
            String expectedGeneration,
            String cacheKey,
            String json,
            Duration ttl) {
        String safeGenerationKey = requireKey(generationKey, "generationKey");
        String safeCacheKey = requireKey(cacheKey, "cacheKey");
        if (safeGenerationKey.equals(safeCacheKey)) {
            throw new IllegalArgumentException("generationKey and cacheKey must be different");
        }
        String safeExpectedGeneration = Objects.requireNonNull(expectedGeneration, "expectedGeneration");
        String safeJson = Objects.requireNonNull(json, "json");
        long ttlMillis = Objects.requireNonNull(ttl, "ttl").toMillis();
        if (ttlMillis < 1) {
            throw new IllegalArgumentException("ttl must be at least one millisecond");
        }

        Long stored = template.execute(
                PUT_IF_GENERATION_MATCHES,
                List.of(safeGenerationKey, safeCacheKey),
                safeExpectedGeneration,
                safeJson,
                Long.toString(ttlMillis));
        if (stored == null) {
            throw new IllegalStateException("Redis did not return the cache publication result");
        }
        return stored == 1L;
    }

    private String requireKey(String key, String name) {
        String safeKey = Objects.requireNonNull(key, name);
        if (safeKey.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return safeKey;
    }
}
