package com.example.msvc_producto;

import com.example.msvc_producto.application.dto.VisitasEstadisticasDto;
import com.example.msvc_producto.application.service.VisitasService;
import com.example.msvc_producto.infrastructure.persistence.entity.VisitaDiariaEntity;
import com.example.msvc_producto.infrastructure.persistence.repository.VisitaDiariaJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitasServiceTest {

    private static final ZoneId ZONA_ECUADOR = ZoneId.of("America/Guayaquil");
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 17);

    @Mock
    private VisitaDiariaJpaRepository repository;

    @InjectMocks
    private VisitasService visitasService;

    @Test
    void registraLaVisitaEnLaFechaDeEcuador() {
        VisitasService service = new VisitasService(repository, relojFijo());

        service.registrarVisita();

        verify(repository).incrementar(FECHA);
    }

    @Test
    void devuelveTotalYVisitasDelDia() {
        VisitaDiariaEntity hoy = new VisitaDiariaEntity();
        hoy.setFecha(FECHA);
        hoy.setCantidad(7L);
        when(repository.findById(FECHA)).thenReturn(Optional.of(hoy));
        when(repository.sumarVisitas()).thenReturn(42L);

        VisitasService service = new VisitasService(repository, relojFijo());
        VisitasEstadisticasDto resultado = service.obtenerEstadisticas();

        assertEquals(42L, resultado.total());
        assertEquals(7L, resultado.hoy());
        assertEquals(FECHA, resultado.fecha());
    }

    private Clock relojFijo() {
        return Clock.fixed(
                Instant.parse("2026-11-17T15:00:00Z"),
                ZONA_ECUADOR);
    }
}
