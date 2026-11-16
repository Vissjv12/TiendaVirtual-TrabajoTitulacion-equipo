package com.example.msvc_inventario.application.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Usuario real que ejecuta una operación de inventario, tomado del JWT validado (claim userId).
 * Reemplaza el usuarioId fijo (1L) que se registraba en los movimientos (A-01).
 * Devuelve null para credenciales sin usuario (p. ej. la credencial interna de servicio de pagos).
 */
public final class UsuarioAutenticado {

    private UsuarioAutenticado() {
    }

    public static Long id() {
        return id(SecurityContextHolder.getContext().getAuthentication());
    }

    public static Long id(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return null;
        }
        Object claim = jwt.getToken().getClaims().get("userId");
        if (claim instanceof Number numero) {
            return numero.longValue();
        }
        if (claim instanceof String texto) {
            try {
                return Long.valueOf(texto.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
