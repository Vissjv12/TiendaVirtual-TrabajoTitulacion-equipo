package com.example.demo.application.service;

import com.example.demo.domain.model.Usuario;
import com.example.demo.domain.repository.UsuarioRepository;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UsuarioServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmprendedorJpaRepository emprendedorRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void whenRegistrarUsuario_thenReturnUsuario() {
        // Arrange
        Usuario usuarioInput = Usuario.builder()
                .username("testuser")
                .password("password123")
                .email("test@example.com")
                .nombre("Test")
                .apellido("User")
                .build();

        Usuario usuarioGuardado = Usuario.builder()
                .id(1L)
                .username("testuser")
                .password("encodedPassword")
                .email("test@example.com")
                .nombre("Test")
                .apellido("User")
                .roles(Collections.singleton("ROLE_USER"))
                .activo(true)
                .build();

        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioGuardado);

        // Act
        Usuario resultado = usuarioService.registrarUsuario(usuarioInput);

        // Assert
        assertNotNull(resultado);
        assertEquals(1L, resultado.getId());
        assertEquals("testuser", resultado.getUsername());
        assertEquals("encodedPassword", resultado.getPassword());
        assertEquals("test@example.com", resultado.getEmail());
        assertEquals("Test", resultado.getNombre());
        assertEquals("User", resultado.getApellido());
        assertTrue(resultado.isActivo());

        Set<String> roles = resultado.getRoles();
        assertNotNull(roles);
        assertEquals(1, roles.size());
        assertTrue(roles.contains("ROLE_USER"));

        // Verify
        verify(passwordEncoder).encode("password123");
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void whenBuscarPorUsername_thenReturnUsuario() {
        // Arrange
        String username = "testuser";
        Usuario usuario = Usuario.builder()
                .id(1L)
                .username(username)
                .email("test@example.com")
                .nombre("Test")
                .apellido("User")
                .roles(Collections.singleton("ROLE_USER"))
                .activo(true)
                .build();

        when(usuarioRepository.findByUsername(username)).thenReturn(Optional.of(usuario));

        // Act
        Optional<Usuario> resultado = usuarioService.buscarPorUsername(username);

        // Assert
        assertTrue(resultado.isPresent());
        assertEquals(username, resultado.get().getUsername());

        // Verify
        verify(usuarioRepository).findByUsername(username);
    }

    @Test
    void whenBuscarPorUsernameNoExiste_thenReturnEmpty() {
        // Arrange
        String username = "nonexistentuser";
        when(usuarioRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Act
        Optional<Usuario> resultado = usuarioService.buscarPorUsername(username);

        // Assert
        assertFalse(resultado.isPresent());

        // Verify
        verify(usuarioRepository).findByUsername(username);
    }

    @Test
    void whenExisteUsername_thenReturnTrue() {
        // Arrange
        String username = "existinguser";
        when(usuarioRepository.existsByUsername(username)).thenReturn(true);

        // Act
        boolean resultado = usuarioService.existeUsername(username);

        // Assert
        assertTrue(resultado);

        // Verify
        verify(usuarioRepository).existsByUsername(username);
    }

    @Test
    void whenNoExisteUsername_thenReturnFalse() {
        // Arrange
        String username = "nonexistentuser";
        when(usuarioRepository.existsByUsername(username)).thenReturn(false);

        // Act
        boolean resultado = usuarioService.existeUsername(username);

        // Assert
        assertFalse(resultado);

        // Verify
        verify(usuarioRepository).existsByUsername(username);
    }

    @Test
    void whenExisteEmail_thenReturnTrue() {
        // Arrange
        String email = "existing@example.com";
        when(usuarioRepository.existsByEmail(email)).thenReturn(true);

        // Act
        boolean resultado = usuarioService.existeEmail(email);

        // Assert
        assertTrue(resultado);

        // Verify
        verify(usuarioRepository).existsByEmail(email);
    }

    @Test
    void whenNoExisteEmail_thenReturnFalse() {
        // Arrange
        String email = "nonexistent@example.com";
        when(usuarioRepository.existsByEmail(email)).thenReturn(false);

        // Act
        boolean resultado = usuarioService.existeEmail(email);

        // Assert
        assertFalse(resultado);

        // Verify
        verify(usuarioRepository).existsByEmail(email);
    }

    @Test
    void whenEliminarUsuarioNormal_thenBorradoLogicoSinDeleteById() {
        Usuario cliente = Usuario.builder()
                .id(7L)
                .username("cmbonifaz")
                .roles(Collections.singleton("ROLE_USER"))
                .activo(true)
                .build();

        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(cliente));
        when(emprendedorRepository.findByUsuarioId(7L)).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.eliminarUsuario(7L);

        assertFalse(cliente.isActivo());
        verify(usuarioRepository).save(cliente);
        verify(usuarioRepository, never()).deleteById(anyLong());
    }

    @Test
    void whenEliminarUnicoAdmin_thenLanzaExcepcion() {
        Usuario admin = Usuario.builder()
                .id(1L)
                .username("admin")
                .roles(Collections.singleton("ROLE_ADMIN"))
                .activo(true)
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByRolesContaining("ROLE_ADMIN")).thenReturn(1L);
        when(usuarioRepository.countByRolAndActivoTrue("ROLE_ADMIN")).thenReturn(1L);

        assertThrows(IllegalStateException.class, () -> usuarioService.eliminarUsuario(1L));
        assertThrows(IllegalStateException.class, () -> usuarioService.cambiarEstado(1L, false));
        verify(usuarioRepository, never()).save(any());
        verify(usuarioRepository, never()).deleteById(anyLong());
    }
}