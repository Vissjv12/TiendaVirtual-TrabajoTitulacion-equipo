package com.example.msvc_producto;

import com.example.msvc_producto.infrastructure.cache.RedisProductoCatalogoStore;
import com.example.msvc_producto.application.service.ProductoCatalogoCacheService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoCatalogoCacheServiceTest {

    private static final String GENERATION_KEY = "feriadigital:a17:synthetic:catalogo:generation";
    private static final String KEY_PREFIX = "feriadigital:a17:synthetic:catalogo:productos:listado:publico";
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final String SYNTHETIC_JSON = "[{\"fixture\":\"synthetic\",\"id\":1}]";

    @Mock
    private RedisProductoCatalogoStore store;

    @Test
    void hitReturnsCachedJsonWithoutCallingSource() {
        when(store.getGeneration(GENERATION_KEY)).thenReturn("4");
        when(store.getIfGenerationMatches(GENERATION_KEY, "4", KEY_PREFIX + ":g4"))
                .thenReturn(Optional.of(SYNTHETIC_JSON));
        AtomicBoolean sourceCalled = new AtomicBoolean();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        Supplier<String> source = () -> {
            sourceCalled.set(true);
            return SYNTHETIC_JSON;
        };
        ProductoCatalogoCacheService service = new ProductoCatalogoCacheService(store, metrics);

        String result = service.obtenerJson(GENERATION_KEY, KEY_PREFIX, TTL, source);

        assertThat(result).isEqualTo(SYNTHETIC_JSON);
        assertThat(sourceCalled).isFalse();
        assertThat(metrics.get("feriadigital.redis.cache.requests")
                .tag("resource", "catalogo")
                .tag("result", "hit")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(metrics.get("feriadigital.redis.cache.duration")
                .tag("resource", "catalogo")
                .tag("result", "hit")
                .timer()
                .count()).isEqualTo(1);
        verify(store, never()).putIfGenerationMatches(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missLoadsFromSourceAndPublishesUnderObservedGenerationWithTtl() {
        when(store.getGeneration(GENERATION_KEY)).thenReturn("4");
        when(store.getIfGenerationMatches(GENERATION_KEY, "4", KEY_PREFIX + ":g4"))
                .thenReturn(Optional.empty());
        when(store.putIfGenerationMatches(GENERATION_KEY, "4", KEY_PREFIX + ":g4", SYNTHETIC_JSON, TTL))
                .thenReturn(true);
        ProductoCatalogoCacheService service = new ProductoCatalogoCacheService(store);

        String result = service.obtenerJson(GENERATION_KEY, KEY_PREFIX, TTL, () -> SYNTHETIC_JSON);

        assertThat(result).isEqualTo(SYNTHETIC_JSON);
        verify(store).putIfGenerationMatches(GENERATION_KEY, "4", KEY_PREFIX + ":g4", SYNTHETIC_JSON, TTL);
    }

    @Test
    void sourceValidatorRunsBeforeCachePublication() {
        when(store.getGeneration(GENERATION_KEY)).thenReturn("4");
        when(store.getIfGenerationMatches(GENERATION_KEY, "4", KEY_PREFIX + ":g4"))
                .thenReturn(Optional.empty());
        when(store.putIfGenerationMatches(GENERATION_KEY, "4", KEY_PREFIX + ":g4", SYNTHETIC_JSON, TTL))
                .thenReturn(true);
        AtomicBoolean validatorCalled = new AtomicBoolean();
        ProductoCatalogoCacheService service = new ProductoCatalogoCacheService(store);

        service.obtenerJson(
                GENERATION_KEY,
                KEY_PREFIX,
                TTL,
                () -> SYNTHETIC_JSON,
                json -> validatorCalled.set(json.equals(SYNTHETIC_JSON)));

        assertThat(validatorCalled).isTrue();
        verify(store).putIfGenerationMatches(GENERATION_KEY, "4", KEY_PREFIX + ":g4", SYNTHETIC_JSON, TTL);
    }

    @Test
    void sourceValidatorFailurePreventsCachePublication() {
        when(store.getGeneration(GENERATION_KEY)).thenReturn("4");
        when(store.getIfGenerationMatches(GENERATION_KEY, "4", KEY_PREFIX + ":g4"))
                .thenReturn(Optional.empty());
        ProductoCatalogoCacheService service = new ProductoCatalogoCacheService(store);

        assertThatThrownBy(() -> service.obtenerJson(
                GENERATION_KEY,
                KEY_PREFIX,
                TTL,
                () -> SYNTHETIC_JSON,
                json -> { throw new IllegalStateException("inventory unavailable"); }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inventory unavailable");
        verify(store, never()).putIfGenerationMatches(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void redisReadFailureFallsBackToSourceWithoutTryingRedisWrite() {
        when(store.getGeneration(GENERATION_KEY)).thenThrow(new RedisConnectionFailureException("offline"));
        ProductoCatalogoCacheService service = new ProductoCatalogoCacheService(store);

        String result = service.obtenerJson(GENERATION_KEY, KEY_PREFIX, TTL, () -> SYNTHETIC_JSON);

        assertThat(result).isEqualTo(SYNTHETIC_JSON);
        verify(store, never()).putIfGenerationMatches(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void redisWriteFailureReturnsSourceResult() {
        when(store.getGeneration(GENERATION_KEY)).thenReturn("4");
        when(store.getIfGenerationMatches(GENERATION_KEY, "4", KEY_PREFIX + ":g4"))
                .thenReturn(Optional.empty());
        when(store.putIfGenerationMatches(GENERATION_KEY, "4", KEY_PREFIX + ":g4", SYNTHETIC_JSON, TTL))
                .thenThrow(new RedisConnectionFailureException("offline"));
        ProductoCatalogoCacheService service = new ProductoCatalogoCacheService(store);

        String result = service.obtenerJson(GENERATION_KEY, KEY_PREFIX, TTL, () -> SYNTHETIC_JSON);

        assertThat(result).isEqualTo(SYNTHETIC_JSON);
    }

    @Test
    void sourceFailurePropagatesAndIsNeverCached() {
        when(store.getGeneration(GENERATION_KEY)).thenReturn("4");
        when(store.getIfGenerationMatches(GENERATION_KEY, "4", KEY_PREFIX + ":g4"))
                .thenReturn(Optional.empty());
        ProductoCatalogoCacheService service = new ProductoCatalogoCacheService(store);
        Supplier<String> source = () -> {
            throw new IllegalStateException("source failed");
        };

        assertThatThrownBy(() -> service.obtenerJson(GENERATION_KEY, KEY_PREFIX, TTL, source))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("source failed");
        verify(store, never()).putIfGenerationMatches(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }
}
