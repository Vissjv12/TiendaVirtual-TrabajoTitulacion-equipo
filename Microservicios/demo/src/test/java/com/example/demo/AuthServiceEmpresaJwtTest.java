package com.example.demo;

import com.example.demo.application.dto.LoginRequestDto;
import com.example.demo.application.dto.TokenResponseDto;
import com.example.demo.application.service.AuthService;
import com.example.demo.domain.model.Usuario;
import com.example.demo.domain.service.UsuarioService;
import com.example.demo.infrastructure.persistence.entity.EmprendedorEntity;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T11: el JWT toma empresaId de emprendedores en cada login. Sin vinculación no hay claim (y producto,
 * inventario y pagos niegan las operaciones de empresa); tras vincular, el siguiente login lo incluye.
 */
class AuthServiceEmpresaJwtTest {

    private static final String SECRETO = "clave-de-prueba-de-al-menos-32-bytes-t11-jwt-123456";

    private EmprendedorJpaRepository emprendedores;
    private AuthService authService;
    private NimbusJwtDecoder decoder;

    @BeforeEach
    void setUp() {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(SECRETO.getBytes(StandardCharsets.UTF_8))
                .algorithm(JWSAlgorithm.HS256).build();
        decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();

        UserDetailsService userDetails = mock(UserDetailsService.class);
        when(userDetails.loadUserByUsername("emp")).thenReturn(
                User.withUsername("emp").password("hash").authorities("ROLE_EMP").build());
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches(any(), any())).thenReturn(true);
        UsuarioService usuarios = mock(UsuarioService.class);
        when(usuarios.buscarPorUsername("emp")).thenReturn(Optional.of(
                Usuario.builder().id(5L).username("emp").email("emp@test.com").nombre("Ana").apellido("Lopez")
                        .roles(Set.of("ROLE_EMP")).activo(true).build()));
        emprendedores = mock(EmprendedorJpaRepository.class);

        authService = new AuthService(new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk))),
                userDetails, usuarios, emprendedores, encoder);
    }

    @Test
    void emprendedorSinEmpresaNoRecibeClaimEmpresaId() {
        when(emprendedores.findByUsuarioId(5L)).thenReturn(Optional.empty());

        TokenResponseDto r = authService.login(login());

        Jwt jwt = decoder.decode(r.getAccessToken());
        assertFalse(jwt.hasClaim("empresaId"));
        assertNull(r.getUsuario().getEmpresaId());
    }

    @Test
    void trasVincularElSiguienteLoginIncluyeLaEmpresa() {
        when(emprendedores.findByUsuarioId(5L)).thenReturn(Optional.of(
                EmprendedorEntity.builder().id(50L).usuarioId(5L).empresaId(10L).activo(true).build()));

        TokenResponseDto r = authService.login(login());

        Jwt jwt = decoder.decode(r.getAccessToken());
        assertEquals(10L, ((Number) jwt.getClaim("empresaId")).longValue());
        assertEquals(10L, r.getUsuario().getEmpresaId());
    }

    @Test
    void filaDesvinculadaNoEmiteEmpresaId() {
        when(emprendedores.findByUsuarioId(5L)).thenReturn(Optional.of(
                EmprendedorEntity.builder().id(50L).usuarioId(5L).empresaId(null).activo(true).build()));

        Jwt jwt = decoder.decode(authService.login(login()).getAccessToken());
        assertFalse(jwt.hasClaim("empresaId"));
    }

    private static LoginRequestDto login() {
        LoginRequestDto dto = new LoginRequestDto();
        dto.setUsername("emp");
        dto.setPassword("secreta");
        return dto;
    }
}
