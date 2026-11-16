package com.example.msvc_producto.presentation.controller;

import com.example.msvc_producto.application.client.InventarioClient;
import com.example.msvc_producto.application.dto.InventarioInfoDto;
import com.example.msvc_producto.application.dto.PageResponseDto;
import com.example.msvc_producto.application.dto.ProductoListadoDto;
import com.example.msvc_producto.application.dto.ProductoRequestDto;
import com.example.msvc_producto.application.dto.ProductoResponseDto;
import com.example.msvc_producto.application.mapper.ProductoMapper;
import com.example.msvc_producto.application.util.PaginationUtils;
import com.example.msvc_producto.domain.model.Categoria;
import com.example.msvc_producto.domain.model.Empresa;
import com.example.msvc_producto.domain.model.Producto;
import com.example.msvc_producto.domain.service.CategoriaService;
import com.example.msvc_producto.domain.service.EmpresaService;
import com.example.msvc_producto.domain.service.ProductoService;
import com.example.msvc_producto.application.service.ProductoAuthorizationService;
import feign.FeignException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/productos")
@Tag(name = "Productos", description = "API para gestionar productos")
public class ProductoController {

    private final ProductoService productoService;
    private final EmpresaService empresaService;
    private final CategoriaService categoriaService;
    private final ProductoMapper productoMapper;
    private final InventarioClient inventarioClient;
    private final ProductoAuthorizationService productoAuthorizationService;

