package com.example.demo.presentation.controller;

import com.example.demo.application.dto.*;
import com.example.demo.application.mapper.UsuarioMapper;
import com.example.demo.application.service.AuthService;
import com.example.demo.application.service.PasswordResetService;
import com.example.demo.application.util.PaginationUtils;
import com.example.demo.domain.model.Usuario;
import com.example.demo.domain.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Gestión de Usuarios", description = "API para gestión de usuarios por administradores")
public class AuthController {

    private final UsuarioService usuarioService;
    private final AuthService authService;
    private final UsuarioMapper usuarioMapper;
    private final PasswordResetService passwordResetService;
    private final com.example.demo.application.service.EmprendedorEmpresaService emprendedorEmpresaService;

    // =====================================
    // ENDPOINTS DE AUTENTICACIÓN PÚBLICA
    // =====================================

    @PostMapping("/auth/registro")
    @Operation(summary = "Registro público de clientes")
    public ResponseEntity<UsuarioResponseDto> registrarCliente(@Valid @RequestBody RegistroUsuarioDto registroDto) {
        if (usuarioService.existeUsername(registroDto.getUsername())) {
            return ResponseEntity.badRequest().build();
        }

        if (usuarioService.existeEmail(registroDto.getEmail())) {
            return ResponseEntity.badRequest().build();
        }

        Usuario usuario = usuarioMapper.toDomain(registroDto);
        Usuario usuarioRegistrado = usuarioService.registrarUsuario(usuario);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(usuarioMapper.toResponseDto(usuarioRegistrado));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Iniciar sesión")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDto loginRequest) {
        try {
            TokenResponseDto tokenResponse = authService.login(loginRequest);

            Optional<Usuario> usuarioOpt = usuarioService.buscarPorUsername(loginRequest.getUsername());
            if (usuarioOpt.isPresent()) {
                Usuario usuario = usuarioOpt.get();
                UsuarioResponseDto usuarioDto = usuarioMapper.toResponseDto(usuario);
                // T11: conservar el empresaId resuelto al emitir el JWT (antes se perdía aquí y llegaba null)
                if (tokenResponse.getUsuario() != null) {
                    usuarioDto.setEmpresaId(tokenResponse.getUsuario().getEmpresaId());
                }
                tokenResponse.setUsuario(usuarioDto);
            }

            return ResponseEntity.ok(tokenResponse);
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Usuario o contraseña incorrectos"));
        }
    }

