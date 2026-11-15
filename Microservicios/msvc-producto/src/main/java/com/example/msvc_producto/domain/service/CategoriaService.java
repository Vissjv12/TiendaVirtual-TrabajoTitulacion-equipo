package com.example.msvc_producto.domain.service;

import com.example.msvc_producto.domain.model.Categoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CategoriaService {
    Categoria crearCategoria(Categoria categoria);
    Categoria actualizarCategoria(Long id, Categoria categoria);
    Categoria obtenerCategoriaPorId(Long id);
    List<Categoria> listarCategorias();
    Page<Categoria> listarCategoriasPaginado(String q, Pageable pageable);
    void eliminarCategoria(Long id);
}