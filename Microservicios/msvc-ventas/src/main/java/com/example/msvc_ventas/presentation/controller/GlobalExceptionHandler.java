package com.example.msvc_ventas.presentation.controller;

import com.example.msvc_ventas.presentation.dto.ErrorResponse;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuchElementException(NoSuchElementException ex) {
        ErrorResponse error = new ErrorResponse("NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        ErrorResponse error = new ErrorResponse("BAD_REQUEST", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse("BAD_REQUEST", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse("FORBIDDEN", "No tienes permisos para acceder o modificar este recurso");
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        ErrorResponse error = new ErrorResponse("UNAUTHORIZED", "Autenticacion requerida");
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(FeignException ex) {
        log.error("Fallo de comunicacion con un microservicio: status={}, mensaje={}",
                ex.status(), ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse("SERVICE_UNAVAILABLE",
                "El servicio requerido no esta disponible temporalmente");
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        // Excepciones propias de Spring MVC con su estado HTTP (ruta inexistente -> 404, método no
        // permitido -> 405, tipo de contenido -> 415...). Antes caían aquí y respondían 500.
        if (ex instanceof org.springframework.web.ErrorResponse respuestaMvc) {
            HttpStatus estado = HttpStatus.resolve(respuestaMvc.getStatusCode().value());
            if (estado != null && estado.is4xxClientError()) {
                return new ResponseEntity<>(new ErrorResponse(estado.name(), "Solicitud no válida para este recurso"), estado);
            }
        }
        log.error("Error no controlado en msvc-ventas", ex);
        ErrorResponse error = new ErrorResponse("INTERNAL_SERVER_ERROR",
                "Ha ocurrido un error inesperado");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
