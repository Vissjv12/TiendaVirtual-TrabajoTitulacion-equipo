package com.example.demo.domain.repository;

import com.example.demo.domain.model.ConfiguracionMetodosPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracionMetodosPagoRepository extends JpaRepository<ConfiguracionMetodosPago, Long> {
    
    /**
     * Buscar configuración por ID del emprendedor
     */
    Optional<ConfiguracionMetodosPago> findByEmprendedorId(Long emprendedorId);
    
    /**
     * Verificar si existe configuración para un emprendedor
     */
    boolean existsByEmprendedorId(Long emprendedorId);
    
    /**
     * Eliminar configuración por ID del emprendedor
     */
    void deleteByEmprendedorId(Long emprendedorId);

    // --- Migración del token de Payphone a cifrado en reposo (consultas nativas: sin conversor) ---

    @org.springframework.data.jpa.repository.Query(value =
            "SELECT id FROM configuracion_metodos_pago WHERE payphone_token IS NOT NULL "
                    + "AND payphone_token <> '' AND payphone_token NOT LIKE 'enc:v1:%'", nativeQuery = true)
    java.util.List<Long> findIdsConTokenPayphoneSinCifrar();

    @org.springframework.data.jpa.repository.Query(value =
            "SELECT payphone_token FROM configuracion_metodos_pago WHERE id = :id", nativeQuery = true)
    String findTokenPayphoneCrudo(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value =
            "UPDATE configuracion_metodos_pago SET payphone_token = :valor WHERE id = :id", nativeQuery = true)
    int actualizarTokenPayphoneCrudo(@org.springframework.data.repository.query.Param("id") Long id,
                                     @org.springframework.data.repository.query.Param("valor") String valor);
}
