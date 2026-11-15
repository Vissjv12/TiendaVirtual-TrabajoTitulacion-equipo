package com.example.msvcventas.infrastructure.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/bpm/ventas")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "BPM - Proceso de Ventas", description = "Gestión de ventas mediante proceso BPM")
public class ProcesoVentaBpmController {

    private final RuntimeService runtimeService;

    @PostMapping("/iniciar")
    @Operation(summary = "Iniciar proceso BPM de venta", 
               description = "Crea una venta mediante el motor BPM de Camunda, orquestando todos los microservicios")
    public ResponseEntity<Map<String, Object>> iniciarProcesoVenta(@RequestBody Map<String, Object> ventaRequest) {
        try {
            log.info("🚀 Iniciando proceso BPM de venta");
            
            // Preparar variables del proceso
            Map<String, Object> variables = new HashMap<>();
            variables.put("clienteId", ventaRequest.get("clienteId"));
            variables.put("items", ventaRequest.get("items"));
            variables.put("metodoPago", ventaRequest.get("metodoPago"));
            
            // Iniciar proceso
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "proceso-venta-completa", 
                variables
            );
            
            log.info("✅ Proceso BPM iniciado: ProcessInstanceID={}", processInstance.getId());
            
            // Preparar respuesta
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("processInstanceId", processInstance.getId());
            response.put("processDefinitionId", processInstance.getProcessDefinitionId());
            response.put("businessKey", processInstance.getBusinessKey());
            response.put("mensaje", "Proceso de venta iniciado exitosamente");
            
            // Verificar si el proceso terminó
            if (processInstance.isEnded()) {
                response.put("estadoProceso", "COMPLETADO");
                
                // Obtener variables del proceso
                Map<String, Object> processVariables = runtimeService.getVariables(processInstance.getId());
                
                if (processVariables.containsKey("ventaId")) {
                    response.put("ventaId", processVariables.get("ventaId"));
                    response.put("numeroFactura", processVariables.get("numeroFactura"));
                    response.put("total", processVariables.get("totalVenta"));
                }
                
                if (processVariables.get("ventaCancelada") == Boolean.TRUE) {
                    response.put("estadoProceso", "CANCELADO");
                    response.put("motivo", processVariables.get("motivoCancelacion"));
                }
                
                if (processVariables.get("ventaRevertida") == Boolean.TRUE) {
                    response.put("estadoProceso", "REVERTIDO");
                    response.put("motivo", processVariables.get("motivoReversion"));
                }
            } else {
                response.put("estadoProceso", "EN_PROCESO");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error al iniciar proceso BPM de venta", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("mensaje", "Error al procesar la venta mediante BPM");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/proceso/{processInstanceId}")
    @Operation(summary = "Consultar estado de proceso BPM", 
               description = "Obtiene el estado actual de un proceso de venta en ejecución")
    public ResponseEntity<Map<String, Object>> consultarEstadoProceso(@PathVariable String processInstanceId) {
        try {
            log.info("🔍 Consultando estado del proceso: {}", processInstanceId);
            
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
            
            Map<String, Object> response = new HashMap<>();
            
            if (processInstance == null) {
                // Proceso no existe o ya terminó
                response.put("existe", false);
                response.put("mensaje", "Proceso no encontrado o ya completado");
                return ResponseEntity.ok(response);
            }
            
            response.put("existe", true);
            response.put("processInstanceId", processInstance.getId());
            response.put("activo", !processInstance.isEnded());
            response.put("suspendido", processInstance.isSuspended());
            
            // Obtener variables del proceso
            Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
            response.put("variables", variables);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error al consultar proceso BPM", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/procesos-activos")
    @Operation(summary = "Listar procesos activos", 
               description = "Obtiene todos los procesos de venta que están en ejecución")
    public ResponseEntity<Map<String, Object>> listarProcesosActivos() {
        try {
            List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey("proceso-venta-completa")
                .active()
                .list();
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalProcesos", processInstances.size());
            response.put("procesos", processInstances.stream().map(pi -> {
                Map<String, Object> procesoInfo = new HashMap<>();
                procesoInfo.put("processInstanceId", pi.getId());
                procesoInfo.put("businessKey", pi.getBusinessKey());
                procesoInfo.put("suspendido", pi.isSuspended());
                return procesoInfo;
            }).toList());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error al listar procesos activos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
