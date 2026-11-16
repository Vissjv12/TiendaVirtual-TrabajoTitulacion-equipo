package com.example.msvc_ventas.application.service;

import com.example.msvc_ventas.domain.service.FacturaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A-05: la factura se genera solo tras el commit; nunca con rollback y sin hilos con sleep. */
class GeneradorFacturaTrasCommitTest {

    private final FacturaService facturaService = mock(FacturaService.class);
    // Ejecutor síncrono: la prueba observa la tarea sin esperar
    private final GeneradorFacturaTrasCommit generador = new GeneradorFacturaTrasCommit(facturaService, Runnable::run);

    @AfterEach
    void limpiar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void conTransaccionNoSeGeneraHastaElCommit() {
        TransactionSynchronizationManager.initSynchronization();

        generador.programar(7L);
        verify(facturaService, never()).generarFacturaPDF(7L);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(facturaService).generarFacturaPDF(7L);
    }

    @Test
    void conRollbackNoSeGeneraFactura() {
        TransactionSynchronizationManager.initSynchronization();

        generador.programar(7L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(facturaService, never()).generarFacturaPDF(7L);
    }

    @Test
    void sinTransaccionSeGeneraDeInmediato() {
        generador.programar(8L);
        verify(facturaService).generarFacturaPDF(8L);
    }

    @Test
    void unErrorAlGenerarNoSePropaga() {
        when(facturaService.generarFacturaPDF(9L)).thenThrow(new IllegalStateException("disco lleno"));
        assertDoesNotThrow(() -> generador.programar(9L));
    }
}
