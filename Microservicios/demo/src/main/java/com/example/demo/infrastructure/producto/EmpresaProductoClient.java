package com.example.demo.infrastructure.producto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.NoSuchElementException;

/**
 * T11: consulta la empresa real en msvc-producto (dueño del dato) antes de vincularla a un
 * emprendedor. No hay relación JPA entre bases: la empresa vive en otro servicio y otra base.
 * Llama directo al servicio (no por el Gateway); GET /api/empresas/{id} es de lectura pública.
 */
@Component
public class EmpresaProductoClient {

    private static final Logger log = LoggerFactory.getLogger(EmpresaProductoClient.class);

    private final RestClient restClient;

    public EmpresaProductoClient(RestClient.Builder builder,
                                 @Value("${app.msvc-producto.url:http://localhost:8081}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * @throws NoSuchElementException          si la empresa no existe (404)
     * @throws ProductoNoDisponibleException   si msvc-producto no responde o falla (503)
     */
    public EmpresaResumen obtenerEmpresa(long empresaId) {
        try {
            EmpresaResumen empresa = restClient.get()
                    .uri("/api/empresas/{id}", empresaId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(EmpresaResumen.class);
            if (empresa == null || empresa.id() == null) {
                throw new ProductoNoDisponibleException("msvc-producto no devolvió la empresa");
            }
            return empresa;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new NoSuchElementException("La empresa " + empresaId + " no existe");
            }
            log.warn("msvc-producto respondió {} al consultar la empresa {}", ex.getStatusCode().value(), empresaId);
            throw new ProductoNoDisponibleException("No se pudo verificar la empresa en el servicio de productos");
        } catch (RestClientException ex) {
            log.warn("No se pudo contactar a msvc-producto al consultar la empresa {}", empresaId);
            throw new ProductoNoDisponibleException("El servicio de productos no está disponible");
        }
    }

    /** Solo los campos necesarios para validar la vinculación. */
    public record EmpresaResumen(Long id, String nombre, Boolean activo) {
        public boolean estaActiva() {
            return Boolean.TRUE.equals(activo);
        }
    }
}
