package com.example.demo.application.service;

import com.example.demo.application.dto.LoginRequestDto;
import com.example.demo.application.dto.TokenResponseDto;
import com.example.demo.application.dto.UsuarioResponseDto;
import com.example.demo.domain.model.Usuario;
import com.example.demo.domain.service.UsuarioService;
import com.example.demo.infrastructure.persistence.entity.EmprendedorEntity;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtEncoder jwtEncoder;
    private final UserDetailsService userDetailsService;
    private final UsuarioService usuarioService;
    private final EmprendedorJpaRepository emprendedorRepository;
    private final PasswordEncoder passwordEncoder;

    /** Client ID de Google OAuth; vacío = login con Google deshabilitado */
    @Value("${google.client-id:}")
    private String googleClientId;

    @Value("${jwt.access-token-ttl-seconds:900}")
    private long accessTokenTtlSeconds;

    private static final String GOOGLE_TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    public TokenResponseDto login(LoginRequestDto loginRequest) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getUsername());

        if (!passwordEncoder.matches(loginRequest.getPassword(), userDetails.getPassword())) {
            throw new BadCredentialsException("Contraseña incorrecta");
        }

        if (!userDetails.isEnabled()) {
            throw new DisabledException("La cuenta está desactivada. Contacte al administrador.");
        }

        // Obtener usuario completo para información adicional
        Usuario usuario = usuarioService.buscarPorUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!usuario.isActivo()) {
            throw new DisabledException("La cuenta está desactivada. Contacte al administrador.");
        }

        // Buscar empresaId si el usuario es emprendedor
        Long empresaId = null;
        Optional<EmprendedorEntity> emprendedor = emprendedorRepository.findByUsuarioId(usuario.getId());
        if (emprendedor.isPresent() && emprendedor.get().getEmpresaId() != null) {
            empresaId = emprendedor.get().getEmpresaId();
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        return generateToken(authentication, usuario, empresaId);
    }

    /**
     * Inicia sesión con una credencial (ID token) de Google.
     * Si no existe un usuario con ese email, se registra automáticamente con ROLE_USER.
     */
    public TokenResponseDto loginConGoogle(String idToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new IllegalStateException("El inicio de sesión con Google no está configurado en el servidor");
        }

        Map<String, Object> payload = verificarTokenGoogle(idToken);

        String email = (String) payload.get("email");
        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("El token de Google no contiene un email válido");
        }
        email = email.toLowerCase();

        // Buscar usuario existente por email o crearlo automáticamente
        Usuario usuario = usuarioService.buscarPorEmail(email)
                .orElseGet(() -> registrarUsuarioDesdeGoogle(payload));

        if (!usuario.isActivo()) {
            throw new BadCredentialsException("La cuenta está desactivada");
        }

        // Buscar empresaId si el usuario es emprendedor
        Long empresaId = null;
        Optional<EmprendedorEntity> emprendedor = emprendedorRepository.findByUsuarioId(usuario.getId());
        if (emprendedor.isPresent() && emprendedor.get().getEmpresaId() != null) {
            empresaId = emprendedor.get().getEmpresaId();
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                usuario.getUsername(),
                null,
                usuario.getRoles().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));

        return generateToken(authentication, usuario, empresaId);
    }

    /**
     * Valida el ID token contra el endpoint oficial de Google y verifica el audience.
     */
    private Map<String, Object> verificarTokenGoogle(String idToken) {
        Map<String, Object> payload;
        try {
            RestTemplate restTemplate = new RestTemplate();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(GOOGLE_TOKENINFO_URL + idToken, Map.class);
            payload = response;
        } catch (Exception e) {
            throw new BadCredentialsException("Token de Google inválido o expirado");
        }

        if (payload == null) {
            throw new BadCredentialsException("No se pudo verificar el token de Google");
        }

        // El token debe haber sido emitido para ESTA aplicación
        String audience = (String) payload.get("aud");
        if (!googleClientId.equals(audience)) {
            throw new BadCredentialsException("El token de Google no pertenece a esta aplicación");
        }

        Object emailVerified = payload.get("email_verified");
        if (emailVerified != null && "false".equalsIgnoreCase(String.valueOf(emailVerified))) {
            throw new BadCredentialsException("El email de Google no está verificado");
        }

        return payload;
    }

    /**
     * Crea un usuario nuevo a partir de los datos del token de Google.
     */
    private Usuario registrarUsuarioDesdeGoogle(Map<String, Object> payload) {
        String email = ((String) payload.get("email")).toLowerCase();
        String nombre = (String) payload.getOrDefault("given_name", "Usuario");
        String apellido = (String) payload.getOrDefault("family_name", "Google");

        // Generar username único a partir del email
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9._-]", "");
        if (base.length() < 4) {
            base = base + "user";
        }
        String username = base;
        int sufijo = 1;
        while (usuarioService.existeUsername(username)) {
            username = base + sufijo++;
        }

        Usuario usuario = Usuario.builder()
                .username(username)
                // Contraseña aleatoria: la cuenta se gestiona vía Google
                .password(UUID.randomUUID().toString())
                .email(email)
                .nombre(nombre)
                .apellido(apellido)
                .build();

        return usuarioService.registrarUsuario(usuario);
    }

    private TokenResponseDto generateToken(Authentication authentication, Usuario usuario, Long empresaId) {
        Instant now = Instant.now();
        long expiresIn = accessTokenTtlSeconds;

        String scope = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        // Construir claims del JWT
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(now.plus(expiresIn, ChronoUnit.SECONDS))
                .subject(authentication.getName())
                .claim("scope", scope)
                .claim("userId", usuario.getId())
                .claim("email", usuario.getEmail())
                .claim("nombre", usuario.getNombre())
                .claim("apellido", usuario.getApellido());

        // Agregar empresaId si existe
        if (empresaId != null) {
            claimsBuilder.claim("empresaId", empresaId);
        }

        JwtClaimsSet claims = claimsBuilder.build();

        // Especificar explícitamente HS256 para que NimbusJwtEncoder seleccione
        // la clave HMAC correcta. Sin esto, usa RS256 por defecto y falla.
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();

        // Construir información del usuario en la respuesta
        UsuarioResponseDto usuarioDto = new UsuarioResponseDto();
        usuarioDto.setId(usuario.getId());
        usuarioDto.setUsername(usuario.getUsername());
        usuarioDto.setEmail(usuario.getEmail());
        usuarioDto.setNombre(usuario.getNombre());
        usuarioDto.setApellido(usuario.getApellido());
        usuarioDto.setActivo(usuario.isActivo());
        usuarioDto.setRoles(usuario.getRoles());
        usuarioDto.setEmpresaId(empresaId);

        // Construir respuesta completa
        TokenResponseDto tokenResponse = new TokenResponseDto();
        tokenResponse.setAccessToken(token);
        tokenResponse.setTokenType("Bearer");
        tokenResponse.setExpiresIn(expiresIn);
        tokenResponse.setUsuario(usuarioDto);

        return tokenResponse;
    }
}
