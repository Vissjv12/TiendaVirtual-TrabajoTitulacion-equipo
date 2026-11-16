package com.example.demo.infrastructure.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Emite la credencial INTERNA de servicio que auth-service usa para registrar pagos de
 * pasarela en msvc-ventas (y que msvc-ventas reenvía a inventario para descontar stock) (A-04).
 *
 * Características:
 *  - Rol exclusivo ROLE_SERVICIO_PAGOS + claim tipo=servicio (ningún usuario los recibe).
 *  - Vida corta (2 minutos) y firmada con el mismo JWT_SECRET que ya validan los servicios.
 *  - Nunca se entrega al navegador ni se registra en logs.
 */
@Component
public class ServicioTokenProvider {

    public static final String ROLE_SERVICIO_PAGOS = "ROLE_SERVICIO_PAGOS";
    static final long VIGENCIA_SEGUNDOS = 120;

    private final JwtEncoder jwtEncoder;

    public ServicioTokenProvider(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String emitirTokenPagos() {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("auth-service")
                .subject("servicio:pagos")
                .issuedAt(ahora)
                .expiresAt(ahora.plus(VIGENCIA_SEGUNDOS, ChronoUnit.SECONDS))
                .claim("scope", ROLE_SERVICIO_PAGOS)
                .claim("tipo", "servicio")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
