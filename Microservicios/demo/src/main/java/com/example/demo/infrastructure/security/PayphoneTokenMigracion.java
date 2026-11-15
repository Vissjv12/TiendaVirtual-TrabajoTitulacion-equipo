package com.example.demo.infrastructure.security;

import com.example.demo.domain.repository.ConfiguracionMetodosPagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Al arrancar, cifra los tokens de Payphone que aún estén en texto plano (datos anteriores
 * al cifrado en reposo). Idempotente: los valores ya cifrados no se tocan. Nunca registra tokens.
 */
@Component
public class PayphoneTokenMigracion implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PayphoneTokenMigracion.class);

    private final ConfiguracionMetodosPagoRepository repository;
    private final PayphoneTokenCifradoConverter converter;

    public PayphoneTokenMigracion(ConfiguracionMetodosPagoRepository repository,
                                  PayphoneTokenCifradoConverter converter) {
        this.repository = repository;
        this.converter = converter;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrar();
    }

    @Transactional
    public int migrar() {
        if (!converter.claveConfigurada()) {
            log.warn("PAYPHONE_TOKEN_ENC_KEY no configurada: los tokens de Payphone no se cifrarán "
                    + "y no podrán guardarse nuevos tokens");
            return 0;
        }
        List<Long> pendientes = repository.findIdsConTokenPayphoneSinCifrar();
        for (Long id : pendientes) {
            String plano = repository.findTokenPayphoneCrudo(id);
            if (plano != null && !plano.startsWith(PayphoneTokenCifradoConverter.PREFIJO)) {
                repository.actualizarTokenPayphoneCrudo(id, converter.convertToDatabaseColumn(plano));
            }
        }
        if (!pendientes.isEmpty()) {
            log.info("Tokens de Payphone cifrados en reposo: {}", pendientes.size());
        }
        return pendientes.size();
    }
}
