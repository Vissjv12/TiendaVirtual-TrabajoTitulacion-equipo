package com.example.demo.application.service;

import com.example.demo.application.dto.EmpresaVinculadaResponseDto;
import com.example.demo.application.dto.UsuarioResponseDto;
import com.example.demo.domain.model.Usuario;
import com.example.demo.domain.service.UsuarioService;
import com.example.demo.infrastructure.persistence.entity.EmprendedorEntity;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import com.example.demo.infrastructure.producto.EmpresaProductoClient;
import com.example.demo.infrastructure.producto.EmpresaProductoClient.EmpresaResumen;
import com.example.demo.infrastructure.producto.ProductoNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T11: vinculación emprendedor-empresa. Usuario 5 = emprendedor; usuario 6 = cliente; usuario 8 = otro
 * emprendedor. Empresa 10 activa, 11 inactiva, 12 ya vinculada al usuario 8.
 */
class EmprendedorEmpresaServiceTest {

    private UsuarioService usuarioService;
    private EmprendedorJpaRepository repo;
    private EmpresaProductoClient productos;
    private EmprendedorEmpresaService service;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        repo = mock(EmprendedorJpaRepository.class);
        productos = mock(EmpresaProductoClient.class);
        service = new EmprendedorEmpresaService(usuarioService, repo, productos);

        when(usuarioService.buscarPorId(5L)).thenReturn(Optional.of(usuario(5L, "ROLE_EMP")));
        when(usuarioService.buscarPorId(6L)).thenReturn(Optional.of(usuario(6L, "ROLE_USER")));
        when(usuarioService.buscarPorId(8L)).thenReturn(Optional.of(usuario(8L, "ROLE_EMP")));
        when(productos.obtenerEmpresa(10L)).thenReturn(new EmpresaResumen(10L, "Sigcholac", true));
        when(productos.obtenerEmpresa(11L)).thenReturn(new EmpresaResumen(11L, "Cerrada", false));
        when(productos.obtenerEmpresa(12L)).thenReturn(new EmpresaResumen(12L, "Ajena", true));
        when(productos.obtenerEmpresa(99L)).thenThrow(new NoSuchElementException("La empresa 99 no existe"));
        when(repo.findByEmpresaId(12L)).thenReturn(Optional.of(fila(80L, 8L, 12L)));
        when(repo.findByEmpresaId(10L)).thenReturn(Optional.empty());
        when(repo.findByUsuarioId(anyLong())).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void vincularEmprendedorSinFilaCreaLaFilaConLaEmpresa() {
        EmpresaVinculadaResponseDto r = service.vincular(5L, 10L);

        ArgumentCaptor<EmprendedorEntity> captor = ArgumentCaptor.forClass(EmprendedorEntity.class);
        verify(repo).saveAndFlush(captor.capture());
        assertEquals(5L, captor.getValue().getUsuarioId());
        assertEquals(10L, captor.getValue().getEmpresaId());
        assertTrue(captor.getValue().getActivo());
        assertEquals("VINCULADA", r.resultado());
        assertEquals("Sigcholac", r.empresaNombre());
        assertTrue(r.requiereNuevoLogin(), "el JWT vigente no cambia hasta el próximo login");
    }

    @Test
    void reasignarConservaLaFilaYRegistraLaEmpresaAnterior() {
        EmprendedorEntity existente = fila(50L, 5L, 3L);
        when(repo.findByUsuarioId(5L)).thenReturn(Optional.of(existente));

        EmpresaVinculadaResponseDto r = service.vincular(5L, 10L);

        assertEquals("REASIGNADA", r.resultado());
        assertEquals(3L, r.empresaAnteriorId());
        assertEquals(10L, existente.getEmpresaId());
        assertEquals(50L, existente.getId(), "no se crea otra fila");
    }

