package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.application.client.ProductoClient;
import com.example.msvc_ventas.application.client.ConfiguracionPagosClient;
import com.example.msvc_ventas.application.dto.ProductoDto;
import com.example.msvc_ventas.application.dto.VentaRequestDto;
import com.example.msvc_ventas.application.dto.VentaResponseDto;
import com.example.msvc_ventas.application.mapper.VentaMapper;
import com.example.msvc_ventas.domain.model.Cliente;
import com.example.msvc_ventas.domain.model.Venta;
import com.example.msvc_ventas.domain.repository.ClienteRepository;
import com.example.msvc_ventas.domain.service.VentaService;
import com.example.msvc_ventas.domain.service.PagoService;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VentaApplicationService {

    private final VentaService ventaService;
    private final VentaMapper ventaMapper;
    private final PagoService pagoService;
    private final ClienteRepository clienteRepository;
    private final ProductoClient productoClient;
    private final ConfiguracionPagosClient configuracionPagosClient;

    @Autowired
    public VentaApplicationService(
            VentaService ventaService,
            VentaMapper ventaMapper,
            PagoService pagoService,
            ClienteRepository clienteRepository,
            ProductoClient productoClient,
            ConfiguracionPagosClient configuracionPagosClient) {
        this.ventaService = ventaService;
        this.ventaMapper = ventaMapper;
        this.pagoService = pagoService;
        this.clienteRepository = clienteRepository;
        this.productoClient = productoClient;
        this.configuracionPagosClient = configuracionPagosClient;
    }

    @Transactional
    public VentaResponseDto crearVenta(VentaRequestDto requestDto) {
        log.info("🛒 Iniciando proceso de creación de venta en msvc-ventas...");
        log.info("👤 Cliente ID: {}", requestDto.getClienteId());
        log.info("🏢 Emprendedor ID recibido: {}", requestDto.getEmprendedorId());
        log.info("💳 Método de Pago: {}", requestDto.getMetodoPago());
        log.info("📦 Cantidad de items: {}", requestDto.getItems() != null ? requestDto.getItems().size() : 0);

        // 1. Obtener el cliente existente de la base de datos usando ClienteRepository
        log.info("🔍 Validando existencia del cliente...");
        Cliente cliente = clienteRepository.findById(requestDto.getClienteId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con ID: " + requestDto.getClienteId()));
        log.info("Cliente encontrado: {} - {}", cliente.getNombre(), cliente.getEmail());

        // 2. Crear ProductoDtos desde los items del request (sin llamadas HTTP)
        List<ProductoDto> productos = requestDto.getItems().stream()
                .map(item -> productoClient.obtenerProducto(item.getProductoId()))
                .collect(Collectors.toList());

        log.info("Productos mapeados: {}", productos.size());

        if (productos.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Uno o más productos no existen");
        }

        Long empresaId = productos.stream()
                .map(ProductoDto::getEmpresa)
                .filter(Objects::nonNull)
                .map(com.example.msvc_ventas.application.dto.EmpresaDto::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No se pudo determinar la empresa de los productos"));

        boolean mismaEmpresa = productos.stream()
                .allMatch(producto -> producto.getEmpresa() != null
                        && empresaId.equals(producto.getEmpresa().getId()));
        if (!mismaEmpresa) {
            throw new IllegalArgumentException("Los productos de una venta deben pertenecer a la misma empresa");
        }

        // La tarifa se obtiene del servicio propietario; el costo recibido del navegador se ignora.
        var costoEnvio = configuracionPagosClient.obtenerCostoEnvio(empresaId).getCostoEnvio();
        VentaRequestDto requestAutoritativo = VentaRequestDto.builder()
                .clienteId(requestDto.getClienteId())
                .items(requestDto.getItems())
                .metodoPago(requestDto.getMetodoPago())
                .comprobanteUrl(requestDto.getComprobanteUrl())
                .emprendedorId(empresaId)
                .costoEnvio(costoEnvio)
                .build();

        // 3. Mapear a entidad de dominio y crear la venta
        Venta venta = ventaMapper.toEntity(requestAutoritativo, cliente, productos);
        log.info("Venta mapeada, procediendo a guardar");

        Venta ventaCreada = ventaService.crearVenta(venta);
        log.info("✅ Venta guardada exitosamente en la base de datos con ID: {} y Factura: {}", 
                ventaCreada.getId(), ventaCreada.getNumeroFactura());
        log.info("Venta creada con ID: {}", ventaCreada.getId());

        // 5. Crear el pago de forma asíncrona (en segundo plano)
        if (requestDto.getMetodoPago() != null && !requestDto.getMetodoPago().isEmpty()) {
            try {
                PagoEntity.MetodoPago metodoPago = PagoEntity.MetodoPago.valueOf(requestDto.getMetodoPago().toUpperCase());
                // Crear pago de forma no bloqueante
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        pagoService.crearPago(ventaCreada.getId(), metodoPago, requestDto.getComprobanteUrl());
                        log.info("Pago creado en estado PENDIENTE para venta ID: {}", ventaCreada.getId());
                    } catch (Exception e) {
                        log.error("Error al crear pago para venta ID: {}", ventaCreada.getId(), e);
                    }
                }).start();
            } catch (IllegalArgumentException e) {
                log.warn("Método de pago inválido: {}, se creará la venta sin pago", requestDto.getMetodoPago());
            }
        }

        // 6. Mapear de vuelta a DTO para la respuesta
        return ventaMapper.toDto(ventaCreada);
    }
}
