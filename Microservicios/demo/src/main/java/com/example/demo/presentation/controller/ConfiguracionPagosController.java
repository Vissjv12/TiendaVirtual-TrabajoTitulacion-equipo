package com.example.demo.presentation.controller;

import com.example.demo.application.dto.ConfiguracionPagosDto;
import com.example.demo.application.dto.DatosBancariosDto;
import com.example.demo.application.dto.PayphoneDto;
import com.example.demo.domain.service.ConfiguracionPagosService;
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
     * Obtener QR de Deuna de un emprendedor (para clientes)
     * Este endpoint es público o puede requerir autenticación según tu lógica
     */
    @GetMapping("/deuna-qr/{emprendedorId}")
    public ResponseEntity<?> obtenerQrDeunaPorEmprendedor(@PathVariable Long emprendedorId) {
        return configuracionPagosService.obtenerQrDeuna(emprendedorId)
                .map(qrUrl -> ResponseEntity.ok(Map.of("qrUrl", qrUrl)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Obtener datos bancarios de un emprendedor (para clientes)
     */
    @GetMapping("/bancarios/{emprendedorId}")
    public ResponseEntity<DatosBancariosDto> obtenerDatosBancariosPorEmprendedor(@PathVariable Long emprendedorId) {
        return configuracionPagosService.obtenerDatosBancarios(emprendedorId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Obtener configuración de Payphone de un emprendedor por empresa_id (para clientes en el checkout).
     * El frontend envía empresa.id del producto - que coincide con empresa_id en la tabla emprendedores.
     */
    @GetMapping("/payphone/{empresaId}")
    public ResponseEntity<PayphoneDto> obtenerPayphonePorEmprendedor(@PathVariable Long empresaId) {
        // Resolver emprendedor_id a partir de empresa_id
        var emprendedorOpt = emprendedorRepository.findByEmpresaId(empresaId);
        if (emprendedorOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Long emprendedorId = emprendedorOpt.get().getId();
        
        return configuracionPagosService.obtenerConfiguracion(emprendedorId)
                .filter(config -> config.getPayphone() != null)
                .map(config -> ResponseEntity.ok(config.getPayphone()))
                .orElse(ResponseEntity.notFound().build());
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
