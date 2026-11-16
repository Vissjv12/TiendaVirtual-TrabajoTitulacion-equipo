package com.example.demo.infrastructure.persistence.repository;

import com.example.demo.infrastructure.persistence.entity.EmprendedorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmprendedorJpaRepository extends JpaRepository<EmprendedorEntity, Long> {
    Optional<EmprendedorEntity> findByUsuarioId(Long usuarioId);
    Optional<EmprendedorEntity> findByEmpresaId(Long empresaId);
    /** T11: empresaId de una página de usuarios en una sola consulta. */
    List<EmprendedorEntity> findByUsuarioIdIn(Collection<Long> usuarioIds);
}