    public ProductoController(
            ProductoService productoService,
            EmpresaService empresaService,
            CategoriaService categoriaService,
            ProductoMapper productoMapper,
            @Qualifier("com.example.msvc_producto.application.client.InventarioClient") InventarioClient inventarioClient,
            ProductoAuthorizationService productoAuthorizationService) {
        this.productoService = productoService;
        this.empresaService = empresaService;
        this.categoriaService = categoriaService;
        this.productoMapper = productoMapper;
        this.inventarioClient = inventarioClient;
        this.productoAuthorizationService = productoAuthorizationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP')")
    @Operation(summary = "Crear un nuevo producto")
    public ResponseEntity<ProductoResponseDto> crearProducto(
            @Valid @RequestBody ProductoRequestDto requestDto,
            Authentication authentication) {
        assertCompanyAccess(authentication, requestDto.getEmpresaId());
        applyAuthenticatedEntrepreneurId(authentication, requestDto);
        // Obtener empresa y categoría
        Empresa empresa = empresaService.obtenerEmpresaPorId(requestDto.getEmpresaId());
        Categoria categoria = categoriaService.obtenerCategoriaPorId(requestDto.getCategoriaId());

        // Crear producto
        Producto producto = productoMapper.toEntity(requestDto, empresa, categoria);
        Producto productoCreado = productoService.crearProducto(producto);

        // Obtener DTO de respuesta
        ProductoResponseDto responseDto = productoMapper.toDto(productoCreado);

        // Intentar obtener información de inventario
        // ✅ Evitamos loop infinito (circular dependency) al no consultar inventario aquí
        /*
        try {
            InventarioInfoDto inventarioInfo = inventarioClient.obtenerInventarioPorProductoId(productoCreado.getId());
            responseDto.setInventario(inventarioInfo);
        } catch (Exception e) {
            // Si no se puede obtener información de inventario, no hacer nada
        }
        */

        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP')")
    @Operation(summary = "Actualizar un producto existente")
    public ResponseEntity<ProductoResponseDto> actualizarProducto(
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequestDto requestDto,
            Authentication authentication) {
        assertProductAccess(authentication, id);
        applyAuthenticatedEntrepreneurId(authentication, requestDto);
        // Obtener empresa y categoría
        Empresa empresa = empresaService.obtenerEmpresaPorId(requestDto.getEmpresaId());
        Categoria categoria = categoriaService.obtenerCategoriaPorId(requestDto.getCategoriaId());

        // Actualizar producto
        Producto producto = productoMapper.toEntity(requestDto, id, empresa, categoria);
        Producto productoActualizado = productoService.actualizarProducto(id, producto);

        // Obtener DTO de respuesta
        ProductoResponseDto responseDto = productoMapper.toDto(productoActualizado);

        // Intentar obtener información de inventario
        // ✅ Evitamos loop infinito (circular dependency)
        /*
        try {
            InventarioInfoDto inventarioInfo = inventarioClient.obtenerInventarioPorProductoId(productoActualizado.getId());
            responseDto.setInventario(inventarioInfo);
        } catch (Exception e) {
            // Si no se puede obtener información de inventario, no hacer nada
        }
        */

        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un producto por su ID")
    public ResponseEntity<ProductoResponseDto> obtenerProductoPorId(@PathVariable Long id) {
        Producto producto = productoService.obtenerProductoPorId(id);
        ProductoResponseDto responseDto = productoMapper.toDto(producto);

        // Intentar obtener información de inventario
        // ✅ Evitamos loop infinito (circular dependency)
        /*
        try {
            InventarioInfoDto inventarioInfo = inventarioClient.obtenerInventarioPorProductoId(producto.getId());
            responseDto.setInventario(inventarioInfo);
        } catch (Exception e) {
            // Si no se puede obtener información de inventario, no hacer nada
        }
        */

        return ResponseEntity.ok(responseDto);
    }

    @GetMapping
    @Operation(summary = "Listar todos los productos")
    public ResponseEntity<List<ProductoResponseDto>> listarProductos() {
        List<Producto> productos = productoService.listarProductos();
        List<ProductoResponseDto> responseDtos = productos.stream()
                .map(producto -> {
                    ProductoResponseDto dto = productoMapper.toDto(producto);

                    // ✅ TEMPORAL: Comentado para evitar N+1 con inventario
                    // El cliente de inventario probablemente está causando un loop de llamadas
                    /*
                    try {
                        InventarioInfoDto inventarioInfo = inventarioClient.obtenerInventarioPorProductoId(producto.getId());
                        dto.setInventario(inventarioInfo);
                    } catch (FeignException e) {
                        // Si no se puede obtener información de inventario, dejamos el inventario como null
                    }
                    */

                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }


    @GetMapping("/listado")
    @Operation(summary = "Listar productos optimizado (solo datos esenciales)")
    public ResponseEntity<List<ProductoListadoDto>> listarProductosOptimizado() {
        try {
            List<ProductoListadoDto> productos = productoService.obtenerProductosOptimizado();

            // ✅ SIN LOGGER - usar System.out temporalmente
            System.out.println("Productos optimizados obtenidos: " + productos.size());
            return ResponseEntity.ok(productos);

        } catch (Exception e) {
            System.err.println("Error obteniendo productos optimizados: " + e.getMessage());
            e.printStackTrace(); // Para ver el error completo

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ArrayList<>());
        }
    }

    private static final Map<String, String> PRODUCTO_SORT_FIELDS = Map.of(
            "id", "id",
            "nombre", "nombre",
            "precio", "precio",
            "activo", "activo",
            "fechaCreacion", "fechaCreacion",
            "fechaActualizacion", "fechaActualizacion"
    );

    private static final Map<String, String> LISTADO_SORT_FIELDS = Map.of(
            "id", "id",
            "nombre", "nombre",
            "precio", "precio",
            "fechaCreacion", "fechaCreacion"
    );

    @GetMapping("/paginado")
    @Operation(summary = "Listar productos paginados (contrato A-19)")
    public ResponseEntity<PageResponseDto<ProductoResponseDto>> listarProductosPaginado(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "id,desc") String sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Boolean activo) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "id", Sort.Direction.DESC, PRODUCTO_SORT_FIELDS);
        Page<Producto> result = productoService.listarProductosPaginado(
                q, empresaId, categoriaId, activo, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(result, productoMapper::toDto));
    }

    @GetMapping("/listado/paginado")
    @Operation(summary = "Listar productos públicos paginados para vitrina (contrato A-19)")
    public ResponseEntity<PageResponseDto<ProductoListadoDto>> listarProductosOptimizadoPaginado(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            @RequestParam(defaultValue = "nombre,asc") String sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String empresa) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_STOREFRONT_SIZE,
                "nombre", Sort.Direction.ASC, LISTADO_SORT_FIELDS);
        Page<ProductoListadoDto> result = productoService.obtenerProductosOptimizadoPaginado(
                q, categoria, empresa, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(result));
    }

    @GetMapping("/empresa/{empresaId}/paginado")
    @Operation(summary = "Buscar productos por empresa paginados (contrato A-19)")
    public ResponseEntity<PageResponseDto<ProductoResponseDto>> buscarProductosPorEmpresaPaginado(
            @PathVariable Long empresaId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "id,desc") String sort,
            @RequestParam(required = false) String q) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "id", Sort.Direction.DESC, PRODUCTO_SORT_FIELDS);
        Page<Producto> result = productoService.listarProductosPaginado(
                q, empresaId, null, null, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(result, productoMapper::toDto));
    }

    @GetMapping("/categoria/{categoriaId}/paginado")
    @Operation(summary = "Buscar productos por categoría paginados (contrato A-19)")
    public ResponseEntity<PageResponseDto<ProductoResponseDto>> buscarProductosPorCategoriaPaginado(
            @PathVariable Long categoriaId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "id,desc") String sort) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "id", Sort.Direction.DESC, PRODUCTO_SORT_FIELDS);
        Page<Producto> result = productoService.listarProductosPaginado(
                null, null, categoriaId, null, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(result, productoMapper::toDto));
    }

    @GetMapping("/empresa/{empresaId}")
    @Operation(summary = "Buscar productos por empresa")
    public ResponseEntity<List<ProductoResponseDto>> buscarProductosPorEmpresa(@PathVariable Long empresaId) {
        List<Producto> productos = productoService.buscarProductosPorEmpresa(empresaId);
        List<ProductoResponseDto> responseDtos = productos.stream()
                .map(productoMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseDtos);
    }

    @GetMapping("/categoria/{categoriaId}")
    @Operation(summary = "Buscar productos por categoría")
    public ResponseEntity<List<ProductoResponseDto>> buscarProductosPorCategoria(@PathVariable Long categoriaId) {
        List<Producto> productos = productoService.buscarProductosPorCategoria(categoriaId);
        List<ProductoResponseDto> responseDtos = productos.stream()
                .map(productoMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseDtos);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP')")
    @Operation(summary = "Eliminar un producto")
    public ResponseEntity<Void> eliminarProducto(
            @PathVariable Long id,
            Authentication authentication) {
        assertProductAccess(authentication, id);
        productoService.eliminarProducto(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/sincronizar-stock")
    @Operation(summary = "Sincronizar stock desde inventario")
    public ResponseEntity<Void> sincronizarStock(
            @PathVariable Long id,
            @RequestParam Integer stock) {
        // Este endpoint es solo para recibir notificaciones del inventario
        // Podrías guardar el stock en caché, logs, o simplemente confirmarlo

        // Verificar que el producto existe
        productoService.obtenerProductoPorId(id);

        // Log para auditoría
        System.out.println("Stock sincronizado para producto " + id + ": " + stock);

        return ResponseEntity.ok().build();
    }

    private void assertCompanyAccess(Authentication authentication, Long empresaId) {
        if (!productoAuthorizationService.canManageCompany(authentication, empresaId)) {
            throw new AccessDeniedException("El usuario no puede operar sobre esta empresa");
        }
    }

    private void assertProductAccess(Authentication authentication, Long productoId) {
        if (!productoAuthorizationService.canManageProduct(authentication, productoId)) {
            throw new AccessDeniedException("El usuario no puede operar sobre este producto");
        }
    }

    private void applyAuthenticatedEntrepreneurId(
            Authentication authentication,
            ProductoRequestDto requestDto) {
        if (!hasAuthority(authentication, "ROLE_EMP")) {
            return;
        }

        Long userId = productoAuthorizationService.getAuthenticatedUserId(authentication)
                .orElseThrow(() -> new AccessDeniedException(
                        "El token del emprendedor no contiene userId"));
        requestDto.setEmprendedorId(userId);
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

}
