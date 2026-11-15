package com.example.msvc_producto;

import com.example.msvc_producto.application.dto.EmpresaRequestDto;
import com.example.msvc_producto.application.mapper.EmpresaMapper;
import com.example.msvc_producto.application.service.EmpresaServiceImpl;
import com.example.msvc_producto.domain.model.Empresa;
import com.example.msvc_producto.domain.repository.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A-02: actualización de empresas por lista blanca de campos.
 * El emprendedor no puede cambiar RUC ni estado; el valor por defecto activo=true ya no reactiva.
 */
class EmpresaListaBlancaTest {

    private EmpresaRepository repository;
    private EmpresaServiceImpl service;
    private Empresa existente;

    @BeforeEach
    void setUp() {
        repository = mock(EmpresaRepository.class);
        service = new EmpresaServiceImpl(repository);
        existente = new Empresa();
        existente.setId(2L);
        existente.setNombre("Original");
        existente.setRuc("1700000000001");
        existente.setActivo(false);
        when(repository.findById(2L)).thenReturn(Optional.of(existente));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void contactoNoModificaRucNiEstado() {
        Empresa r = service.actualizarDatosContacto(2L, "Nuevo nombre", "Calle 1", "0999999999", "a@b.com");

        assertEquals("Nuevo nombre", r.getNombre());
        assertEquals("Calle 1", r.getDireccion());
        assertEquals("1700000000001", r.getRuc());
        assertFalse(r.getActivo(), "Una empresa desactivada no puede reactivarse editando su contacto");
    }

    @Test
    void actualizacionAdministrativaSinActivoConservaElEstado() {
        Empresa cambios = new Empresa();
        cambios.setNombre("Nombre admin");
        cambios.setRuc("1799999999001");

        Empresa r = service.actualizarEmpresa(2L, cambios);

        assertEquals("1799999999001", r.getRuc());
        assertFalse(r.getActivo());
    }

    @Test
    void actualizacionAdministrativaPuedeCambiarElEstadoExplicitamente() {
        Empresa cambios = new Empresa();
        cambios.setNombre("Nombre admin");
        cambios.setRuc("1700000000001");
        cambios.setActivo(true);

        assertEquals(true, service.actualizarEmpresa(2L, cambios).getActivo());
    }

    @Test
    void rucDuplicadoEnOtraEmpresaSeRechazaSinGuardar() {
        when(repository.existeRucEnOtraEmpresa("1799999999001", 2L)).thenReturn(true);
        Empresa cambios = new Empresa();
        cambios.setNombre("Nombre");
        cambios.setRuc("1799999999001");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.actualizarEmpresa(2L, cambios));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
        assertEquals("1700000000001", existente.getRuc());
    }

    @Test
    void crearEmpresaConRucExistenteSeRechaza() {
        when(repository.existeRucEnOtraEmpresa("1700000000001", null)).thenReturn(true);
        Empresa nueva = new Empresa();
        nueva.setNombre("Otra");
        nueva.setRuc("1700000000001");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.crearEmpresa(nueva));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void mapperYaNoFijaActivoTruePorDefecto() {
        EmpresaRequestDto dto = new EmpresaRequestDto("Empresa", "1700000000001", null, null, null, null);
        assertNull(new EmpresaMapper().toEntity(dto, 2L).getActivo());
    }
}
