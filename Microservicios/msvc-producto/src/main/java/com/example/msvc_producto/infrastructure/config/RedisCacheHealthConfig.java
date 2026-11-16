package com.example.msvc_producto.infrastructure.config;

import com.example.msvc_producto.infrastructure.health.RedisCacheHealthIndicator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration(proxyBeanMethods = false)
public class RedisCacheHealthConfig {

    @Bean
    HealthIndicator redisCacheHealthIndicator(
            RedisConnectionFactory connectionFactory,
            @Value("${cache.redis.enabled:false}") boolean cacheEnabled) {
        return new RedisCacheHealthIndicator(connectionFactory, cacheEnabled);
    }
}
