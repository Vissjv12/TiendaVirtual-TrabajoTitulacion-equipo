package com.example.demo.infrastructure.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado en reposo del token de Payphone (columna configuracion_metodos_pago.payphone_token).
 *
 * - AES-256-GCM con IV aleatorio de 12 bytes por valor y etiqueta de 128 bits (autenticado:
 *   cualquier alteración del valor guardado se detecta al descifrar).
 * - Formato guardado: "enc:v1:" + Base64(IV || cifrado+etiqueta).
 * - Clave: PAYPHONE_TOKEN_ENC_KEY (Base64 de 32 bytes), definida solo en el servidor.
 * - Valores heredados en texto plano (sin prefijo) se leen tal cual y los cifra
 *   {@link PayphoneTokenMigracion} al arrancar.
 * - Falla cerrado: sin clave no se puede guardar ni leer un token cifrado.
 */
@Component
@Converter
public class PayphoneTokenCifradoConverter implements AttributeConverter<String, String> {

    public static final String PREFIJO = "enc:v1:";
    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] AAD = "configuracion_metodos_pago.payphone_token".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec clave;
    private final SecureRandom random = new SecureRandom();

    public PayphoneTokenCifradoConverter(@Value("${payphone.token.encryption-key:}") String claveBase64) {
        this.clave = construirClave(claveBase64);
    }

    public boolean claveConfigurada() {
        return clave != null;
    }

    @Override
    public String convertToDatabaseColumn(String valor) {
        if (valor == null) {
            return null;
        }
        if (valor.startsWith(PREFIJO)) {
            return valor; // ya cifrado
        }
        exigirClave();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] cifrado = cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8));
            byte[] salida = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIJO + Base64.getEncoder().encodeToString(salida);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el token de Payphone");
        }
    }

    @Override
    public String convertToEntityAttribute(String guardado) {
        if (guardado == null || !guardado.startsWith(PREFIJO)) {
            return guardado; // nulo o heredado en texto plano (pendiente de migración)
        }
        exigirClave();
        try {
            byte[] datos = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
            if (datos.length <= IV_BYTES) {
                throw new IllegalStateException("Token de Payphone cifrado inválido");
            }
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, datos, 0, IV_BYTES));
            cipher.updateAAD(AAD);
            byte[] plano = cipher.doFinal(datos, IV_BYTES, datos.length - IV_BYTES);
            return new String(plano, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            // Mensaje sin el valor: ni el cifrado ni el token aparecen en logs
            throw new IllegalStateException("No se pudo descifrar el token de Payphone (clave incorrecta o dato alterado)");
        }
    }

    private void exigirClave() {
        if (clave == null) {
            throw new IllegalStateException(
                    "PAYPHONE_TOKEN_ENC_KEY no está configurada: no se puede operar con tokens de Payphone");
        }
    }

    private static SecretKeySpec construirClave(String claveBase64) {
        if (claveBase64 == null || claveBase64.isBlank()) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(claveBase64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("PAYPHONE_TOKEN_ENC_KEY debe estar en Base64");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("PAYPHONE_TOKEN_ENC_KEY debe representar exactamente 32 bytes (AES-256)");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
