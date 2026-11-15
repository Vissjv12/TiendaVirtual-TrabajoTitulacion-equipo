package com.example.msvc_producto.presentation.controller;

import com.example.msvc_producto.application.dto.CategoriaRequestDto;
import com.example.msvc_producto.application.dto.CategoriaResponseDto;
import com.example.msvc_producto.application.dto.PageResponseDto;
import com.example.msvc_producto.application.mapper.CategoriaMapper;
import com.example.msvc_producto.application.service.CategoriaCatalogoQueryService;
import com.example.msvc_producto.application.util.PaginationUtils;
import com.example.msvc_producto.domain.model.Categoria;
import com.example.msvc_producto.domain.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categorias")
@Tag(name = "Categorías", description = "API para gestionar categorías de productos")
@CrossOrigin(originPatterns = "*")
public class CategoriaController {

    @Autowired
    private CategoriaService categoriaService;
    @Autowired
    private CategoriaMapper categoriaMapper;
    @Autowired
    private CategoriaCatalogoQueryService categoriaCatalogoQueryService;


    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP')")
    @Operation(summary = "Crear una nueva categoría")
    public ResponseEntity<CategoriaResponseDto> crearCategoria(@Valid @RequestBody CategoriaRequestDto requestDto) {
        Categoria categoria = categoriaMapper.toEntity(requestDto);
        Categoria categoriaCreada = categoriaService.crearCategoria(categoria);
        return new ResponseEntity<>(categoriaMapper.toDto(categoriaCreada), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Actualizar una categoría existente")
    public ResponseEntity<CategoriaResponseDto> actualizarCategoria(
            @PathVariable Long id,
            @Valid @RequestBody CategoriaRequestDto requestDto) {
        Categoria categoria = categoriaMapper.toEntity(requestDto, id);
        Categoria categoriaActualizada = categoriaService.actualizarCategoria(id, categoria);
        return ResponseEntity.ok(categoriaMapper.toDto(categoriaActualizada));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una categoría por su ID")
    public ResponseEntity<CategoriaResponseDto> obtenerCategoriaPorId(@PathVariable Long id) {
        return ResponseEntity.ok(categoriaCatalogoQueryService.obtenerCategoriaPorId(id));
    }

    private static final Map<String, String> CATEGORIA_SORT_FIELDS = Map.of(
            "id", "id",
            "nombre", "nombre",
            "activo", "activo",
            "fechaCreacion", "fechaCreacion",
            "fechaActualizacion", "fechaActualizacion"
    );

    @GetMapping
    @Operation(summary = "Listar todas las categorías")
    public ResponseEntity<List<CategoriaResponseDto>> listarCategorias() {
        return ResponseEntity.ok(categoriaCatalogoQueryService.listarCategorias());
    }

    @GetMapping("/paginado")
    @Operation(summary = "Listar categorías paginadas (contrato A-19)")
    public ResponseEntity<PageResponseDto<CategoriaResponseDto>> listarCategoriasPaginado(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "id,desc") String sort,
            @RequestParam(required = false) String q) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "id", Sort.Direction.DESC, CATEGORIA_SORT_FIELDS);
        Page<Categoria> result = categoriaService.listarCategoriasPaginado(q, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(result, categoriaMapper::toDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Eliminar una categoría")
    public ResponseEntity<Void> eliminarCategoria(@PathVariable Long id) {
        categoriaService.eliminarCategoria(id);
        return ResponseEntity.noContent().build();
    }
}
