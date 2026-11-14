package com.example.msvc_producto;

import com.example.msvc_producto.application.service.ProductoCatalogoCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RedisProductoCatalogoStoreIT {

    private static final String CONFIG_CLASS_NAME =
            "com.example.msvc_producto.infrastructure.config.RedisCacheConfig";

    @Test
    void generationChangeRejectsStaleWriteAndAllowsCurrentJsonPayload() throws Exception {
        String scope = UUID.randomUUID().toString();
        String generationKey = "feriadigital:a17:it:generation:" + scope;
        String payloadKey = "feriadigital:a17:it:catalogo:g0:" + scope;
        String expiringPayloadKey = "feriadigital:a17:it:catalogo:ttl:" + scope;
        String payload = "{\"fixture\":\"synthetic\",\"id\":1}";

        runWithStore(store -> {
            String generation = (String) invoke(store, "getGeneration",
                    new Class<?>[]{String.class}, generationKey);
            assertThat(generation).isNotBlank().isNotEqualTo("0");

            String nextGeneration = (String) invoke(store, "advanceGeneration",
                    new Class<?>[]{String.class}, generationKey);
            assertThat(nextGeneration).isNotEqualTo(generation);

            boolean staleWriteAccepted = (boolean) invoke(store, "putIfGenerationMatches",
                    new Class<?>[]{String.class, String.class, String.class, String.class, Duration.class},
                    generationKey, generation, payloadKey, payload, Duration.ofSeconds(30));
            assertThat(staleWriteAccepted).isFalse();
            assertThat((Optional<?>) invoke(store, "get", new Class<?>[]{String.class}, payloadKey))
                    .isEmpty();

            boolean currentWriteAccepted = (boolean) invoke(store, "putIfGenerationMatches",
                    new Class<?>[]{String.class, String.class, String.class, String.class, Duration.class},
                    generationKey, nextGeneration, payloadKey, payload, Duration.ofSeconds(30));
            assertThat(currentWriteAccepted).isTrue();
            assertThat(invoke(store, "getIfGenerationMatches",
                    new Class<?>[]{String.class, String.class, String.class},
                    generationKey, nextGeneration, payloadKey))
                    .isEqualTo(Optional.of(payload));

            boolean expiringWriteAccepted = (boolean) invoke(store, "putIfGenerationMatches",
                    new Class<?>[]{String.class, String.class, String.class, String.class, Duration.class},
                    generationKey, nextGeneration, expiringPayloadKey, payload, Duration.ofMillis(150));
            assertThat(expiringWriteAccepted).isTrue();
            Thread.sleep(300);
            assertThat((Optional<?>) invoke(store, "get", new Class<?>[]{String.class}, expiringPayloadKey))
                    .isEmpty();
        }, generationKey, payloadKey, expiringPayloadKey);
    }

    @Test
    void cacheAsideUsesLocalRedisForMissThenHit() throws Exception {
        String scope = UUID.randomUUID().toString();
        String generationKey = "feriadigital:a17:it:cache-aside:generation:" + scope;
        String cacheKeyPrefix = "feriadigital:a17:it:cache-aside:catalogo:" + scope;
        String payload = "[{\"fixture\":\"synthetic\",\"id\":2}]";
        AtomicInteger sourceCalls = new AtomicInteger();

        runWithCacheService(service -> {
            String first = service.obtenerJson(
                    generationKey,
                    cacheKeyPrefix,
                    Duration.ofSeconds(30),
                    () -> {
                        sourceCalls.incrementAndGet();
                        return payload;
                    });
            String second = service.obtenerJson(
                    generationKey,
                    cacheKeyPrefix,
                    Duration.ofSeconds(30),
                    () -> {
                        sourceCalls.incrementAndGet();
                        return payload;
                    });

            assertThat(first).isEqualTo(payload);
            assertThat(second).isEqualTo(payload);
            assertThat(sourceCalls).hasValue(1);
        }, generationKey, cacheKeyPrefix + ":g0");
    }

    @Test
    void unavailableRedisFallsBackToTheSyntheticSource() throws Exception {
        AtomicBoolean sourceCalled = new AtomicBoolean();
        String payload = "[{\"fixture\":\"synthetic-fallback\",\"id\":3}]";

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withUserConfiguration(Class.forName(CONFIG_CLASS_NAME))
                .withPropertyValues(
                        "cache.redis.enabled=true",
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=6399",
                        "spring.data.redis.connect-timeout=150ms",
                        "spring.data.redis.timeout=150ms")
                .run(context -> {
                    ProductoCatalogoCacheService service = context.getBean(ProductoCatalogoCacheService.class);
                    String result = service.obtenerJson(
                            "feriadigital:a17:it:unavailable:generation",
                            "feriadigital:a17:it:unavailable:catalogo",
                            Duration.ofSeconds(30),
                            () -> {
                                sourceCalled.set(true);
                                return payload;
                            });

                    assertThat(result).isEqualTo(payload);
                    assertThat(sourceCalled).isTrue();
                });
    }

    private void runWithStore(StoreCheck check, String... keys) throws Exception {
        Class<?> redisCacheConfig = Class.forName(CONFIG_CLASS_NAME);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withUserConfiguration(redisCacheConfig)
                .withPropertyValues(
                        "cache.redis.enabled=true",
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=6380",
                        "spring.data.redis.connect-timeout=500ms",
                        "spring.data.redis.timeout=500ms")
                .run(context -> {
                    assertThat(context).hasBean("redisProductoCatalogoStore");
                    StringRedisTemplate template = context.getBean("pilotRedisTemplate", StringRedisTemplate.class);
                    template.delete(List.of(keys));
                    try {
                        check.accept(context.getBean("redisProductoCatalogoStore"));
                    } catch (Throwable exception) {
                        failure.set(exception);
                    } finally {
                        template.delete(List.of(keys));
                        for (String key : keys) {
                            var related = template.keys(key + "*");
                            if (related != null && !related.isEmpty()) {
                                template.delete(related);
                            }
                        }
                    }
                });

        if (failure.get() != null) {
            throw new AssertionError("Redis store integration check failed", failure.get());
        }
    }

    private void runWithCacheService(CacheServiceCheck check, String... keys) throws Exception {
        Class<?> redisCacheConfig = Class.forName(CONFIG_CLASS_NAME);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withUserConfiguration(redisCacheConfig)
                .withPropertyValues(
                        "cache.redis.enabled=true",
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=6380",
                        "spring.data.redis.connect-timeout=500ms",
                        "spring.data.redis.timeout=500ms")
                .run(context -> {
                    assertThat(context).hasBean("productoCatalogoCacheService");
                    StringRedisTemplate template = context.getBean("pilotRedisTemplate", StringRedisTemplate.class);
                    template.delete(List.of(keys));
                    try {
                        check.accept(context.getBean(ProductoCatalogoCacheService.class));
                    } catch (Throwable exception) {
                        failure.set(exception);
                    } finally {
                        template.delete(List.of(keys));
                        for (String key : keys) {
                            var related = template.keys(key + "*");
                            if (related != null && !related.isEmpty()) {
                                template.delete(related);
                            }
                        }
                    }
                });

        if (failure.get() != null) {
            throw new AssertionError("Redis Cache-Aside integration check failed", failure.get());
        }
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checkedException) {
                throw checkedException;
            }
            throw exception;
        }
    }

    @FunctionalInterface
    private interface StoreCheck {
        void accept(Object store) throws Exception;
    }

    @FunctionalInterface
    private interface CacheServiceCheck {
        void accept(ProductoCatalogoCacheService service) throws Exception;
    }
}