    @PostMapping("/auth/google")
    @Operation(summary = "Iniciar sesión o registrarse con Google",
            description = "Recibe el ID token de Google Identity Services; si el email no existe, crea la cuenta automáticamente")
    public ResponseEntity<?> loginConGoogle(@Valid @RequestBody GoogleLoginRequestDto googleRequest) {
        try {
            TokenResponseDto tokenResponse = authService.loginConGoogle(googleRequest.getIdToken());
            return ResponseEntity.ok(tokenResponse);
        } catch (IllegalStateException e) {
            // Google no configurado en el servidor
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("message", e.getMessage() != null ? e.getMessage() : "Token de Google inválido"));
        }
    }

    // =====================================
    // RECUPERACIÓN DE CONTRASEÑA (código temporal por correo)
    // =====================================

    @PostMapping("/auth/recuperar-password/solicitar")
    @Operation(summary = "Solicitar código de recuperación",
            description = "Envía un código temporal de 6 dígitos al correo registrado del usuario (válido 15 min)")
    public ResponseEntity<?> solicitarCodigoRecuperacion(@Valid @RequestBody SolicitarCodigoDto dto) {
        passwordResetService.solicitarCodigo(dto.getUsername());
        // Respuesta genérica: no revela si el usuario existe o no
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Si el usuario existe, se envió un código a su correo registrado"));
    }

    @PostMapping("/auth/recuperar-password/verificar")
    @Operation(summary = "Verificar código de recuperación",
            description = "Valida el código recibido por correo y emite un token de restablecimiento de un solo uso")
    public ResponseEntity<?> verificarCodigoRecuperacion(@Valid @RequestBody VerificarCodigoDto dto) {
        try {
            String resetToken = passwordResetService.verificarCodigo(dto.getUsername(), dto.getCodigo());
            return ResponseEntity.ok(java.util.Map.of("resetToken", resetToken));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/auth/recuperar-password/restablecer")
    @Operation(summary = "Restablecer contraseña",
            description = "Consume el token de restablecimiento emitido tras verificar el código y define la nueva contraseña")
    public ResponseEntity<?> restablecerPasswordConToken(@Valid @RequestBody RestablecerConTokenDto dto) {
        try {
            passwordResetService.restablecerPassword(dto.getUsername(), dto.getResetToken(), dto.getNuevaPassword());
            return ResponseEntity.ok(java.util.Map.of("message", "Contraseña actualizada exitosamente"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    // =====================================
    // GESTIÓN DE USUARIOS (Solo Admin)
    // =====================================

    @PostMapping("/usuarios")
    @Operation(summary = "Crear usuario con rol específico (Solo Admin)",
            description = "Permite al administrador crear cualquier tipo de usuario eligiendo el rol")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponseDto> crearUsuario(
            @Valid @RequestBody RegistroUsuarioAdminDto registroDto) {

        // Validar unicidad
        if (usuarioService.existeUsername(registroDto.getUsername())) {
            return ResponseEntity.badRequest().build();
        }

        if (usuarioService.existeEmail(registroDto.getEmail())) {
            return ResponseEntity.badRequest().build();
        }

        // Crear usuario con roles específicos
        Usuario usuario = usuarioMapper.toDomainFromAdmin(registroDto);
        Usuario usuarioRegistrado = usuarioService.registrarUsuarioConRoles(usuario);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(usuarioMapper.toResponseDto(usuarioRegistrado));
    }

    private static final Map<String, String> USUARIO_SORT_FIELDS = Map.of(
            "id", "id",
            "username", "username",
            "email", "email",
            "nombre", "nombre",
            "apellido", "apellido",
            "activo", "activo"
    );

    @GetMapping("/usuarios/paginado")
    @Operation(summary = "Listar usuarios paginados (Solo Admin, contrato A-19)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponseDto<UsuarioResponseDto>> obtenerUsuariosPaginado(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "id,desc") String sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) Boolean activo) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "id", Sort.Direction.DESC, USUARIO_SORT_FIELDS);
        Page<Usuario> result = usuarioService.obtenerUsuariosPaginado(q, rol, activo, pageable);
        PageResponseDto<UsuarioResponseDto> pagina = PageResponseDto.fromPage(result, usuarioMapper::toResponseDto);
        emprendedorEmpresaService.completarEmpresa(pagina.getContent());
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/usuarios")
    @Operation(summary = "Listar todos los usuarios (Solo Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioResponseDto>> obtenerUsuarios() {
        try {
            List<Usuario> usuarios = usuarioService.obtenerTodosLosUsuarios();
            List<UsuarioResponseDto> usuariosDto = usuarios.stream()
                    .map(usuarioMapper::toResponseDto)
                    .collect(Collectors.toList());
            emprendedorEmpresaService.completarEmpresa(usuariosDto);

            return ResponseEntity.ok(usuariosDto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/usuarios/{id}")
    @Operation(summary = "Obtener usuario por ID (Solo Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponseDto> obtenerUsuarioPorId(@PathVariable Long id) {
        try {
            Optional<Usuario> usuarioOpt = usuarioService.buscarPorId(id);

            if (usuarioOpt.isPresent()) {
                UsuarioResponseDto usuarioDto = usuarioMapper.toResponseDto(usuarioOpt.get());
                emprendedorEmpresaService.completarEmpresa(List.of(usuarioDto));
                return ResponseEntity.ok(usuarioDto);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/usuarios/{id}")
    @Operation(summary = "Actualizar usuario (Solo Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> actualizarUsuario(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarUsuarioDto actualizarDto) {
        try {
            Optional<Usuario> usuarioExistenteOpt = usuarioService.buscarPorId(id);
            if (!usuarioExistenteOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Usuario usuarioExistente = usuarioExistenteOpt.get();

            // Validaciones de unicidad
            if (actualizarDto.getUsername() != null &&
                    !actualizarDto.getUsername().equals(usuarioExistente.getUsername()) &&
                    usuarioService.existeUsername(actualizarDto.getUsername())) {
                return ResponseEntity.badRequest().build();
            }

            if (actualizarDto.getEmail() != null &&
                    !actualizarDto.getEmail().equals(usuarioExistente.getEmail()) &&
                    usuarioService.existeEmail(actualizarDto.getEmail())) {
                return ResponseEntity.badRequest().build();
            }

            Usuario usuarioActualizado = usuarioService.actualizarUsuario(id, actualizarDto);
            UsuarioResponseDto usuarioDto = usuarioMapper.toResponseDto(usuarioActualizado);
            emprendedorEmpresaService.completarEmpresa(List.of(usuarioDto));

            return ResponseEntity.ok(usuarioDto);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PatchMapping("/usuarios/{id}/estado")
    @Operation(summary = "Cambiar estado del usuario (Solo Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cambiarEstadoUsuario(
            @PathVariable Long id,
            @RequestBody CambiarEstadoDto estadoDto) {
        try {
            Usuario usuarioActualizado = usuarioService.cambiarEstado(id, estadoDto.isActivo());
            UsuarioResponseDto usuarioDto = usuarioMapper.toResponseDto(usuarioActualizado);
            emprendedorEmpresaService.completarEmpresa(List.of(usuarioDto));

            return ResponseEntity.ok(usuarioDto);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/usuarios/{id}")
    @Operation(summary = "Eliminar usuario lógicamente (Solo Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> eliminarUsuario(@PathVariable Long id) {
        try {
            usuarioService.eliminarUsuario(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/usuarios/buscar")
    @Operation(summary = "Buscar usuarios (Solo Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioResponseDto>> buscarUsuarios(@RequestParam String q) {
        try {
            List<Usuario> usuarios = usuarioService.buscarUsuarios(q);
            List<UsuarioResponseDto> usuariosDto = usuarios.stream()
                    .map(usuarioMapper::toResponseDto)
                    .collect(Collectors.toList());
            emprendedorEmpresaService.completarEmpresa(usuariosDto);

            return ResponseEntity.ok(usuariosDto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/usuarios/rol/{rol}")
    @Operation(summary = "Obtener usuarios por rol (Solo Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioResponseDto>> obtenerUsuariosPorRol(@PathVariable String rol) {
        try {
            List<Usuario> usuarios = usuarioService.buscarPorRol(rol);
            List<UsuarioResponseDto> usuariosDto = usuarios.stream()
                    .map(usuarioMapper::toResponseDto)
                    .collect(Collectors.toList());
            emprendedorEmpresaService.completarEmpresa(usuariosDto);

            return ResponseEntity.ok(usuariosDto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/usuarios/estadisticas")
    @Operation(summary = "Estadísticas de usuarios (Solo Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EstadisticasUsuariosDto> obtenerEstadisticas() {
        try {
            EstadisticasUsuariosDto estadisticas = usuarioService.obtenerEstadisticas();
            return ResponseEntity.ok(estadisticas);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // =====================================
    // VALIDACIONES
    // =====================================

    @GetMapping("/usuarios/validar/username/{username}")
    @Operation(summary = "Validar username disponible")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Boolean> validarUsername(@PathVariable String username) {
        boolean disponible = !usuarioService.existeUsername(username);
        return ResponseEntity.ok(disponible);
    }

    @GetMapping("/usuarios/validar/email/{email}")
    @Operation(summary = "Validar email disponible")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Boolean> validarEmail(@PathVariable String email) {
        boolean disponible = !usuarioService.existeEmail(email);
        return ResponseEntity.ok(disponible);
    }

    // =====================================
    // OPTIONS
    // =====================================

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        return ResponseEntity.ok().build();
    }
}