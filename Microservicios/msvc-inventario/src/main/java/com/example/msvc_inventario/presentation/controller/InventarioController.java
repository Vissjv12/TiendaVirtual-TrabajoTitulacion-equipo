package com.example.msvc_inventario.presentation.controller;

import com.example.msvc_inventario.application.client.ProductoClient;
import com.example.msvc_inventario.application.dto.*;
import com.example.msvc_inventario.application.mapper.InventarioMapper;
import com.example.msvc_inventario.application.util.PaginationUtils;
import com.example.msvc_inventario.domain.model.Inventario;
import com.example.msvc_inventario.domain.repository.InventarioRepository;
import com.example.msvc_inventario.domain.service.InventarioService;
import feign.FeignException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(originPatterns = "*")
@RequestMapping("/api/inventarios")
@Tag(name = "Inventarios", description = "API para gestionar inventarios")
public class InventarioController {

    private final InventarioService inventarioService;
    private final InventarioMapper inventarioMapper;
    private final ProductoClient productoClient;
    private final InventarioRepository inventarioRepository;

    public InventarioController(InventarioService inventarioService,
                                InventarioMapper inventarioMapper,
                                @Qualifier("com.example.msvc_inventario.application.client.ProductoClient") ProductoClient productoClient,
                                InventarioRepository inventarioRepository) {
        this.inventarioService = inventarioService;
        this.inventarioMapper = inventarioMapper;
        this.productoClient = productoClient;
        this.inventarioRepository = inventarioRepository;
    }

