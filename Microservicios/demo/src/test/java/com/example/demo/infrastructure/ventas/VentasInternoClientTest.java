package com.example.demo.infrastructure.ventas;

import com.example.demo.infrastructure.security.ServicioTokenProvider;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Canal interno auth-service -> msvc-ventas: credencial de servicio de corta duración y
 * traducción de errores (4xx no reintentable, 5xx reintentable).
 */
class VentasInternoClientTest {

    private static final String SECRETO = "clave-de-prueba-de-al-menos-32-bytes-1234567890";
    private static final String BASE = "http://ventas.test";

    private MockRestServiceServer ventas;
    private VentasInternoClient cliente;
    private NimbusJwtDecoder decoder;

    @BeforeEach
    void setUp() {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(SECRETO.getBytes(StandardCharsets.UTF_8))
                .algorithm(JWSAlgorithm.HS256).build();
        ServicioTokenProvider tokens = new ServicioTokenProvider(
                new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk))));
        decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();

        RestClient.Builder builder = RestClient.builder();
        ventas = MockRestServiceServer.bindTo(builder).build();
        cliente = new VentasInternoClient(builder, tokens, BASE);
    }

    @Test
    void usaCredencialDeServicioValidaYDeCortaDuracion() {
        AtomicReference<String> autorizacion = new AtomicReference<>();
        ventas.expect(requestTo(BASE + "/api/ventas/internal/pagos-pasarela/77"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(req -> autorizacion.set(req.getHeaders().getFirst("Authorization")))
                .andRespond(withSuccess("{\"ventaId\":77,\"empresaId\":10,\"total\":12.30,\"estado\":\"PENDIENTE\"}",
                        MediaType.APPLICATION_JSON));

        VentaResumenPago r = cliente.obtenerResumen(77L);

        assertEquals(10L, r.empresaId());
        assertTrue(autorizacion.get().startsWith("Bearer "));
        Jwt jwt = decoder.decode(autorizacion.get().substring(7));
        assertEquals("ROLE_SERVICIO_PAGOS", jwt.getClaimAsString("scope"));
        assertEquals("servicio", jwt.getClaimAsString("tipo"));
        assertFalse(jwt.getClaims().containsKey("email"));
        assertFalse(jwt.getClaims().containsKey("empresaId"));
        assertTrue(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds() <= 120);
    }

    @Test
    void registrarEnviaReferenciaYMontoEnCentavos() {
        ventas.expect(requestTo(BASE + "/api/ventas/internal/pagos-pasarela/77/aprobar"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.referenciaTransaccion").value("555"))
                .andExpect(jsonPath("$.montoCentavos").value(1230))
                .andExpect(jsonPath("$.pasarela").value("PAYPHONE"))
                .andRespond(withSuccess("{\"ventaId\":77,\"resultado\":\"COMPLETADA\"}", MediaType.APPLICATION_JSON));

        assertEquals("COMPLETADA", cliente.registrarPagoAprobado(77L, "555", 1230L, "PAYPHONE", "FD-77-x").resultado());
        ventas.verify();
    }

    @Test
    void erroresSeTraducenSegunSiSonReintentables() {
        ventas.expect(requestTo(BASE + "/api/ventas/internal/pagos-pasarela/1")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        ventas.expect(requestTo(BASE + "/api/ventas/internal/pagos-pasarela/2/aprobar")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        ventas.expect(requestTo(BASE + "/api/ventas/internal/pagos-pasarela/3")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(NoSuchElementException.class, () -> cliente.obtenerResumen(1L));
        assertThrows(VentaPagoRechazadoException.class, () -> cliente.registrarPagoAprobado(2L, "r", 1L, "PAYPHONE", "c"));
        assertThrows(VentasNoDisponibleException.class, () -> cliente.obtenerResumen(3L));
    }
}
