package com.example.msvc_inventario.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Protege las operaciones de escritura del inventario con el mismo JWT
 * emitido por el servicio de autenticación.
 *
 * Las lecturas de catálogo y cantidad permanecen públicas porque son
 * consumidas por el catálogo y el checkout. La autorización de negocio del
 * flujo de compra se completa en A-24.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/inventarios/**", "/api/movimientos/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/inventarios/cantidad/batch").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/inventarios/salida-lote").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/inventarios/producto/*/stock").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/inventarios/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_EMP")
                        .requestMatchers(HttpMethod.PUT, "/api/inventarios/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_EMP")
                        .requestMatchers(HttpMethod.DELETE, "/api/inventarios/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_EMP")
                        .requestMatchers(HttpMethod.POST, "/api/movimientos/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_EMP")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(
                jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        authoritiesConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
