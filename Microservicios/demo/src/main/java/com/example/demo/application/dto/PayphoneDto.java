package com.example.demo.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Credenciales de Payphone que ENVÍA el emprendedor al guardarlas (solo entrada).
 * El token es de solo escritura: Jackson nunca lo serializa en una respuesta (hallazgo C-02)
 * y se excluye de toString() para que no aparezca en logs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayphoneDto {
    private String payphoneAppId;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String payphoneToken;
}
