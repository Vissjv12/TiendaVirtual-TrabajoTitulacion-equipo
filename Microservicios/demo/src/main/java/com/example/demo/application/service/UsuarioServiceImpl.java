package com.example.demo.application.service;

import com.example.demo.application.dto.ActualizarUsuarioDto;
import com.example.demo.application.dto.EstadisticasUsuariosDto;
import com.example.demo.domain.model.Usuario;
import com.example.demo.domain.repository.UsuarioRepository;
import com.example.demo.domain.service.UsuarioService;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final UsuarioRepository usuarioRepository;
    private final EmprendedorJpaRepository emprendedorRepository;
    private final PasswordEncoder passwordEncoder;

    // =====================================
    // MÉTODOS EXISTENTES (NO MODIFICADOS)
    // =====================================

    @Override
    public Usuario registrarUsuario(Usuario usuario) {
        // Encriptar password
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        // Asignar rol por defecto
        usuario.setRoles(Collections.singleton("ROLE_USER"));
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    @Override
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    @Override
    public boolean existeUsername(String username) {
        return usuarioRepository.existsByUsername(username);
    }

    @Override
    public boolean existeEmail(String email) {
        return usuarioRepository.existsByEmail(email);
    }

    @Override
    public Usuario registrarUsuarioConRoles(Usuario usuario) {
        // Encriptar password
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuario.setActivo(true);
        // ✅ NO sobrescribir los roles - usar los que vienen del mapper
        return usuarioRepository.save(usuario);
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    // =====================================
    // MÉTODOS NUEVOS PARA GESTIÓN ADMIN
    // =====================================

    @Override
    public List<Usuario> obtenerTodosLosUsuarios() {
        return usuarioRepository.findAll();
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return usuarioRepository.findById(id);
    }

    @Override
    public Usuario actualizarUsuario(Long id, ActualizarUsuarioDto actualizarDto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Actualizar campos si están presentes
        if (actualizarDto.getUsername() != null) {
            usuario.setUsername(actualizarDto.getUsername());
        }

        if (actualizarDto.getEmail() != null) {
            usuario.setEmail(actualizarDto.getEmail());
        }

        if (actualizarDto.getNombre() != null) {
            usuario.setNombre(actualizarDto.getNombre());
        }

        if (actualizarDto.getApellido() != null) {
            usuario.setApellido(actualizarDto.getApellido());
        }

        if (actualizarDto.getRoles() != null) {
            validarProteccionUltimoAdmin(usuario, usuario.isActivo(), actualizarDto.getRoles());
            usuario.setRoles(actualizarDto.getRoles());
        }

        // Si se proporciona nueva contraseña, encriptarla
        if (actualizarDto.getPassword() != null && !actualizarDto.getPassword().trim().isEmpty()) {
            usuario.setPassword(passwordEncoder.encode(actualizarDto.getPassword()));
        }

        return usuarioRepository.save(usuario);
    }

    @Override
    public Usuario cambiarEstado(Long id, boolean activo) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!activo) {
            validarProteccionUltimoAdmin(usuario, false, usuario.getRoles());
        }

        usuario.setActivo(activo);
        Usuario actualizado = usuarioRepository.save(usuario);
        sincronizarEstadoEmprendedor(id, activo);
        return actualizado;
    }

    @Override
    public void eliminarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        validarProteccionUltimoAdmin(usuario, false, usuario.getRoles());

        // Borrado lógico para conservar integridad referencial e historial
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
        sincronizarEstadoEmprendedor(id, false);
    }

    private void validarProteccionUltimoAdmin(Usuario usuario, boolean nuevoActivo, Set<String> nuevosRoles) {
        boolean esAdminActual = usuario.getRoles() != null && usuario.getRoles().contains(ROLE_ADMIN);
        if (!esAdminActual) {
            return;
        }

        boolean mantieneRolAdmin = nuevosRoles != null && nuevosRoles.contains(ROLE_ADMIN);
        boolean dejaDeSerAdminActivo = !nuevoActivo || !mantieneRolAdmin;
        if (!dejaDeSerAdminActivo) {
            return;
        }

        long totalAdmins = usuarioRepository.countByRolesContaining(ROLE_ADMIN);
        long adminsActivos = usuarioRepository.countByRolAndActivoTrue(ROLE_ADMIN);

        if (totalAdmins <= 1 || (usuario.isActivo() && adminsActivos <= 1)) {
            if (!mantieneRolAdmin) {
                throw new IllegalStateException(
                        "No se puede quitar el rol de administrador al único administrador del sistema");
            }
            throw new IllegalStateException(
                    "No se puede eliminar ni desactivar al único administrador del sistema");
        }
    }

    private void sincronizarEstadoEmprendedor(Long usuarioId, boolean activo) {
        if (emprendedorRepository == null) {
            return;
        }
        emprendedorRepository.findByUsuarioId(usuarioId).ifPresent(emp -> {
            emp.setActivo(activo);
            emprendedorRepository.save(emp);
        });
    }

    @Override
    public List<Usuario> buscarUsuarios(String query) {
        return usuarioRepository.findByUsernameContainingOrNombreContainingOrApellidoContainingOrEmailContaining(
                query, query, query, query);
    }

    @Override
    public List<Usuario> buscarPorRol(String rol) {
        return usuarioRepository.findByRolesContaining(rol);
    }

    @Override
    public Page<Usuario> obtenerUsuariosPaginado(String q, String rol, Boolean activo, Pageable pageable) {
        return usuarioRepository.findPaginado(q, rol, activo, pageable);
    }

    @Override
    public EstadisticasUsuariosDto obtenerEstadisticas() {
        long totalUsuarios = usuarioRepository.count();
        long usuariosActivos = usuarioRepository.countByActivoTrue();
        long usuariosInactivos = usuarioRepository.countByActivoFalse();
        long administradores = usuarioRepository.countByRolesContaining(ROLE_ADMIN);
        long administradoresActivos = usuarioRepository.countByRolAndActivoTrue(ROLE_ADMIN);
        long emprendedores = usuarioRepository.countByRolesContaining("ROLE_EMP");
        long clientes = usuarioRepository.countByRolesContaining("ROLE_USER");

        return EstadisticasUsuariosDto.builder()
                .totalUsuarios(totalUsuarios)
                .usuariosActivos(usuariosActivos)
                .usuariosInactivos(usuariosInactivos)
                .administradores(administradores)
                .administradoresActivos(administradoresActivos)
                .emprendedores(emprendedores)
                .clientes(clientes)
                .build();
    }
}