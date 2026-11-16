package com.example.msvc_producto.application.service;

import com.example.msvc_producto.domain.model.Empresa;
import com.example.msvc_producto.domain.repository.EmpresaRepository;
import com.example.msvc_producto.domain.service.EmpresaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class EmpresaServiceImpl implements EmpresaService {

    private static final org.slf4j.Logger AUDITORIA = org.slf4j.LoggerFactory.getLogger("auditoria.empresas");

    private final EmpresaRepository empresaRepository;

    @Autowired
    private ProductoCatalogoInvalidacionService invalidacionService;

    public EmpresaServiceImpl(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    @Override
    @Transactional
    public Empresa crearEmpresa(Empresa empresa) {
        validarRucUnico(empresa.getRuc(), null);
        empresa.setFechaCreacion(LocalDateTime.now());
        empresa.setFechaActualizacion(LocalDateTime.now());
        empresa.setActivo(true);
        Empresa empresaGuardada = empresaRepository.save(empresa);
        invalidarCatalogoDespuesDeCommit();
        return empresaGuardada;
    }

    @Override
    @Transactional
    public Empresa actualizarEmpresa(Long id, Empresa empresa) {
        // Lista blanca de campos administrativos (solo ADMIN llega aquí). Nunca se copia la
        // entidad recibida completa; si "activo" no se envía, se conserva el estado actual (A-02).
        Empresa empresaExistente = obtenerEmpresaPorId(id);
        validarRucUnico(empresa.getRuc(), id);

        String rucAnterior = empresaExistente.getRuc();
        Boolean activoAnterior = empresaExistente.getActivo();

        empresaExistente.setNombre(empresa.getNombre());
        empresaExistente.setRuc(empresa.getRuc());
        empresaExistente.setDireccion(empresa.getDireccion());
        empresaExistente.setTelefono(empresa.getTelefono());
        empresaExistente.setEmail(empresa.getEmail());
        if (empresa.getActivo() != null) {
            empresaExistente.setActivo(empresa.getActivo());
        }
        empresaExistente.setFechaActualizacion(LocalDateTime.now());

        Empresa empresaActualizada = empresaRepository.save(empresaExistente);
        auditarCambioAdministrativo(id, rucAnterior, activoAnterior, empresaActualizada);
        invalidarCatalogoDespuesDeCommit();
        return empresaActualizada;
    }

    /** El RUC es único (también lo garantiza la BD): se responde 400 claro en vez de un 500. */
    private void validarRucUnico(String ruc, Long idExcluido) {
        if (empresaRepository.existeRucEnOtraEmpresa(ruc, idExcluido)) {
            throw new IllegalArgumentException("Ya existe otra empresa registrada con ese RUC");
        }
    }

    /** Registro de quién cambió datos administrativos (A-02). No se registran otros datos de la empresa. */
    private void auditarCambioAdministrativo(Long id, String rucAnterior, Boolean activoAnterior, Empresa actual) {
        boolean cambioRuc = !java.util.Objects.equals(rucAnterior, actual.getRuc());
        boolean cambioEstado = !java.util.Objects.equals(activoAnterior, actual.getActivo());
        if (cambioRuc || cambioEstado) {
            AUDITORIA.info("Empresa {}: cambio administrativo por usuario {} (rucModificado={}, activo {} -> {})",
                    id, usuarioActual(), cambioRuc, activoAnterior, actual.getActivo());
        }
    }

    private static Object usuarioActual() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt) {
            Object userId = jwt.getToken().getClaims().get("userId");
            return userId != null ? userId : jwt.getName();
        }
        return auth != null ? auth.getName() : "desconocido";
    }

    @Override
    @Transactional
    public Empresa actualizarDatosContacto(Long id, String nombre, String direccion, String telefono, String email) {
        // Lista blanca del emprendedor: RUC y estado activo NO se tocan (A-02)
        Empresa empresaExistente = obtenerEmpresaPorId(id);

        empresaExistente.setNombre(nombre);
        empresaExistente.setDireccion(direccion);
        empresaExistente.setTelefono(telefono);
        empresaExistente.setEmail(email);
        empresaExistente.setFechaActualizacion(LocalDateTime.now());

        Empresa empresaActualizada = empresaRepository.save(empresaExistente);
        invalidarCatalogoDespuesDeCommit();
        return empresaActualizada;
    }

    @Override
    @Transactional(readOnly = true)
    public Empresa obtenerEmpresaPorId(Long id) {
        return empresaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Empresa no encontrada con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Empresa> listarEmpresas() {
        return empresaRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Empresa> listarEmpresasPaginado(String q, Pageable pageable) {
        return empresaRepository.findPaginado(q == null ? "" : q.trim(), pageable);
    }

    @Override
    @Transactional
    public void eliminarEmpresa(Long id) {
        Empresa empresa = obtenerEmpresaPorId(id);
        empresa.setActivo(false);
        empresa.setFechaActualizacion(LocalDateTime.now());
        empresaRepository.save(empresa);
        invalidarCatalogoDespuesDeCommit();
    }

    private void invalidarCatalogoDespuesDeCommit() {
        if (invalidacionService != null) {
            invalidacionService.invalidarDespuesDeCommit();
        }
    }
}
