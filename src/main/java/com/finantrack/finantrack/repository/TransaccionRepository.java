package com.finantrack.finantrack.repository;

import com.finantrack.finantrack.model.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {
    List<Transaccion> findByUsuario_IdOrderByFechaDesc(Long usuarioId);
}
