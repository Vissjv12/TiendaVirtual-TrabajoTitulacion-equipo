package com.example.msvc_ventas.application.client;

import com.example.msvc_ventas.application.dto.CostoEnvioResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", url = "${app.msvc-auth-service.url}")
public interface ConfiguracionPagosClient {

    @GetMapping("/api/emprendedor/configuracion-pagos/costo-envio/{empresaId}")
    CostoEnvioResponseDto obtenerCostoEnvio(@PathVariable("empresaId") Long empresaId);
}
