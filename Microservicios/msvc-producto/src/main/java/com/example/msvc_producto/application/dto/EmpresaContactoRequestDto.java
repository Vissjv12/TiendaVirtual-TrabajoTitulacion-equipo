package com.example.msvc_producto.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Datos de la empresa que puede modificar el EMPRENDEDOR dueño (A-02).
 * Solo nombre y contacto: el RUC y el estado activo son campos administrativos (solo ADMIN).
 * Cualquier propiedad adicional en el JSON (p. ej. "ruc" o "activo") se rechaza con 400, de forma
 * explícita: no depende de la configuración global de Jackson (que ignora propiedades desconocidas).
 */
public class EmpresaContactoRequestDto {

    @JsonIgnore
    private final Set<String> camposNoPermitidos = new LinkedHashSet<>();

    @JsonAnySetter
    public void registrarCampoNoPermitido(String nombre, Object valor) {
        camposNoPermitidos.add(nombre);
    }

    @JsonIgnore
    @AssertTrue(message = "Solo se pueden modificar nombre, dirección, teléfono y email; el RUC y el estado los gestiona el administrador")
    public boolean isSoloCamposPermitidos() {
        return camposNoPermitidos.isEmpty();
    }

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    private String nombre;

    @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
    private String direccion;

    @Pattern(regexp = "^[0-9]{9,15}$", message = "El teléfono debe tener entre 9 y 15 dígitos")
    private String telefono;

    @Email(message = "El formato del email no es válido")
    @Size(max = 100, message = "El email no puede exceder 100 caracteres")
    private String email;

    public EmpresaContactoRequestDto() {
    }

    public EmpresaContactoRequestDto(String nombre, String direccion, String telefono, String email) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.telefono = telefono;
        this.email = email;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
