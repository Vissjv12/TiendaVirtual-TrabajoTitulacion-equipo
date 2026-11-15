package com.example.msvc_ventas.domain.service;

import com.example.msvc_ventas.domain.model.Venta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface VentaService {
    Venta crearVenta(Venta venta);
    Venta actualizarVenta(Long id, Venta venta);
    Venta obtenerVentaPorId(Long id);
    Venta obtenerVentaPorNumeroFactura(String numeroFactura);
    List<Venta> listarVentasPorCliente(Long clienteId);
    List<Venta> listarVentasPorEmprendedor(Long emprendedorId);
    List<Venta> listarVentas();
    Page<Venta> listarVentasPaginado(String q, Venta.EstadoVenta estado, Pageable pageable);
    Page<Venta> listarVentasPorClientePaginado(Long clienteId, Pageable pageable);
    Page<Venta> listarVentasPorEmprendedorPaginado(Long emprendedorId, String q, Venta.EstadoVenta estado, Pageable pageable);
    Venta completarVenta(Long id);
    Venta cancelarVenta(Long id);
}