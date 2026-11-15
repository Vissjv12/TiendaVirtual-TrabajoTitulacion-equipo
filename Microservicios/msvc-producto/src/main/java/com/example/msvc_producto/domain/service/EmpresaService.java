package com.example.msvc_producto.domain.service;

import com.example.msvc_producto.domain.model.Empresa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EmpresaService {
    Empresa crearEmpresa(Empresa empresa);
    Empresa actualizarEmpresa(Long id, Empresa empresa);
    Empresa obtenerEmpresaPorId(Long id);
    List<Empresa> listarEmpresas();
    Page<Empresa> listarEmpresasPaginado(String q, Pageable pageable);
    void eliminarEmpresa(Long id);
}