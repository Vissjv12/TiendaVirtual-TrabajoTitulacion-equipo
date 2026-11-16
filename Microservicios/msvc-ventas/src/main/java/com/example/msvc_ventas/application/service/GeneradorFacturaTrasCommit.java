package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.domain.service.FacturaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A-05: genera la factura PDF solo después de que la transacción que completó la venta hizo commit.
 *
 * Reemplaza el patrón anterior (hilo manual con una espera de 100 ms antes de generar), que apostaba a que
 * 100 ms bastaban para el commit: si la transacción tardaba más o hacía rollback, la factura se generaba
 * con datos no confirmados o de una venta que no llegó a completarse.
 *
 * - Con transacción activa: se programa en afterCommit; si hay rollback no se genera nada.
 * - Sin transacción: se programa de inmediato.
 * - La generación corre en un ejecutor acotado propio (no bloquea la respuesta ni crea hilos sin límite).
 *   Si la cola se llena, la genera el hilo que llama (no se pierde).
 * - Un error al generar se registra y no afecta a la venta ya confirmada.
 */
@Component
@Slf4j
public class GeneradorFacturaTrasCommit implements DisposableBean {

    private final FacturaService facturaService;
    private final Executor executor;

    @Autowired
    public GeneradorFacturaTrasCommit(FacturaService facturaService) {
        this(facturaService, crearEjecutor());
    }

    /** Constructor para pruebas: permite un ejecutor síncrono. */
    public GeneradorFacturaTrasCommit(FacturaService facturaService, Executor executor) {
        this.facturaService = facturaService;
        this.executor = executor;
    }

    public void programar(Long ventaId) {
        Runnable encolar = () -> executor.execute(() -> generar(ventaId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    encolar.run();
                }
            });
        } else {
            encolar.run();
        }
    }

    private void generar(Long ventaId) {
        try {
            String ruta = facturaService.generarFacturaPDF(ventaId);
            log.info("Factura generada para la venta {}: {}", ventaId, ruta);
        } catch (Exception e) {
            log.error("Error al generar la factura de la venta {}", ventaId, e);
        }
    }

    private static ExecutorService crearEjecutor() {
        AtomicInteger contador = new AtomicInteger();
        return new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200), r -> {
            Thread hilo = new Thread(r, "factura-" + contador.incrementAndGet());
            hilo.setDaemon(true);
            return hilo;
        }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public void destroy() {
        if (executor instanceof ExecutorService servicio) {
            servicio.shutdown();
        }
    }
}
