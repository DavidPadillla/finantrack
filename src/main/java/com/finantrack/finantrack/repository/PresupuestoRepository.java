package com.finantrack.finantrack.repository;

import com.finantrack.finantrack.model.Presupuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PresupuestoRepository extends JpaRepository<Presupuesto, Long> {
    List<Presupuesto> findByUsuario_Id(Long usuarioId);

    List<Presupuesto> findByUsuario_IdAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
            Long usuarioId, LocalDate fechaInicioMaxima, LocalDate fechaFinMinima);

    @EntityGraph(attributePaths = "categoria")
    List<Presupuesto> findByUsuario_IdOrderByFechaInicioDesc(Long usuarioId);

    @EntityGraph(attributePaths = "categoria")
    List<Presupuesto> findByUsuario_IdAndActivoTrueAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
            Long usuarioId, LocalDate fechaInicioMaxima, LocalDate fechaFinMinima);

    Optional<Presupuesto> findByIdAndUsuario_Id(Long id, Long usuarioId);

    boolean existsByUsuario_IdAndCategoria_IdAndActivoTrueAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
            Long usuarioId, Long categoriaId, LocalDate fechaFin, LocalDate fechaInicio);

    boolean existsByUsuario_IdAndCategoria_IdAndActivoTrueAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqualAndIdNot(
            Long usuarioId, Long categoriaId, LocalDate fechaFin, LocalDate fechaInicio, Long id);
}
