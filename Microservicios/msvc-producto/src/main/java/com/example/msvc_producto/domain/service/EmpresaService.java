package com.example.msvc_producto.domain.service;

import com.example.msvc_producto.domain.model.Empresa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EmpresaService {
    Empresa crearEmpresa(Empresa empresa);
    /** Actualización administrativa (solo ADMIN): incluye RUC y estado activo. */
    Empresa actualizarEmpresa(Long id, Empresa empresa);
    /** Actualización del emprendedor dueño: solo nombre y datos de contacto (A-02). */
    Empresa actualizarDatosContacto(Long id, String nombre, String direccion, String telefono, String email);
    Empresa obtenerEmpresaPorId(Long id);
    List<Empresa> listarEmpresas();
    Page<Empresa> listarEmpresasPaginado(String q, Pageable pageable);
    void eliminarEmpresa(Long id);
}