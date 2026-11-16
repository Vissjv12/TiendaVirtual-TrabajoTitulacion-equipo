package com.example.msvc_producto.presentation.controller;

import com.example.msvc_producto.application.dto.EmpresaContactoRequestDto;
import com.example.msvc_producto.application.dto.EmpresaRequestDto;
import com.example.msvc_producto.application.dto.EmpresaResponseDto;
import com.example.msvc_producto.application.dto.PageResponseDto;
import com.example.msvc_producto.application.mapper.EmpresaMapper;
import com.example.msvc_producto.application.service.ProductoAuthorizationService;
import com.example.msvc_producto.application.util.PaginationUtils;
import com.example.msvc_producto.domain.model.Empresa;
import com.example.msvc_producto.domain.service.EmpresaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/empresas")
@Tag(name = "Empresas", description = "API para gestionar empresas")
public class EmpresaController {

    private final EmpresaService empresaService;
    private final EmpresaMapper empresaMapper;
    private final ProductoAuthorizationService productoAuthorizationService;

    public EmpresaController(
            EmpresaService empresaService,
            EmpresaMapper empresaMapper,
            ProductoAuthorizationService productoAuthorizationService) {
        this.empresaService = empresaService;
        this.empresaMapper = empresaMapper;
        this.productoAuthorizationService = productoAuthorizationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Crear una nueva empresa")
    public ResponseEntity<EmpresaResponseDto> crearEmpresa(@Valid @RequestBody EmpresaRequestDto requestDto) {
        Empresa empresa = empresaMapper.toEntity(requestDto);
        Empresa empresaCreada = empresaService.crearEmpresa(empresa);
        return new ResponseEntity<>(empresaMapper.toDto(empresaCreada), HttpStatus.CREATED);
    }

    /**
     * Actualización ADMINISTRATIVA (A-02): solo ADMIN puede cambiar RUC y estado activo.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Actualizar una empresa (administrador: incluye RUC y estado)")
    public ResponseEntity<EmpresaResponseDto> actualizarEmpresa(
            @PathVariable Long id,
            @Valid @RequestBody EmpresaRequestDto requestDto) {
        Empresa empresa = empresaMapper.toEntity(requestDto, id);
        Empresa empresaActualizada = empresaService.actualizarEmpresa(id, empresa);
        return ResponseEntity.ok(empresaMapper.toDto(empresaActualizada));
    }

    /**
     * Actualización de datos de contacto por el EMPRENDEDOR dueño (o ADMIN) (A-02).
     * Solo nombre, dirección, teléfono y email; enviar "ruc" o "activo" responde 400.
     */
    @PutMapping("/{id}/contacto")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_EMP')")
    @Operation(summary = "Actualizar nombre y datos de contacto de una empresa (emprendedor dueño)")
    public ResponseEntity<EmpresaResponseDto> actualizarDatosContacto(
            @PathVariable Long id,
            @Valid @RequestBody EmpresaContactoRequestDto requestDto,
            Authentication authentication) {
        if (!productoAuthorizationService.canManageCompany(authentication, id)) {
            throw new AccessDeniedException("El usuario no puede operar sobre esta empresa");
        }
        Empresa empresaActualizada = empresaService.actualizarDatosContacto(id,
                requestDto.getNombre(), requestDto.getDireccion(), requestDto.getTelefono(), requestDto.getEmail());
        return ResponseEntity.ok(empresaMapper.toDto(empresaActualizada));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una empresa por su ID")
    public ResponseEntity<EmpresaResponseDto> obtenerEmpresaPorId(@PathVariable Long id) {
        Empresa empresa = empresaService.obtenerEmpresaPorId(id);
        return ResponseEntity.ok(empresaMapper.toDto(empresa));
    }

    private static final Map<String, String> EMPRESA_SORT_FIELDS = Map.of(
            "id", "id",
            "nombre", "nombre",
            "ruc", "ruc",
            "activo", "activo",
            "fechaCreacion", "fechaCreacion",
            "fechaActualizacion", "fechaActualizacion"
    );

    @GetMapping("/paginado")
    @Operation(summary = "Listar empresas paginadas (contrato A-19)")
    public ResponseEntity<PageResponseDto<EmpresaResponseDto>> listarEmpresasPaginado(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "id,desc") String sort,
            @RequestParam(required = false) String q) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "id", Sort.Direction.DESC, EMPRESA_SORT_FIELDS);
        Page<Empresa> result = empresaService.listarEmpresasPaginado(q, pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(result, empresaMapper::toDto));
    }

    @GetMapping
    @Operation(summary = "Listar todas las empresas")
    public ResponseEntity<List<EmpresaResponseDto>> listarEmpresas() {
        List<Empresa> empresas = empresaService.listarEmpresas();
        List<EmpresaResponseDto> responseDtos = empresas.stream()
                .map(empresaMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseDtos);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Eliminar una empresa")
    public ResponseEntity<Void> eliminarEmpresa(@PathVariable Long id) {
        empresaService.eliminarEmpresa(id);
        return ResponseEntity.noContent().build();
    }
}
