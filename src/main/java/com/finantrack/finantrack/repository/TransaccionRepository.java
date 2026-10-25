package com.finantrack.finantrack.repository;

import com.finantrack.finantrack.model.Transaccion;
import com.finantrack.finantrack.model.TipoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransaccionRepository extends JpaRepository<Transaccion, Long>, JpaSpecificationExecutor<Transaccion> {
    List<Transaccion> findByUsuario_IdOrderByFechaDesc(Long usuarioId);

    @Query("""
        select coalesce(sum(t.monto), 0)
        from Transaccion t
        where t.usuario.id = :usuarioId and t.tipo = :tipo
        """)
    BigDecimal totalPorUsuarioYTipo(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoMovimiento tipo);

    @Query("""
        select coalesce(sum(t.monto), 0)
        from Transaccion t
        where t.usuario.id = :usuarioId and t.tipo = :tipo
          and t.fecha between :inicio and :fin
        """)
    BigDecimal totalPorUsuarioTipoYPeriodo(
            @Param("usuarioId") Long usuarioId,
            @Param("tipo") TipoMovimiento tipo,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    @Query("""
        select coalesce(c.nombre, 'Sin categoría') as nombre, sum(t.monto) as total, c.color as color
        from Transaccion t
        left join t.categoria c on c.usuario is null or c.usuario.id = t.usuario.id
        where t.usuario.id = :usuarioId and t.tipo = :tipo
          and t.fecha between :inicio and :fin
        group by c.id, c.nombre, c.color
        order by sum(t.monto) desc
        """)
    List<CategoriaTotal> gastosPorCategoriaYPeriodo(
            @Param("usuarioId") Long usuarioId,
            @Param("tipo") TipoMovimiento tipo,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    @Query(value = """
        select cast(extract(year from fecha) as integer) as year,
               cast(extract(month from fecha) as integer) as month,
               tipo as tipo,
               sum(monto) as total
        from transacciones
        where usuario_id = :usuarioId and fecha between :inicio and :fin
        group by extract(year from fecha), extract(month from fecha), tipo
        """, nativeQuery = true)
    List<MonthlyTotal> totalesMensuales(
            @Param("usuarioId") Long usuarioId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    @EntityGraph(attributePaths = "categoria")
    Page<Transaccion> findByUsuario_IdOrderByFechaDescIdDesc(Long usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = "categoria")
    @Query("""
        select t from Transaccion t
        left join t.categoria c on c.usuario is null or c.usuario.id = t.usuario.id
        where t.usuario.id = :usuarioId
          and (lower(coalesce(t.descripcion, '')) like lower(concat('%', :busqueda, '%'))
            or lower(coalesce(c.nombre, '')) like lower(concat('%', :busqueda, '%')))
        order by t.fecha desc, t.id desc
        """)
    Page<Transaccion> buscarRecientes(
            @Param("usuarioId") Long usuarioId,
            @Param("busqueda") String busqueda,
            Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "categoria")
    Page<Transaccion> findAll(Specification<Transaccion> specification, Pageable pageable);

    Optional<Transaccion> findByIdAndUsuario_Id(Long id, Long usuarioId);

    @Query("""
        select coalesce(sum(t.monto), 0)
        from Transaccion t
        where t.usuario.id = :usuarioId and t.categoria.id = :categoriaId
          and t.tipo = :tipo and t.fecha between :inicio and :fin
        """)
    BigDecimal totalPorCategoriaYPeriodo(
            @Param("usuarioId") Long usuarioId,
            @Param("categoriaId") Long categoriaId,
            @Param("tipo") TipoMovimiento tipo,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);

    @Query(value = """
        select p.id as presupuestoId, coalesce(sum(t.monto), 0) as total
        from presupuestos p
        left join transacciones t
          on t.usuario_id = p.usuario_id and t.categoria_id = p.categoria_id
         and t.tipo = 'GASTO' and t.fecha between p.fecha_inicio and p.fecha_fin
        where p.usuario_id = :usuarioId
        group by p.id
        """, nativeQuery = true)
    List<PresupuestoTotal> totalesPorPresupuesto(@Param("usuarioId") Long usuarioId);

    interface CategoriaTotal {
        String getNombre();
        BigDecimal getTotal();
        String getColor();
    }

    interface MonthlyTotal {
        Integer getYear();
        Integer getMonth();
        String getTipo();
        BigDecimal getTotal();
    }

    interface PresupuestoTotal {
        Long getPresupuestoId();
        BigDecimal getTotal();
    }
}
