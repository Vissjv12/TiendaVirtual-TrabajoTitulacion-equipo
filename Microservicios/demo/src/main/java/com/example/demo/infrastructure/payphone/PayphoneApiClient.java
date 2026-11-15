package com.example.demo.infrastructure.payphone;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Cliente HTTP de la API de "Botón de Pago" de Payphone (hallazgo C-02).
 *
 * El token del comercio SOLO viaja en la cabecera Authorization de estas llamadas
 * servidor a servidor; nunca se registra en logs ni se devuelve al navegador.
 *
 * Endpoints usados (base configurable con PAYPHONE_API_BASE_URL):
 *  - POST {base}/api/button/Prepare     -> { paymentId, payWithPayPhone, payWithCard }
 *  - POST {base}/api/button/V2/Confirm  -> estado de la transacción
 * Validar el contrato con el entorno de pruebas de Payphone cuando se disponga del token.
 */
@Component
public class PayphoneApiClient {

    private static final Logger log = LoggerFactory.getLogger(PayphoneApiClient.class);

    static final String PREPARE_PATH = "/api/button/Prepare";
    static final String CONFIRM_PATH = "/api/button/V2/Confirm";

    private final RestClient restClient;

    public PayphoneApiClient(RestClient.Builder builder,
                             @Value("${payphone.api.base-url:https://pay.payphonetodoesposible.com}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public JsonNode preparar(String token, Map<String, Object> cuerpo) {
        return post(PREPARE_PATH, token, cuerpo, "preparar el pago");
    }

    public JsonNode confirmar(String token, long id, String clientTxId) {
        return post(CONFIRM_PATH, token, Map.of("id", id, "clientTxId", clientTxId), "confirmar el pago");
    }

    private JsonNode post(String path, String token, Object cuerpo, String operacion) {
        try {
            JsonNode respuesta = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(cuerpo)
                    .retrieve()
                    .body(JsonNode.class);
            if (respuesta == null) {
                throw new PayphoneIntegracionException("Payphone no devolvió respuesta al " + operacion);
            }
            return respuesta;
        } catch (RestClientResponseException ex) {
            // Solo el código HTTP: el cuerpo podría reflejar datos sensibles
            log.warn("Payphone respondió HTTP {} al {}", ex.getStatusCode().value(), operacion);
            throw new PayphoneIntegracionException("Payphone rechazó la solicitud al " + operacion);
        } catch (RestClientException ex) {
            log.warn("No se pudo contactar a Payphone al {}: {}", operacion, ex.getClass().getSimpleName());
            throw new PayphoneIntegracionException("No se pudo contactar a Payphone");
        }
    }
}
