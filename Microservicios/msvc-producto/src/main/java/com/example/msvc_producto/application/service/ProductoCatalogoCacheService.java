package com.example.msvc_producto.application.service;

import com.example.msvc_producto.infrastructure.cache.RedisProductoCatalogoStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

/**
 * Cache-Aside coordinator for explicitly allowlisted catalog projections.
 */
public final class ProductoCatalogoCacheService {

    public static final String CATALOGO_GENERATION_KEY = "feriadigital:v1:{catalogo}:epoch";
    public static final String PRODUCTOS_LISTADO_CACHE_KEY = "feriadigital:v1:{catalogo}:productos:listado";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductoCatalogoCacheService.class);
    private static final String METRIC_RESOURCE = "catalogo";

    private final RedisProductoCatalogoStore store;
    private final MeterRegistry meterRegistry;

    public ProductoCatalogoCacheService(RedisProductoCatalogoStore store) {
        this(store, Metrics.globalRegistry);
    }

    public ProductoCatalogoCacheService(RedisProductoCatalogoStore store, MeterRegistry meterRegistry) {
        this.store = Objects.requireNonNull(store, "store");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    public String obtenerJson(
            String generationKey,
            String cacheKeyPrefix,
            Duration ttl,
            Supplier<String> sourceQuery) {
        return obtenerJson(generationKey, cacheKeyPrefix, ttl, sourceQuery, ignored -> { });
    }

    /**
     * The source validator runs on a miss before Redis publication. A caller can
     * use it to complete required downstream reads so a partial HTTP result is
     * never published as a successful cache value.
     */
    public String obtenerJson(
            String generationKey,
            String cacheKeyPrefix,
            Duration ttl,
            Supplier<String> sourceQuery,
            Consumer<String> validateSourceBeforePublication) {
        String safeGenerationKey = requireKey(generationKey, "generationKey");
        String safeCacheKeyPrefix = requireKey(cacheKeyPrefix, "cacheKeyPrefix");
        Duration safeTtl = Objects.requireNonNull(ttl, "ttl");
        if (safeTtl.toMillis() < 1) {
            throw new IllegalArgumentException("ttl must be at least one millisecond");
        }
        Supplier<String> safeSourceQuery = Objects.requireNonNull(sourceQuery, "sourceQuery");
        Consumer<String> safeSourceValidator = Objects.requireNonNull(
                validateSourceBeforePublication,
                "validateSourceBeforePublication");

        long startedAt = System.nanoTime();
        final String generation;
        final String versionedKey;
        try {
            generation = store.getGeneration(safeGenerationKey);
            versionedKey = safeCacheKeyPrefix + ":g" + generation;
            Optional<String> cached = store.getIfGenerationMatches(
                    safeGenerationKey,
                    generation,
                    versionedKey);
            if (cached.isPresent()) {
                recordResult("hit", startedAt);
                return cached.get();
            }
        } catch (RuntimeException redisReadFailure) {
            logRedisUnavailable("read", redisReadFailure);
            recordFailure("read");
            String sourceJson = loadAndValidateSource(safeSourceQuery, safeSourceValidator);
            recordResult("fallback", startedAt);
            return sourceJson;
        }

        String sourceJson = loadAndValidateSource(safeSourceQuery, safeSourceValidator);
        String result = "miss";
        try {
            store.putIfGenerationMatches(
                    safeGenerationKey,
                    generation,
                    versionedKey,
                    sourceJson,
                    safeTtl);
        } catch (RuntimeException redisWriteFailure) {
            logRedisUnavailable("write", redisWriteFailure);
            recordFailure("write");
            result = "fallback";
        }
        recordResult(result, startedAt);
        return sourceJson;
    }

    /** Advances the cache namespace after a committed catalog mutation. */
    public String invalidarCatalogo(String generationKey) {
        return store.advanceGeneration(requireKey(generationKey, "generationKey"));
    }

    private void recordResult(String result, long startedAt) {
        meterRegistry.counter(
                "feriadigital.redis.cache.requests",
                "resource", METRIC_RESOURCE,
                "result", result).increment();
        meterRegistry.timer(
                "feriadigital.redis.cache.duration",
                "resource", METRIC_RESOURCE,
                "result", result).record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
    }

    private void recordFailure(String operation) {
        meterRegistry.counter(
                "feriadigital.redis.cache.failures",
                "resource", METRIC_RESOURCE,
                "operation", operation).increment();
    }

    private String readSource(Supplier<String> sourceQuery) {
        return Objects.requireNonNull(sourceQuery.get(), "sourceQuery result");
    }

    private String loadAndValidateSource(
            Supplier<String> sourceQuery,
            Consumer<String> sourceValidator) {
        String sourceJson = readSource(sourceQuery);
        sourceValidator.accept(sourceJson);
        return sourceJson;
    }

    private String requireKey(String key, String name) {
        String safeKey = Objects.requireNonNull(key, name);
        if (safeKey.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return safeKey;
    }

    private void logRedisUnavailable(String operation, RuntimeException failure) {
        LOGGER.warn("Redis cache {} failed; using the source result ({})",
                operation,
                failure.getClass().getSimpleName());
    }
}
