package com.example.msvc_producto.infrastructure.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** Reports Redis cache availability without including connection details. */
public final class RedisCacheHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;
    private final boolean cacheEnabled;

    public RedisCacheHealthIndicator(RedisConnectionFactory connectionFactory, boolean cacheEnabled) {
        this.connectionFactory = connectionFactory;
        this.cacheEnabled = cacheEnabled;
    }

    @Override
    public Health health() {
        if (!cacheEnabled) {
            return Health.up().withDetail("state", "disabled").build();
        }

        RedisConnection connection = null;
        try {
            connection = connectionFactory.getConnection();
            connection.ping();
            return Health.up().build();
        } catch (RuntimeException failure) {
            return Health.down().withDetail("cache", "redis-unavailable").build();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (RuntimeException ignored) {
                    // Keep connection diagnostics out of the health response.
                }
            }
        }
    }
}
