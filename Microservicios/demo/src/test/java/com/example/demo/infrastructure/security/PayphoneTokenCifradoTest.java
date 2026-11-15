package com.example.demo.infrastructure.security;

import com.example.demo.domain.repository.ConfiguracionMetodosPagoRepository;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cifrado en reposo del token de Payphone (AES-256-GCM) y migración de valores heredados. */
class PayphoneTokenCifradoTest {

    private static final String TOKEN = "tok-payphone-secreto-123";
    private static final String CLAVE = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTRA_CLAVE;

    static {
        byte[] otra = new byte[32];
        otra[0] = 1;
        OTRA_CLAVE = Base64.getEncoder().encodeToString(otra);
    }

    private final PayphoneTokenCifradoConverter converter = new PayphoneTokenCifradoConverter(CLAVE);

    @Test
    void cifraYDescifraSinGuardarElTextoPlano() {
        String guardado = converter.convertToDatabaseColumn(TOKEN);

        assertTrue(guardado.startsWith(PayphoneTokenCifradoConverter.PREFIJO));
        assertFalse(guardado.contains(TOKEN));
        assertEquals(TOKEN, converter.convertToEntityAttribute(guardado));
    }

    @Test
    void cadaCifradoUsaUnIvDistinto() {
        assertNotEquals(converter.convertToDatabaseColumn(TOKEN), converter.convertToDatabaseColumn(TOKEN));
    }

    @Test
    void valorAlteradoOClaveIncorrectaFallanSinMostrarDatos() {
        String guardado = converter.convertToDatabaseColumn(TOKEN);
        // Alterar un carácter del cuerpo (no los finales, que pueden ser bits de relleno Base64)
        char[] c = guardado.toCharArray();
        int i = PayphoneTokenCifradoConverter.PREFIJO.length() + 20;
        c[i] = c[i] == 'A' ? 'B' : 'A';
        String alterado = new String(c);

        IllegalStateException ex1 = assertThrows(IllegalStateException.class,
                () -> converter.convertToEntityAttribute(alterado));
        IllegalStateException ex2 = assertThrows(IllegalStateException.class,
                () -> new PayphoneTokenCifradoConverter(OTRA_CLAVE).convertToEntityAttribute(guardado));
        assertFalse(ex1.getMessage().contains(guardado));
        assertFalse(ex2.getMessage().contains(TOKEN));
    }

    @Test
    void sinClaveFallaCerradoAlGuardarYAlLeerCifrados() {
        PayphoneTokenCifradoConverter sinClave = new PayphoneTokenCifradoConverter("");
        String guardado = converter.convertToDatabaseColumn(TOKEN);

        assertFalse(sinClave.claveConfigurada());
        assertThrows(IllegalStateException.class, () -> sinClave.convertToDatabaseColumn(TOKEN));
        assertThrows(IllegalStateException.class, () -> sinClave.convertToEntityAttribute(guardado));
    }

    @Test
    void claveMalFormadaSeRechazaAlIniciar() {
        assertThrows(IllegalStateException.class, () -> new PayphoneTokenCifradoConverter("no-es-base64!!"));
        assertThrows(IllegalStateException.class,
                () -> new PayphoneTokenCifradoConverter(Base64.getEncoder().encodeToString(new byte[16])));
    }

    @Test
    void valoresHeredadosYNulosSeLeenTalCual() {
        assertEquals("plano-heredado", converter.convertToEntityAttribute("plano-heredado"));
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void migracionCifraSoloLosTokensEnTextoPlano() {
        ConfiguracionMetodosPagoRepository repo = mock(ConfiguracionMetodosPagoRepository.class);
        when(repo.findIdsConTokenPayphoneSinCifrar()).thenReturn(List.of(1L, 2L));
        when(repo.findTokenPayphoneCrudo(1L)).thenReturn(TOKEN);
        when(repo.findTokenPayphoneCrudo(2L)).thenReturn(converter.convertToDatabaseColumn("ya"));

        int migrados = new PayphoneTokenMigracion(repo, converter).migrar();

        assertEquals(2, migrados);
        verify(repo).actualizarTokenPayphoneCrudo(eq(1L), startsWith(PayphoneTokenCifradoConverter.PREFIJO));
        verify(repo, never()).actualizarTokenPayphoneCrudo(eq(2L), anyString());
    }

    @Test
    void migracionSinClaveNoModificaNada() {
        ConfiguracionMetodosPagoRepository repo = mock(ConfiguracionMetodosPagoRepository.class);

        assertEquals(0, new PayphoneTokenMigracion(repo, new PayphoneTokenCifradoConverter("")).migrar());
        verify(repo, never()).findIdsConTokenPayphoneSinCifrar();
        verify(repo, never()).actualizarTokenPayphoneCrudo(anyLong(), anyString());
    }
}
