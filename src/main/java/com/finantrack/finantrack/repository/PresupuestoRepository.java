package com.finantrack.finantrack.repository;

import com.finantrack.finantrack.model.Presupuesto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PresupuestoRepository extends JpaRepository<Presupuesto, Long> {
    List<Presupuesto> findByUsuario_Id(Long usuarioId);

    @EntityGraph(attributePaths = "categoria")
    List<Presupuesto> findByUsuario_IdAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
            Long usuarioId, LocalDate fechaInicioMaxima, LocalDate fechaFinMinima);
}
