package com.example.msvc_ventas.presentation.controller;

import com.example.msvc_ventas.application.dto.ClienteRequestDto;
import com.example.msvc_ventas.application.dto.ClienteResponseDto;
import com.example.msvc_ventas.application.dto.PageResponseDto;
import com.example.msvc_ventas.application.mapper.ClienteMapper;
import com.example.msvc_ventas.application.util.PaginationUtils;
import com.example.msvc_ventas.domain.model.Cliente;
import com.example.msvc_ventas.domain.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(originPatterns = "*")
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "API para gestionar clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final ClienteMapper clienteMapper;

    @PostMapping
    @Operation(summary = "Crear un nuevo cliente")
    @PreAuthorize("@ventaAuthorizationService.canCreateClienteWithEmail(authentication, #requestDto.email)")
    public ResponseEntity<ClienteResponseDto> crearCliente(@Valid @RequestBody ClienteRequestDto requestDto) {
        Cliente cliente = clienteMapper.toEntity(requestDto);
        Cliente clienteCreado = clienteService.crearCliente(cliente);
        return new ResponseEntity<>(clienteMapper.toDto(clienteCreado), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un cliente existente")
    @PreAuthorize("@ventaAuthorizationService.canAccessCliente(authentication, #id) and @ventaAuthorizationService.canCreateClienteWithEmail(authentication, #requestDto.email)")
    public ResponseEntity<ClienteResponseDto> actualizarCliente(
            @PathVariable Long id,
            @Valid @RequestBody ClienteRequestDto requestDto) {
        Cliente cliente = clienteMapper.toEntity(requestDto);
        Cliente clienteActualizado = clienteService.actualizarCliente(id, cliente);
        return ResponseEntity.ok(clienteMapper.toDto(clienteActualizado));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un cliente por su ID")
    @PreAuthorize("@ventaAuthorizationService.canAccessCliente(authentication, #id)")
    public ResponseEntity<ClienteResponseDto> obtenerCliente(@PathVariable Long id) {
        Cliente cliente = clienteService.obtenerClientePorId(id);
        return ResponseEntity.ok(clienteMapper.toDto(cliente));
    }

    @GetMapping("/email/{email}")
    @Operation(summary = "Obtener un cliente por su email")
    @PreAuthorize("@ventaAuthorizationService.canAccessClienteByEmail(authentication, #email)")
    public ResponseEntity<ClienteResponseDto> obtenerClientePorEmail(@PathVariable String email) {
        Cliente cliente = clienteService.obtenerClientePorEmail(email);
        return ResponseEntity.ok(clienteMapper.toDto(cliente));
    }

    private static final Map<String, String> CLIENTE_SORT_FIELDS = Map.of(
            "id", "id",
            "nombre", "nombre",
            "apellido", "apellido",
            "email", "email",
            "fechaCreacion", "fechaCreacion"
    );

    @GetMapping("/paginado")
    @Operation(summary = "Listar clientes paginados (contrato A-19)")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PageResponseDto<ClienteResponseDto>> listarClientesPaginado(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "id,desc") String sort) {
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sort,
                PaginationUtils.DEFAULT_MANAGEMENT_SIZE,
                "id", Sort.Direction.DESC, CLIENTE_SORT_FIELDS);
        Page<Cliente> clientes = clienteService.listarClientesPaginado(pageable);
        return ResponseEntity.ok(PageResponseDto.fromPage(clientes, clienteMapper::toDto));
    }

    @GetMapping
    @Operation(summary = "Listar todos los clientes")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<ClienteResponseDto>> listarClientes() {
        List<Cliente> clientes = clienteService.listarClientes();
        List<ClienteResponseDto> clientesDto = clientes.stream()
                .map(clienteMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(clientesDto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un cliente (desactivación lógica)")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> eliminarCliente(@PathVariable Long id) {
        clienteService.eliminarCliente(id);
        return ResponseEntity.noContent().build();
    }
}