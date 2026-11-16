package com.example.demo.application.service;

import com.example.demo.application.dto.EmpresaVinculadaResponseDto;
import com.example.demo.application.dto.UsuarioResponseDto;
import com.example.demo.domain.model.Usuario;
import com.example.demo.domain.service.UsuarioService;
import com.example.demo.infrastructure.persistence.entity.EmprendedorEntity;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import com.example.demo.infrastructure.producto.EmpresaProductoClient;
import com.example.demo.infrastructure.producto.EmpresaProductoClient.EmpresaResumen;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * T11: vinculación administrativa emprendedor -> empresa (antes solo por SQL).
 *
 * La relación vive en auth_service.emprendedores; la empresa vive en msvc-producto, que es quien
 * dice si existe y está activa (sin relación JPA entre bases). El JWT toma empresaId de esta tabla
 * en el siguiente login, y producto, inventario y pagos aíslan por ese claim.
 *
 * Integridad: una empresa pertenece a un solo emprendedor. Se comprueba antes de guardar y además
 * lo garantiza el índice único uk_emprendedor_empresa (solicitudes simultáneas -> 409, nunca dos filas).
 */
@Service
@RequiredArgsConstructor
public class EmprendedorEmpresaService {

    private static final Logger AUDITORIA = LoggerFactory.getLogger("auditoria.emprendedores");
    private static final String ROLE_EMP = "ROLE_EMP";

    private final UsuarioService usuarioService;
    private final EmprendedorJpaRepository emprendedorRepository;
    private final EmpresaProductoClient empresaProductoClient;

    @Transactional(readOnly = true)
    public EmpresaVinculadaResponseDto consultar(Long usuarioId) {
        buscarUsuario(usuarioId);
        Long empresaId = emprendedorRepository.findByUsuarioId(usuarioId)
                .map(EmprendedorEntity::getEmpresaId)
                .orElse(null);
        return new EmpresaVinculadaResponseDto(usuarioId, empresaId, null, null, "CONSULTA", false);
    }

    @Transactional
    public EmpresaVinculadaResponseDto vincular(Long usuarioId, Long empresaId) {
        Usuario usuario = buscarUsuario(usuarioId);
        if (usuario.getRoles() == null || !usuario.getRoles().contains(ROLE_EMP)) {
            auditar("RECHAZADA", usuarioId, null, empresaId, "el usuario no tiene ROLE_EMP");
            throw new IllegalArgumentException("Solo un usuario con rol de emprendedor puede vincularse a una empresa");
        }

        EmprendedorEntity actual = emprendedorRepository.findByUsuarioId(usuarioId).orElse(null);
        Long anterior = actual != null ? actual.getEmpresaId() : null;
        if (Objects.equals(anterior, empresaId)) {
            // Idempotente: repetir la misma vinculación no cambia nada ni exige nuevo login
            auditar("SIN_CAMBIOS", usuarioId, anterior, empresaId, null);
            return new EmpresaVinculadaResponseDto(usuarioId, empresaId, null, anterior, "SIN_CAMBIOS", false);
        }

        // La empresa la valida su dueño (msvc-producto): 404 si no existe, 503 si no responde
        EmpresaResumen empresa;
        try {
            empresa = empresaProductoClient.obtenerEmpresa(empresaId);
        } catch (NoSuchElementException ex) {
            auditar("RECHAZADA", usuarioId, anterior, empresaId, "la empresa no existe");
            throw ex;
        }
        if (!empresa.estaActiva()) {
            auditar("RECHAZADA", usuarioId, anterior, empresaId, "la empresa está inactiva");
            throw new VinculacionEmpresaConflictoException("La empresa está inactiva; actívela antes de vincularla");
        }

        emprendedorRepository.findByEmpresaId(empresaId)
                .filter(otro -> !Objects.equals(otro.getUsuarioId(), usuarioId))
                .ifPresent(otro -> {
                    auditar("RECHAZADA", usuarioId, anterior, empresaId, "la empresa ya está vinculada a otro emprendedor");
                    throw new VinculacionEmpresaConflictoException(
                            "La empresa ya está vinculada a otro emprendedor; desvincúlela primero");
                });

        EmprendedorEntity fila = actual != null ? actual : EmprendedorEntity.builder()
                .usuarioId(usuarioId)
                .activo(true)
                .build();
        fila.setEmpresaId(empresaId);
        try {
            // flush dentro de la transacción: una carrera con otra solicitud choca aquí con el índice único
            emprendedorRepository.saveAndFlush(fila);
        } catch (DataIntegrityViolationException ex) {
            auditar("RECHAZADA", usuarioId, anterior, empresaId, "conflicto concurrente de unicidad");
            throw new VinculacionEmpresaConflictoException(
                    "La empresa o el emprendedor se vincularon en otra operación simultánea; vuelva a consultar");
        }

        String resultado = anterior == null ? "VINCULADA" : "REASIGNADA";
        auditar(resultado, usuarioId, anterior, empresaId, null);
        return new EmpresaVinculadaResponseDto(usuarioId, empresaId, empresa.nombre(), anterior, resultado, true);
    }

    @Transactional
    public EmpresaVinculadaResponseDto desvincular(Long usuarioId) {
        buscarUsuario(usuarioId);
        EmprendedorEntity actual = emprendedorRepository.findByUsuarioId(usuarioId).orElse(null);
        if (actual == null || actual.getEmpresaId() == null) {
            auditar("SIN_CAMBIOS", usuarioId, null, null, "no tenía empresa vinculada");
            return new EmpresaVinculadaResponseDto(usuarioId, null, null, null, "SIN_CAMBIOS", false);
        }
        Long anterior = actual.getEmpresaId();
        actual.setEmpresaId(null);
        emprendedorRepository.saveAndFlush(actual);
        auditar("DESVINCULADA", usuarioId, anterior, null, null);
        return new EmpresaVinculadaResponseDto(usuarioId, null, null, anterior, "DESVINCULADA", true);
    }

    /**
     * Completa empresaId en respuestas administrativas con UNA consulta para toda la página
     * (antes UsuarioMapper nunca lo llenaba y el listado mostraba null).
     */
    @Transactional(readOnly = true)
    public void completarEmpresa(Collection<UsuarioResponseDto> usuarios) {
        if (usuarios == null || usuarios.isEmpty()) {
            return;
        }
        var ids = usuarios.stream().map(UsuarioResponseDto::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, EmprendedorEntity> porUsuario = emprendedorRepository.findByUsuarioIdIn(ids).stream()
                .collect(Collectors.toMap(EmprendedorEntity::getUsuarioId, Function.identity(), (a, b) -> a));
        usuarios.forEach(dto -> {
            EmprendedorEntity e = porUsuario.get(dto.getId());
            dto.setEmpresaId(e != null ? e.getEmpresaId() : null);
        });
    }

    private Usuario buscarUsuario(Long usuarioId) {
        return usuarioService.buscarPorId(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
    }

    /** Registro de auditoría: quién, a quién, empresa anterior y nueva, resultado. Fecha y hora las pone el log. */
    private void auditar(String resultado, Long usuarioId, Long anterior, Long nueva, String motivo) {
        AUDITORIA.info("Vinculación emprendedor-empresa: resultado={} admin={} usuario={} empresaAnterior={} empresaNueva={}{}",
                resultado, administradorActual(), usuarioId, anterior, nueva,
                motivo != null ? " motivo=" + motivo : "");
    }

    private static Object administradorActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            Object userId = jwt.getToken().getClaims().get("userId");
            return userId != null ? userId : jwt.getName();
        }
        return auth != null ? auth.getName() : "desconocido";
    }
}