    @PostMapping
    @Operation(summary = "Crear el inventario inicial de un producto (no sobrescribe stock existente)")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @inventarioAuthorizationService.canManageProduct(authentication, #requestDto.productoId))")
    public ResponseEntity<InventarioResponseDto> crearInventario(@Valid @RequestBody InventarioRequestDto requestDto) {
        Inventario inventario = inventarioMapper.toEntity(requestDto);
        Inventario inventarioGuardado = inventarioService.registrarInventario(inventario);

        // Obtener información del producto
        ProductoDto productoDto = null;
        try {
            productoDto = productoClient.obtenerProducto(inventarioGuardado.getProductoId());
        } catch (FeignException e) {
            // Si no se puede obtener el producto, continuamos con la información básica
        }

        InventarioResponseDto responseDto = (productoDto != null)
                ? inventarioMapper.toDto(inventarioGuardado, productoDto)
                : inventarioMapper.toDto(inventarioGuardado);

        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    @PostMapping("/producto/{productoId}")
    @Operation(summary = "Crear inventario para un producto específico")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @inventarioAuthorizationService.canManageProduct(authentication, #productoId))")
    public ResponseEntity<InventarioResponseDto> crearInventarioParaProducto(
            @PathVariable Long productoId,
            @RequestParam Integer stock) {

        InventarioRequestDto requestDto = new InventarioRequestDto();
        requestDto.setProductoId(productoId);
        requestDto.setCantidad(stock);
        requestDto.setUbicacion("Almacén principal"); // Valor por defecto

        return crearInventario(requestDto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un registro de inventario existente")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @inventarioAuthorizationService.canManageInventario(authentication, #id))")
    public ResponseEntity<InventarioResponseDto> actualizarInventario(
            @PathVariable Long id,
            @Valid @RequestBody InventarioRequestDto requestDto) {

        Inventario inventario = inventarioMapper.toEntity(requestDto);
        Inventario inventarioActualizado = inventarioService.actualizarInventario(id, inventario);

        // Obtener información del producto
        ProductoDto productoDto = null;
        try {
            productoDto = productoClient.obtenerProducto(inventarioActualizado.getProductoId());
        } catch (FeignException e) {
            // Si no se puede obtener el producto, continuamos con la información básica
        }

        InventarioResponseDto responseDto = (productoDto != null)
                ? inventarioMapper.toDto(inventarioActualizado, productoDto)
                : inventarioMapper.toDto(inventarioActualizado);

        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un registro de inventario por su ID")
    public ResponseEntity<InventarioResponseDto> obtenerInventarioPorId(@PathVariable Long id) {
        Inventario inventario = inventarioService.obtenerPorId(id);

        // Obtener información del producto
        ProductoDto productoDto = null;
        try {
            productoDto = productoClient.obtenerProducto(inventario.getProductoId());
        } catch (FeignException e) {
            // Si no se puede obtener el producto, continuamos con la información básica
        }

        InventarioResponseDto responseDto = (productoDto != null)
                ? inventarioMapper.toDto(inventario, productoDto)
                : inventarioMapper.toDto(inventario);

        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/producto/{productoId}")
    @Operation(summary = "Obtener el inventario por ID de producto")
    public ResponseEntity<InventarioResponseDto> obtenerInventarioPorProductoId(@PathVariable Long productoId) {
        Inventario inventario = inventarioService.obtenerPorProductoId(productoId);

        // Obtener información del producto
        ProductoDto productoDto = null;
        try {
            productoDto = productoClient.obtenerProducto(inventario.getProductoId());
        } catch (FeignException e) {
            // Si no se puede obtener el producto, continuamos con la información básica
        }

        InventarioResponseDto responseDto = (productoDto != null)
                ? inventarioMapper.toDto(inventario, productoDto)
                : inventarioMapper.toDto(inventario);

        return ResponseEntity.ok(responseDto);
    }

    private static final Map<String, String> INVENTARIO_SORT_FIELDS = Map.of(
            "id", "id",
            "productoId", "productoId",
            "cantidad", "cantidad",
            "ubicacion", "ubicacion",
            "activo", "activo",
            "fechaCreacion", "fechaCreacion",
            "fechaActualizacion", "fechaActualizacion"
    );

    @GetMapping("/paginado")
    @Operation(summary = "Listar registros de inventario paginados (contrato A-19)")
    public ResponseEntity<PageResponseDto<InventarioResponseDto>> listarInventariosPaginado(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "id,desc") String sort,
            @RequestParam(required = false) Boolean soloActivos) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "id", Sort.Direction.DESC, INVENTARIO_SORT_FIELDS);
        Page<Inventario> inventarios = inventarioService.listarPaginado(soloActivos, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(inventarios, this::toEnrichedDto));
    }

    @GetMapping
    @Operation(summary = "Listar todos los registros de inventario")
    public ResponseEntity<List<InventarioResponseDto>> listarInventarios() {
        List<Inventario> inventarios = inventarioService.listarTodos();

        List<InventarioResponseDto> responseDtos = inventarios.stream()
                .map(this::toEnrichedDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    private InventarioResponseDto toEnrichedDto(Inventario inventario) {
        ProductoDto productoDto = null;
        try {
            productoDto = productoClient.obtenerProducto(inventario.getProductoId());
        } catch (FeignException e) {
            // Si no se puede obtener el producto, continuamos con la información básica
        }
        return (productoDto != null)
                ? inventarioMapper.toDto(inventario, productoDto)
                : inventarioMapper.toDto(inventario);
    }

    @PutMapping("/producto/{productoId}/stock")
    @Operation(summary = "Actualizar el stock de un producto")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @inventarioAuthorizationService.canManageProduct(authentication, #productoId))")
    public ResponseEntity<InventarioResponseDto> actualizarStock(
            @PathVariable Long productoId,
            @RequestParam Integer cantidad,
            @RequestParam(defaultValue = "AJUSTE") String tipoMovimiento,
            @RequestParam(required = false) String motivo) {

        Inventario inventarioActualizado = inventarioService.actualizarStock(
                productoId, cantidad, tipoMovimiento, motivo);

        // Obtener información del producto
        ProductoDto productoDto = null;
        try {
            productoDto = productoClient.obtenerProducto(inventarioActualizado.getProductoId());
        } catch (FeignException e) {
            // Si no se puede obtener el producto, continuamos con la información básica
        }

        InventarioResponseDto responseDto = (productoDto != null)
                ? inventarioMapper.toDto(inventarioActualizado, productoDto)
                : inventarioMapper.toDto(inventarioActualizado);

        return ResponseEntity.ok(responseDto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un registro de inventario")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or (hasAuthority('ROLE_EMP') and @inventarioAuthorizationService.canManageInventario(authentication, #id))")
    public ResponseEntity<Void> eliminarInventario(@PathVariable Long id) {
        inventarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/salida-lote")
    @Operation(summary = "Procesar una salida de inventario en lote")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or @inventarioAuthorizationService.isServicioPagos(authentication) or (hasAuthority('ROLE_EMP') and @inventarioAuthorizationService.canManageSalidaLote(authentication, #requestDto))")
    public ResponseEntity<List<InventarioResponseDto>> procesarSalidaLote(
            @Valid @RequestBody SalidaInventarioLoteRequestDto requestDto) {

        List<Inventario> inventariosActualizados = inventarioService.procesarSalidaLote(
                requestDto.getItems(),
                requestDto.getMotivo()
        );

        List<InventarioResponseDto> responseDtos = inventariosActualizados.stream()
                .map(inventario -> {
                    // Intentar obtener información del producto
                    ProductoDto productoDto = null;
                    try {
                        productoDto = productoClient.obtenerProducto(inventario.getProductoId());
                    } catch (FeignException e) {
                        // Si no se puede obtener el producto, continuamos con la información básica
                    }

                    return (productoDto != null)
                            ? inventarioMapper.toDto(inventario, productoDto)
                            : inventarioMapper.toDto(inventario);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    // ==================== ENDPOINTS OPTIMIZADOS PARA STOCK ====================

    @GetMapping("/cantidad/producto/{productoId}")
    @Operation(summary = "Obtener SOLO la cantidad de stock de un producto")
    public ResponseEntity<Integer> obtenerCantidadProducto(@PathVariable Long productoId) {
        try {
            Optional<Inventario> inventario = inventarioRepository.findByProductoId(productoId);

            if (inventario.isPresent()) {
                Integer cantidad = inventario.get().getCantidad();
                return ResponseEntity.ok(cantidad != null ? cantidad : 0);
            } else {
                return ResponseEntity.ok(0);
            }
        } catch (Exception e) {
            return ResponseEntity.ok(0);
        }
    }

    @PostMapping("/cantidad/batch")
    @Operation(summary = "Obtener cantidades de múltiples productos - OPTIMIZADO")
    public ResponseEntity<Map<String, Integer>> obtenerCantidadesBatch(@RequestBody List<Long> productosIds) {
        Map<String, Integer> cantidades = new HashMap<>();

        try {
            if (productosIds == null || productosIds.isEmpty()) {
                return ResponseEntity.ok(cantidades);
            }

            // Una sola consulta para todos los productos
            List<Inventario> inventarios = inventarioRepository.findByProductoIdIn(productosIds);

            // Mapear resultados
            for (Inventario inventario : inventarios) {
                Integer cantidad = inventario.getCantidad() != null ? inventario.getCantidad() : 0;
                cantidades.put(inventario.getProductoId().toString(), cantidad);
            }

            // Para productos sin inventario, agregar 0
            for (Long productoId : productosIds) {
                String key = productoId.toString();
                if (!cantidades.containsKey(key)) {
                    cantidades.put(key, 0);
                }
            }

            return ResponseEntity.ok(cantidades);

        } catch (Exception e) {
            // En caso de error, devolver todos en 0
            for (Long productoId : productosIds) {
                cantidades.put(productoId.toString(), 0);
            }
            return ResponseEntity.ok(cantidades);
        }
    }
}