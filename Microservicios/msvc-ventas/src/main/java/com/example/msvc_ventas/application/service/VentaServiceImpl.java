package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.application.client.InventarioClient;
import com.example.msvc_ventas.application.client.ProductoClient;
import com.example.msvc_ventas.application.dto.InventarioInfoDto;
import com.example.msvc_ventas.application.dto.ProductoDto;
import com.example.msvc_ventas.application.dto.SalidaInventarioLoteRequestDto;
import com.example.msvc_ventas.domain.model.Cliente;
import com.example.msvc_ventas.domain.model.DetalleVenta;
import com.example.msvc_ventas.domain.model.Venta;
import com.example.msvc_ventas.domain.repository.DetalleVentaRepository;
import com.example.msvc_ventas.domain.repository.VentaRepository;
import com.example.msvc_ventas.domain.service.ClienteService;
import com.example.msvc_ventas.domain.service.FacturaService;
import com.example.msvc_ventas.domain.service.SeguimientoLogisticaService;
import com.example.msvc_ventas.domain.service.VentaService;
import com.example.msvc_ventas.infrastructure.persistence.entity.PagoEntity;
import com.example.msvc_ventas.infrastructure.persistence.repository.PagoRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VentaServiceImpl implements VentaService {

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final ClienteService clienteService;
    private final PagoRepository pagoRepository;
    private final SeguimientoLogisticaService seguimientoLogisticaService;
    private final FacturaService facturaService;
    @Qualifier("com.example.msvc_ventas.application.client.ProductoClient")
    private final ProductoClient productoClient;
    @Qualifier("com.example.msvc_ventas.application.client.InventarioClient")
    private final InventarioClient inventarioClient;

    @Override
    @Transactional
    public Venta crearVenta(Venta venta) {
        // Establecer fechas y estado inicial
        LocalDateTime ahora = LocalDateTime.now();
        venta.setFechaVenta(ahora);
        venta.setFechaCreacion(ahora);
        venta.setFechaActualizacion(ahora);
        venta.setEstado(Venta.EstadoVenta.PENDIENTE);
        
        if (venta.getEstadoPago() == null) {
            venta.setEstadoPago(Venta.EstadoPago.PENDIENTE);
        }
        
        if (venta.getMetodoPago() == null) {
            venta.setMetodoPago(Venta.MetodoPago.TRANSFERENCIA);
        }

        // Guardar la venta para obtener el ID
        Venta ventaGuardada = ventaRepository.save(venta);

        // Procesar cada detalle de la venta
        List<DetalleVenta> detallesActualizados = new ArrayList<>();
        for (DetalleVenta detalle : venta.getDetalles()) {
            detalle.setVentaId(ventaGuardada.getId());
            DetalleVenta detalleGuardado = detalleVentaRepository.save(detalle);
            detallesActualizados.add(detalleGuardado);
        }

        ventaGuardada.setDetalles(detallesActualizados);
        return ventaGuardada;
    }

    @Override
    @Transactional
    public Venta actualizarVenta(Long id, Venta venta) {
        Venta ventaExistente = obtenerVentaPorId(id);

        // Solo permitir actualizar ventas pendientes
        if (ventaExistente.getEstado() != Venta.EstadoVenta.PENDIENTE) {
            throw new IllegalStateException("No se puede actualizar una venta que no está en estado PENDIENTE");
        }

        // Actualizar campos básicos
        List<DetalleVenta> detalles = ventaExistente.getDetalles();
        ventaExistente.setFechaActualizacion(LocalDateTime.now());

        Venta ventaGuardada = ventaRepository.save(ventaExistente);
        ventaGuardada.setDetalles(detalles);
        return ventaGuardada;
    }

    @Override
    @Transactional(readOnly = true)
    public Venta obtenerVentaPorId(Long id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Venta no encontrada con ID: " + id));

        // Cargar los detalles de la venta
        List<DetalleVenta> detalles = detalleVentaRepository.findByVentaId(venta.getId());
        venta.setDetalles(detalles);

        return venta;
    }

    @Override
    @Transactional(readOnly = true)
    public Venta obtenerVentaPorNumeroFactura(String numeroFactura) {
        Venta venta = ventaRepository.findByNumeroFactura(numeroFactura)
                .orElseThrow(() -> new NoSuchElementException("Venta no encontrada con número de factura: " + numeroFactura));

        // Cargar los detalles de la venta
        List<DetalleVenta> detalles = detalleVentaRepository.findByVentaId(venta.getId());
        venta.setDetalles(detalles);

        return venta;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Venta> listarVentasPorCliente(Long clienteId) {
        // Verificar que el cliente existe
        clienteService.obtenerClientePorId(clienteId);

        List<Venta> ventas = ventaRepository.findByClienteId(clienteId);

        // Cargar los detalles de cada venta
        cargarDetallesLista(ventas);

        return ventas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Venta> listarVentasPorEmprendedor(Long emprendedorId) {
        List<Venta> ventas = ventaRepository.findByEmprendedorId(emprendedorId);

        // Cargar los detalles de cada venta
        cargarDetallesLista(ventas);

        return ventas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Venta> listarVentas() {
        List<Venta> ventas = ventaRepository.findAll();

        // Cargar los detalles de cada venta
        cargarDetallesLista(ventas);

        return ventas;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Venta> listarVentasPaginado(String q, Venta.EstadoVenta estado, Pageable pageable) {
        Page<Venta> ventas = ventaRepository.findPaginado(q, null, null, estado, pageable);
        cargarDetallesLista(ventas.getContent());
        return ventas;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Venta> listarVentasPorClientePaginado(Long clienteId, Pageable pageable) {
        clienteService.obtenerClientePorId(clienteId);
        Page<Venta> ventas = ventaRepository.findPaginado("", null, clienteId, null, pageable);
        cargarDetallesLista(ventas.getContent());
        return ventas;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Venta> listarVentasPorEmprendedorPaginado(
            Long emprendedorId,
            String q,
            Venta.EstadoVenta estado,
            Pageable pageable) {
        Page<Venta> ventas = ventaRepository.findPaginado(q, emprendedorId, null, estado, pageable);
        cargarDetallesLista(ventas.getContent());
        return ventas;
    }

    private void cargarDetallesLista(List<Venta> ventas) {
        for (Venta venta : ventas) {
            List<DetalleVenta> detalles = detalleVentaRepository.findByVentaId(venta.getId());
            venta.setDetalles(detalles);
        }
    }

    @Override
    @Transactional
    public Venta completarVenta(Long id) {
        Venta venta = obtenerVentaPorId(id);

        // Verificar que la venta está en estado pendiente
        if (venta.getEstado() != Venta.EstadoVenta.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden completar ventas en estado PENDIENTE");
        }

        // Obtener los detalles directamente del repositorio para evitar el error
        List<DetalleVenta> detalles = detalleVentaRepository.findByVentaId(id);
        if (detalles == null || detalles.isEmpty()) {
            throw new IllegalStateException("La venta no tiene detalles asociados, no se puede completar");
        }

        // Actualizar el inventario (restar stock)
        try {
            // Preparar los items para la salida de inventario usando los detalles obtenidos directamente
            List<SalidaInventarioLoteRequestDto.SalidaInventarioItemDto> items = detalles.stream()
                    .map(detalle -> {
                        SalidaInventarioLoteRequestDto.SalidaInventarioItemDto item = new SalidaInventarioLoteRequestDto.SalidaInventarioItemDto();
                        item.setProductoId(detalle.getProductoId());
                        item.setCantidad(detalle.getCantidad());
                        return item;
                    })
                    .collect(Collectors.toList());

            // Crear el DTO para la salida de inventario
            SalidaInventarioLoteRequestDto salidaDto = new SalidaInventarioLoteRequestDto();
            salidaDto.setItems(items);
            salidaDto.setMotivo("Venta #" + venta.getNumeroFactura());

            // Llamar al microservicio de inventario
            inventarioClient.procesarSalidaLote(salidaDto);
        } catch (FeignException e) {
            throw new RuntimeException("Error al actualizar el inventario: " + e.getMessage(), e);
        }

        // Actualizar el estado de la venta y su estado de pago asociado
        LocalDateTime ahora = LocalDateTime.now();
        venta.setEstado(Venta.EstadoVenta.COMPLETADA);
        venta.setEstadoPago(Venta.EstadoPago.APROBADO);
        if (venta.getFechaPago() == null) {
            venta.setFechaPago(ahora);
        }
        venta.setFechaActualizacion(ahora);

        Venta ventaGuardada = ventaRepository.save(venta);
        ventaGuardada.setDetalles(detalles);

        // Sincronizar el pago asociado si existe en estado PENDIENTE
        pagoRepository.findByVentaId(id).ifPresent(pago -> {
            if (pago.getEstadoPago() == PagoEntity.EstadoPago.PENDIENTE) {
                pago.setEstadoPago(PagoEntity.EstadoPago.APROBADO);
                pago.setFechaPago(ahora);
                pago.setFechaActualizacion(ahora);
                pagoRepository.save(pago);
                log.info("Pago ID {} sincronizado a APROBADO al completar venta ID {}", pago.getId(), id);
            }
        });

        // Inicializar seguimiento logístico si aún no existe
        try {
            seguimientoLogisticaService.inicializarSeguimientoPorPagoAprobado(
                    ventaGuardada.getId(),
                    "Emprendedor ID: " + ventaGuardada.getEmprendedorId()
            );
        } catch (Exception e) {
            log.error("Error al inicializar seguimiento logístico al completar venta ID: {}", id, e);
        }

        // Generar factura PDF en segundo plano sin bloquear la respuesta
        final Long ventaIdFinal = ventaGuardada.getId();
        new Thread(() -> {
            try {
                Thread.sleep(100);
                facturaService.generarFacturaPDF(ventaIdFinal);
            } catch (Exception e) {
                log.error("Error al generar factura PDF al completar venta ID: {}", ventaIdFinal, e);
            }
        }).start();

        return ventaGuardada;
    }

    @Override
    @Transactional
    public Venta cancelarVenta(Long id) {
        Venta venta = obtenerVentaPorId(id);

        // Solo se pueden cancelar ventas pendientes
        if (venta.getEstado() != Venta.EstadoVenta.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden cancelar ventas en estado PENDIENTE");
        }

        List<DetalleVenta> detalles = detalleVentaRepository.findByVentaId(id);

        // Actualizar el estado de la venta y de pago asociado
        LocalDateTime ahora = LocalDateTime.now();
        venta.setEstado(Venta.EstadoVenta.CANCELADA);
        if (venta.getEstadoPago() == Venta.EstadoPago.PENDIENTE) {
            venta.setEstadoPago(Venta.EstadoPago.RECHAZADO);
        }
        venta.setFechaActualizacion(ahora);

        Venta ventaGuardada = ventaRepository.save(venta);
        ventaGuardada.setDetalles(detalles);

        // Sincronizar el pago asociado si existe en estado PENDIENTE
        pagoRepository.findByVentaId(id).ifPresent(pago -> {
            if (pago.getEstadoPago() == PagoEntity.EstadoPago.PENDIENTE) {
                pago.setEstadoPago(PagoEntity.EstadoPago.RECHAZADO);
                pago.setFechaActualizacion(ahora);
                pagoRepository.save(pago);
                log.info("Pago ID {} sincronizado a RECHAZADO al cancelar venta ID {}", pago.getId(), id);
            }
        });

        return ventaGuardada;
    }
}