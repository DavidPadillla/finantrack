package com.finantrack.finantrack.repository;

import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.TipoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByUsuario_Id(Long usuarioId);

    List<Categoria> findByUsuarioIsNull();

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
