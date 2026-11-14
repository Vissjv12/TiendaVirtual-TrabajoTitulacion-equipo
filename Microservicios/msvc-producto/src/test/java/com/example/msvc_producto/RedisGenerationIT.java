package com.example.msvc_producto;

import com.example.msvc_producto.application.service.ProductoCatalogoCacheService;
import com.example.msvc_producto.infrastructure.cache.RedisProductoCatalogoStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RedisGenerationIT {

    @Test
    void missingGenerationIsRecreatedAndCannotResurrectOldEntry() {
        String scope = UUID.randomUUID().toString();
        String generationKey = "feriadigital:a17:generation-it:" + scope + ":generation";
        String cacheKeyPrefix = "feriadigital:a17:generation-it:" + scope + ":catalog";
        AtomicInteger sourceCalls = new AtomicInteger();

        runWithContext((template, store, cache) -> {
            String first = cache.obtenerJson(
                    generationKey,
                    cacheKeyPrefix,
                    Duration.ofMinutes(1),
                    () -> "[{\"revision\":1}]" + sourceCalls.incrementAndGet());
            String oldGeneration = store.getGeneration(generationKey);
            assertThat(oldGeneration).isNotEqualTo("0").isNotBlank();

            // Simulates eviction of only the generation metadata while an old value remains.
            template.delete(generationKey);

            String second = cache.obtenerJson(
                    generationKey,
                    cacheKeyPrefix,
                    Duration.ofMinutes(1),
                    () -> "[{\"revision\":2}]" + sourceCalls.incrementAndGet());
            String newGeneration = store.getGeneration(generationKey);

            assertThat(newGeneration).isNotEqualTo(oldGeneration).isNotEqualTo("0");
            assertThat(first).isEqualTo("[{\"revision\":1}]1");
            assertThat(second).isEqualTo("[{\"revision\":2}]2");
            assertThat(sourceCalls).hasValue(2);
        }, generationKey, cacheKeyPrefix);
    }

    @Test
    void concurrentInitializationReturnsOneSharedGeneration() throws Exception {
        String generationKey = "feriadigital:a17:generation-it:concurrent:" + UUID.randomUUID();

        runWithContext((template, store, cache) -> {
            var pool = Executors.newFixedThreadPool(2);
            try {
                Future<String> first = pool.submit(() -> store.getGeneration(generationKey));
                Future<String> second = pool.submit(() -> store.getGeneration(generationKey));

                String firstGeneration = first.get(3, TimeUnit.SECONDS);
                String secondGeneration = second.get(3, TimeUnit.SECONDS);

                assertThat(firstGeneration).isEqualTo(secondGeneration).isNotEqualTo("0");
            } finally {
                pool.shutdownNow();
            }
        }, generationKey);
    }

    @Test
    void staleGenerationCannotReadOrPublishWhenGenerationKeyIsAbsent() {
        String scope = UUID.randomUUID().toString();
        String generationKey = "feriadigital:a17:generation-it:cas:" + scope + ":generation";
        String cacheKey = "feriadigital:a17:generation-it:cas:" + scope + ":value:g-old";

        runWithContext((template, store, cache) -> {
            template.opsForValue().set(cacheKey, "{\"revision\":1}");

            assertThat(store.putIfGenerationMatches(
                    generationKey,
                    "0",
                    cacheKey + ":new",
                    "{\"revision\":2}",
                    Duration.ofSeconds(30))).isFalse();
            assertThat(store.getIfGenerationMatches(generationKey, "0", cacheKey)).isEmpty();
        }, generationKey, cacheKey);
    }

    private void runWithContext(StoreCheck check, String... cleanupKeys) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withUserConfiguration(com.example.msvc_producto.infrastructure.config.RedisCacheConfig.class)
                .withPropertyValues(
                        "cache.redis.enabled=true",
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=6380",
                        "spring.data.redis.connect-timeout=500ms",
                        "spring.data.redis.timeout=500ms")
                .run(context -> {
                    assertThat(context).hasBean("redisProductoCatalogoStore");
                    StringRedisTemplate template = context.getBean("pilotRedisTemplate", StringRedisTemplate.class);
                    RedisProductoCatalogoStore store = context.getBean(RedisProductoCatalogoStore.class);
                    ProductoCatalogoCacheService cache = context.getBean(ProductoCatalogoCacheService.class);
                    template.delete(List.of(cleanupKeys));
                    try {
                        check.accept(template, store, cache);
                    } finally {
                        template.delete(List.of(cleanupKeys));
                        for (String key : cleanupKeys) {
                            var related = template.keys(key + "*");
                            if (related != null && !related.isEmpty()) {
                                template.delete(related);
                            }
                        }
                    }
                });
    }

    @FunctionalInterface
    private interface StoreCheck {
        void accept(StringRedisTemplate template, RedisProductoCatalogoStore store,
                ProductoCatalogoCacheService cache) throws Exception;
    }
}
