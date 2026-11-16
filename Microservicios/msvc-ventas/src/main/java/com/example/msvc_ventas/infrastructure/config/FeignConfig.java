package com.example.msvc_ventas.infrastructure.config;

import feign.codec.ErrorDecoder;
import feign.RequestInterceptor;
import org.springframework.http.HttpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            // Personaliza el manejo de errores aquí si es necesario
            return feign.FeignException.errorStatus(methodKey, response);
        };
    }

    @Bean
    public RequestInterceptor bearerTokenRequestInterceptor() {
        return requestTemplate -> {
            // Las lecturas internas de catálogo/envío/stock son públicas (permitAll);
            // evitar enviar JWT potencialmente inválido o expirado en GET internos.
            if ("GET".equalsIgnoreCase(requestTemplate.method())) {
                return;
            }

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }

            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return;
            }

            String token = authorization.substring(7).trim();
            if (token.isEmpty()
                    || "null".equalsIgnoreCase(token)
                    || "undefined".equalsIgnoreCase(token)
                    || token.split("\\.").length != 3) {
                return;
            }

            requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        };
    }
}
