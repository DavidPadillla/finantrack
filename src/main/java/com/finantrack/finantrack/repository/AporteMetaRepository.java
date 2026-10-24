package com.finantrack.finantrack.repository;

import com.finantrack.finantrack.model.AporteMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AporteMetaRepository extends JpaRepository<AporteMeta, Long> {
    List<AporteMeta> findByMeta_IdOrderByFechaDesc(Long metaId);
}
