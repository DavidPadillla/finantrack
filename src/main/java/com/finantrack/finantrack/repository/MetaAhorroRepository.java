package com.finantrack.finantrack.repository;

import com.finantrack.finantrack.model.MetaAhorro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface MetaAhorroRepository extends JpaRepository<MetaAhorro, Long> {
    List<MetaAhorro> findByUsuario_Id(Long usuarioId);

    @Query("""
        select a.meta.id as metaId, sum(a.monto) as total
        from AporteMeta a
        where a.meta.id in :metaIds
        group by a.meta.id
        """)
    List<AportesPorMeta> totalAportesPorMeta(@Param("metaIds") List<Long> metaIds);

    interface AportesPorMeta {
        Long getMetaId();
        BigDecimal getTotal();
    }
}
