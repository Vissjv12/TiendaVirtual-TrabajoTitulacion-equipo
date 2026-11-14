package com.example.msvc_producto.application.service;

import com.example.msvc_producto.domain.model.Categoria;
import com.example.msvc_producto.domain.repository.CategoriaRepository;
import com.example.msvc_producto.domain.service.CategoriaService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Autowired
    private ProductoCatalogoInvalidacionService invalidacionService;

    public CategoriaServiceImpl(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @Override
    @Transactional
    public Categoria crearCategoria(Categoria categoria) {
        categoria.setFechaCreacion(LocalDateTime.now());
        categoria.setFechaActualizacion(LocalDateTime.now());
        categoria.setActivo(true);
        Categoria categoriaGuardada = categoriaRepository.save(categoria);
        invalidarCatalogoDespuesDeCommit();
        return categoriaGuardada;
    }

    @Override
    @Transactional
    public Categoria actualizarCategoria(Long id, Categoria categoria) {
        Categoria categoriaExistente = obtenerCategoriaPorId(id);

        categoriaExistente.setNombre(categoria.getNombre());
        categoriaExistente.setDescripcion(categoria.getDescripcion());
        categoriaExistente.setActivo(categoria.getActivo());
        categoriaExistente.setFechaActualizacion(LocalDateTime.now());

        Categoria categoriaActualizada = categoriaRepository.save(categoriaExistente);
        invalidarCatalogoDespuesDeCommit();
        return categoriaActualizada;
    }

    @Override
    @Transactional(readOnly = true)
    public Categoria obtenerCategoriaPorId(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Categoría no encontrada con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Categoria> listarCategorias() {
        return categoriaRepository.findAll();
    }

    @Override
    @Transactional
    public void eliminarCategoria(Long id) {
        Categoria categoria = obtenerCategoriaPorId(id);
        categoria.setActivo(false);
        categoria.setFechaActualizacion(LocalDateTime.now());
        categoriaRepository.save(categoria);
        invalidarCatalogoDespuesDeCommit();
    }

    private void invalidarCatalogoDespuesDeCommit() {
        if (invalidacionService != null) {
            invalidacionService.invalidarDespuesDeCommit();
        }
    }
}
