package com.example.msvc_producto;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheConfigTest {

    private static final String CONFIG_CLASS_NAME =
            "com.example.msvc_producto.infrastructure.config.RedisCacheConfig";

    @Test
    void disabledPilotDoesNotCreateItsRedisTemplateOrConnect() throws Exception {
        AtomicInteger connectionFactoryCalls = new AtomicInteger();
        RedisConnectionFactory connectionFactory = unusedConnectionFactory(connectionFactoryCalls);

        contextRunner(connectionFactory)
                .withPropertyValues("cache.redis.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("pilotRedisTemplate");
                    assertThat(context).doesNotHaveBean("redisProductoCatalogoStore");
                    assertThat(context).doesNotHaveBean("productoCatalogoCacheService");
                    assertThat(connectionFactoryCalls).hasValue(0);
                });
    }

    @Test
    void enabledPilotCreatesTemplateWithoutConnectingDuringStartup() throws Exception {
        AtomicInteger connectionFactoryCalls = new AtomicInteger();
        RedisConnectionFactory connectionFactory = unusedConnectionFactory(connectionFactoryCalls);

        contextRunner(connectionFactory)
                .withPropertyValues("cache.redis.enabled=true")
                .run(context -> {
                    assertThat(context).hasBean("pilotRedisTemplate");
                    assertThat(context).hasBean("redisProductoCatalogoStore");
                    assertThat(context).hasBean("productoCatalogoCacheService");
                    assertThat(connectionFactoryCalls).hasValue(0);
                });
    }

    @Test
    void applicationRedisAutoConfigurationStartsWithoutARedisServerWhenPilotIsDisabled() throws Exception {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withUserConfiguration(Class.forName(CONFIG_CLASS_NAME))
                .withPropertyValues(
                        "cache.redis.enabled=false",
                        "spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=6399",
                        "spring.data.redis.connect-timeout=100ms")
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisConnectionFactory.class);
                    assertThat(context).doesNotHaveBean("pilotRedisTemplate");
                    assertThat(context).doesNotHaveBean("redisProductoCatalogoStore");
                    assertThat(context).doesNotHaveBean("productoCatalogoCacheService");
                });
    }

    private RedisConnectionFactory unusedConnectionFactory(AtomicInteger calls) {
        return (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "unused-test-connection-factory";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    calls.incrementAndGet();
                    return null;
                });
    }

    private ApplicationContextRunner contextRunner(RedisConnectionFactory connectionFactory)
            throws ClassNotFoundException {
        Class<?> redisCacheConfig = Class.forName(CONFIG_CLASS_NAME);
        return new ApplicationContextRunner()
                .withUserConfiguration(redisCacheConfig)
                .withBean(RedisConnectionFactory.class, () -> connectionFactory);
    }
}