    @Test
    void repetirLaMismaVinculacionEsIdempotente() {
        when(repo.findByUsuarioId(5L)).thenReturn(Optional.of(fila(50L, 5L, 10L)));

        EmpresaVinculadaResponseDto r = service.vincular(5L, 10L);

        assertEquals("SIN_CAMBIOS", r.resultado());
        assertFalse(r.requiereNuevoLogin());
        verify(repo, never()).saveAndFlush(any());
        verifyNoInteractions(productos);
    }

    @Test
    void usuarioInexistenteResponde404SinTocarNada() {
        assertThrows(NoSuchElementException.class, () -> service.vincular(404L, 10L));
        verifyNoInteractions(productos);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void usuarioQueNoEsEmprendedorSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> service.vincular(6L, 10L));
        verifyNoInteractions(productos);
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void empresaInexistenteResponde404() {
        assertThrows(NoSuchElementException.class, () -> service.vincular(5L, 99L));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void empresaInactivaEsConflicto() {
        assertThrows(VinculacionEmpresaConflictoException.class, () -> service.vincular(5L, 11L));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void empresaYaVinculadaAOtroEmprendedorEsConflicto() {
        assertThrows(VinculacionEmpresaConflictoException.class, () -> service.vincular(5L, 12L));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void servicioDeProductosCaidoNoVincula() {
        when(productos.obtenerEmpresa(10L)).thenThrow(new ProductoNoDisponibleException("caído"));
        assertThrows(ProductoNoDisponibleException.class, () -> service.vincular(5L, 10L));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void solicitudSimultaneaQueChocaConElIndiceUnicoEsConflictoNoDuplicado() {
        // Otra solicitud vinculó la empresa 10 entre la comprobación y el guardado
        when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_emprendedor_empresa"));
        assertThrows(VinculacionEmpresaConflictoException.class, () -> service.vincular(5L, 10L));
    }

    @Test
    void desvincularQuitaLaEmpresaYPideNuevoLogin() {
        EmprendedorEntity existente = fila(50L, 5L, 10L);
        when(repo.findByUsuarioId(5L)).thenReturn(Optional.of(existente));

        EmpresaVinculadaResponseDto r = service.desvincular(5L);

        assertNull(existente.getEmpresaId());
        assertEquals("DESVINCULADA", r.resultado());
        assertEquals(10L, r.empresaAnteriorId());
        assertTrue(r.requiereNuevoLogin());
    }

    @Test
    void desvincularSinEmpresaEsIdempotente() {
        EmpresaVinculadaResponseDto r = service.desvincular(5L);
        assertEquals("SIN_CAMBIOS", r.resultado());
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void desvincularUsuarioInexistenteResponde404() {
        assertThrows(NoSuchElementException.class, () -> service.desvincular(404L));
    }

    @Test
    void completarEmpresaUsaUnaSolaConsultaParaTodaLaPagina() {
        UsuarioResponseDto emp = dto(5L);
        UsuarioResponseDto cliente = dto(6L);
        emp.setEmpresaId(null);
        cliente.setEmpresaId(777L); // valor basura previo: se corrige a null
        when(repo.findByUsuarioIdIn(any())).thenReturn(List.of(fila(50L, 5L, 10L)));

        service.completarEmpresa(List.of(emp, cliente));

        assertEquals(10L, emp.getEmpresaId());
        assertNull(cliente.getEmpresaId());
        verify(repo, times(1)).findByUsuarioIdIn(any());
    }

    private static Usuario usuario(Long id, String rol) {
        return Usuario.builder().id(id).username("u" + id).roles(Set.of(rol)).activo(true).build();
    }

    private static EmprendedorEntity fila(Long id, Long usuarioId, Long empresaId) {
        return EmprendedorEntity.builder().id(id).usuarioId(usuarioId).empresaId(empresaId).activo(true).build();
    }

    private static UsuarioResponseDto dto(Long id) {
        UsuarioResponseDto d = new UsuarioResponseDto();
        d.setId(id);
        return d;
    }
}
