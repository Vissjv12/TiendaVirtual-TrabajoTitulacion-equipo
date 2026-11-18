package com.example.msvc_producto.application.service;

import com.example.msvc_producto.application.dto.VisitasEstadisticasDto;
import com.example.msvc_producto.infrastructure.persistence.entity.VisitaDiariaEntity;
import com.example.msvc_producto.infrastructure.persistence.repository.VisitaDiariaJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class VisitasService {

    private static final ZoneId ZONA_ECUADOR = ZoneId.of("America/Guayaquil");

    private final VisitaDiariaJpaRepository repository;
    private final Clock clock;

    @Autowired
    public VisitasService(VisitaDiariaJpaRepository repository) {
        this(repository, Clock.system(ZONA_ECUADOR));
    }

    public VisitasService(VisitaDiariaJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void registrarVisita() {
        repository.incrementar(hoy());
    }

    @Transactional(readOnly = true)
    public VisitasEstadisticasDto obtenerEstadisticas() {
        LocalDate fecha = hoy();
        long visitasHoy = repository.findById(fecha)
                .map(VisitaDiariaEntity::getCantidad)
                .orElse(0L);
        return new VisitasEstadisticasDto(repository.sumarVisitas(), visitasHoy, fecha);
    }

    private LocalDate hoy() {
        return LocalDate.now(clock);
    }
}
