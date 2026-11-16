package com.example.demo.presentation.controller;

import com.example.demo.application.dto.ConfiguracionPagosDto;
import com.example.demo.application.dto.DatosBancariosDto;
import com.example.demo.application.dto.PayphoneConfirmacionDto;
import com.example.demo.application.dto.PayphoneConfirmarRequestDto;
import com.example.demo.application.dto.PayphoneDisponibilidadDto;
import com.example.demo.application.dto.PayphoneDto;
import com.example.demo.application.dto.PayphonePrepararResponseDto;
import com.example.demo.domain.service.ConfiguracionPagosService;
import com.example.demo.domain.service.PayphonePagoService;
import jakarta.validation.Valid;
import com.example.demo.infrastructure.persistence.repository.EmprendedorJpaRepository;
import com.example.demo.infrastructure.persistence.repository.UsuarioJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/emprendedor/configuracion-pagos")
@RequiredArgsConstructor
public class ConfiguracionPagosController {

    private final ConfiguracionPagosService configuracionPagosService;
    private final PayphonePagoService payphonePagoService;
    private final UsuarioJpaRepository usuarioRepository;
    private final EmprendedorJpaRepository emprendedorRepository;

    /**
     * Obtener configuración completa del emprendedor autenticado
     */
    @GetMapping
    @PreAuthorize("hasRole('EMP')")
    public ResponseEntity<ConfiguracionPagosDto> obtenerConfiguracion(Authentication authentication) {
        Long emprendedorId = obtenerUsuarioId(authentication);
        
        return configuracionPagosService.obtenerConfiguracion(emprendedorId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Guardar datos bancarios del emprendedor
     */
    @PostMapping("/bancarios")
    @PreAuthorize("hasRole('EMP')")
    public ResponseEntity<ConfiguracionPagosDto> guardarDatosBancarios(
            @RequestBody DatosBancariosDto datosBancarios,
            Authentication authentication) {
        
        Long emprendedorId = obtenerUsuarioId(authentication);
        ConfiguracionPagosDto config = configuracionPagosService.guardarDatosBancarios(emprendedorId, datosBancarios);
        
        return ResponseEntity.ok(config);
    }

    /**
     * Guardar datos de Payphone del emprendedor
     */
    @PostMapping("/payphone")
    @PreAuthorize("hasRole('EMP')")
    public ResponseEntity<ConfiguracionPagosDto> guardarPayphone(
            @RequestBody PayphoneDto payphoneDto,
            Authentication authentication) {
        
        Long emprendedorId = obtenerUsuarioId(authentication);
        ConfiguracionPagosDto config = configuracionPagosService.guardarPayphone(emprendedorId, payphoneDto);
        
        return ResponseEntity.ok(config);
    }

    /**
     * Subir QR de Deuna
     */
    @PostMapping("/deuna-qr")
    @PreAuthorize("hasRole('EMP')")
    public ResponseEntity<?> subirQrDeuna(
            @RequestParam("qrImage") MultipartFile archivo,
            Authentication authentication) {
        
        Long emprendedorId = obtenerUsuarioId(authentication);

        try {
            configuracionPagosService.validarArchivoImagenQr(archivo);
            ConfiguracionPagosDto config = configuracionPagosService.guardarQrDeuna(emprendedorId, archivo);
            
            Map<String, String> response = new HashMap<>();
            response.put("qrUrl", config.getQrDeunaUrl());
            response.put("message", "QR de Deuna guardado exitosamente");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al guardar el archivo: " + e.getMessage()));
        }
    }

    /**
     * Eliminar QR de Deuna
     */
    @DeleteMapping("/deuna-qr")
    @PreAuthorize("hasRole('EMP')")
    public ResponseEntity<?> eliminarQrDeuna(Authentication authentication) {
        Long emprendedorId = obtenerUsuarioId(authentication);
        configuracionPagosService.eliminarQrDeuna(emprendedorId);
        
        return ResponseEntity.ok(Map.of("message", "QR eliminado exitosamente"));
    }

    /**
     * Obtener el QR de Deuna de una empresa (público, para el checkout).
     * El parámetro es SIEMPRE empresa.id del producto. Si esa empresa no tiene QR configurado
     * se responde 404; nunca se devuelve el QR de otro emprendedor (hallazgo C-01).
     */
    @GetMapping("/deuna-qr/{empresaId}")
    public ResponseEntity<Map<String, String>> obtenerQrDeunaPorEmpresa(@PathVariable Long empresaId) {
        return configuracionPagosService.obtenerQrDeunaPorEmpresa(empresaId)
                .map(qrUrl -> ResponseEntity.ok(Map.of("qrUrl", qrUrl)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Obtener los datos bancarios de una empresa (público, para el checkout).
     * El parámetro es SIEMPRE empresa.id del producto; 404 si la empresa no los configuró.
     */
    @GetMapping("/bancarios/{empresaId}")
    public ResponseEntity<DatosBancariosDto> obtenerDatosBancariosPorEmpresa(@PathVariable Long empresaId) {
        return configuracionPagosService.obtenerDatosBancariosPorEmpresa(empresaId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Disponibilidad PÚBLICA de Payphone para una empresa (checkout).
     * Solo responde si la empresa acepta Payphone; nunca expone token ni App ID (hallazgo C-02).
     */
    @GetMapping("/payphone/{empresaId}")
    public ResponseEntity<PayphoneDisponibilidadDto> obtenerDisponibilidadPayphone(@PathVariable Long empresaId) {
        return ResponseEntity.ok(new PayphoneDisponibilidadDto(
                empresaId, configuracionPagosService.payphoneDisponiblePorEmpresa(empresaId)));
    }

    /**
     * Prepara el cobro Payphone de una venta YA CREADA (A-03): el monto lo calcula el backend
     * a partir de la venta; el navegador no envía importes. Solo el comprador de la venta.
     */
    @PostMapping("/payphone/ventas/{ventaId}/preparar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PayphonePrepararResponseDto> prepararPagoPayphone(
            @PathVariable Long ventaId,
            Authentication authentication) {
        return ResponseEntity.ok(payphonePagoService.preparar(ventaId, emailDe(authentication)));
    }

    /**
     * Confirma con Payphone, en el servidor, la transacción devuelta en la URL de respuesta y
     * finaliza la venta de forma idempotente (A-04). Devuelve un resultado saneado.
     */
    @PostMapping("/payphone/confirmar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PayphoneConfirmacionDto> confirmarPagoPayphone(
            @Valid @RequestBody PayphoneConfirmarRequestDto solicitud) {
        return ResponseEntity.ok(payphonePagoService.confirmar(solicitud));
    }

    /**
     * URL de notificación de Payphone (M-06). Pública: su contenido no se da por cierto, solo
     * dispara la verificación servidor-a-servidor con Payphone y la reconciliación de la venta.
     * Responde 503 si un servicio interno no está disponible, para que Payphone reintente.
     */
    @PostMapping("/payphone/notificacion")
    public ResponseEntity<Map<String, Object>> notificacionPayphone(
            @RequestBody(required = false) com.fasterxml.jackson.databind.JsonNode cuerpo) {
        var resultado = payphonePagoService.procesarNotificacion(cuerpo);
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("recibido", true);
        resultado.map(PayphoneConfirmacionDto::getResultadoVenta)
                .ifPresent(r -> respuesta.put("resultadoVenta", r));
        return ResponseEntity.ok(respuesta);
    }

    private String emailDe(Authentication authentication) {
        if (authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt) {
            Object email = jwt.getToken().getClaims().get("email");
            if (email != null && !email.toString().isBlank()) {
                return email.toString();
            }
        }
        return null;
    }

    /**
     * Guardar la cuota de envío (SOLO el rol Emprendedor puede modificarla)
     */
    @PutMapping("/costo-envio")
    @PreAuthorize("hasRole('EMP')")
    public ResponseEntity<ConfiguracionPagosDto> guardarCostoEnvio(
            @jakarta.validation.Valid @RequestBody com.example.demo.application.dto.CostoEnvioDto costoEnvioDto,
            Authentication authentication) {

        Long emprendedorId = obtenerUsuarioId(authentication);
        ConfiguracionPagosDto config = configuracionPagosService.guardarCostoEnvio(
                emprendedorId, costoEnvioDto.getCostoEnvio());

        return ResponseEntity.ok(config);
    }

    /**
     * Obtener la cuota de envío de un emprendedor por empresa_id (público, para el checkout).
     * Si el emprendedor no la configuró, devuelve la cuota fija de $5.00.
     */
    @GetMapping("/costo-envio/{empresaId}")
    public ResponseEntity<Map<String, Object>> obtenerCostoEnvioPorEmpresa(@PathVariable Long empresaId) {
        var emprendedorOpt = emprendedorRepository.findByEmpresaId(empresaId);

        java.math.BigDecimal costoEnvio = emprendedorOpt
                .map(emprendedor -> configuracionPagosService.obtenerCostoEnvio(emprendedor.getId()))
                .orElse(com.example.demo.domain.service.ConfiguracionPagosService.COSTO_ENVIO_POR_DEFECTO);

        Map<String, Object> response = new HashMap<>();
        response.put("empresaId", empresaId);
        response.put("costoEnvio", costoEnvio);

        return ResponseEntity.ok(response);
    }

    /**
     * Extraer el emprendedor_id del usuario autenticado
     * El JWT tiene como subject el username del usuario.
     * Se busca el usuario por username, luego el emprendedor por usuario_id.
     */
    private Long obtenerUsuarioId(Authentication authentication) {
        String username = authentication.getName();
        
        // Buscar usuario por username
        var usuarioOpt = usuarioRepository.findByUsername(username);
        if (usuarioOpt.isEmpty()) {
            throw new RuntimeException("Usuario no encontrado: " + username);
        }
        Long usuarioId = usuarioOpt.get().getId();
        
        // Buscar emprendedor por usuario_id
        var emprendedorOpt = emprendedorRepository.findByUsuarioId(usuarioId);
        if (emprendedorOpt.isEmpty()) {
            throw new RuntimeException("No existe un perfil de emprendedor para el usuario: " + username);
        }
        
        return emprendedorOpt.get().getId();
    }
}
