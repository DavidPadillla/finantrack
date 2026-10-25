package com.finantrack.finantrack.repository;

import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.TipoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByUsuario_Id(Long usuarioId);

    List<Categoria> findByUsuario_IdOrderByNombreAsc(Long usuarioId);

    List<Categoria> findByUsuarioIsNullOrderByNombreAsc();

    Optional<Categoria> findByIdAndUsuario_Id(Long id, Long usuarioId);

    boolean existsByUsuario_IdAndNombreIgnoreCaseAndTipo(Long usuarioId, String nombre, TipoMovimiento tipo);

    @Query("""
        select c from Categoria c
        where c.activa = true and (:tipo is null or c.tipo = :tipo)
          and (c.usuario.id = :usuarioId or c.usuario is null)
        order by c.nombre
        """)
    List<Categoria> buscarDisponibles(
            @Param("usuarioId") Long usuarioId,
            @Param("tipo") TipoMovimiento tipo);
}
