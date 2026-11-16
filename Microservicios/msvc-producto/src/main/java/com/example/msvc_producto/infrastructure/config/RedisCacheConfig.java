package com.example.msvc_producto.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.example.msvc_producto.application.service.ProductoCatalogoCacheService;
import com.example.msvc_producto.infrastructure.cache.RedisProductoCatalogoStore;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cache.redis", name = "enabled", havingValue = "true")
public class RedisCacheConfig {

    @Bean("pilotRedisTemplate")
    StringRedisTemplate pilotRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry redisPilotMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    RedisProductoCatalogoStore redisProductoCatalogoStore(
            @Qualifier("pilotRedisTemplate") StringRedisTemplate template) {
        return new RedisProductoCatalogoStore(template);
    }

    @Bean
    ProductoCatalogoCacheService productoCatalogoCacheService(
            RedisProductoCatalogoStore store,
            MeterRegistry meterRegistry) {
        return new ProductoCatalogoCacheService(store, meterRegistry);
    }
}
