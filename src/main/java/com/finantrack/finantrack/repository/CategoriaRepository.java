package com.finantrack.finantrack.repository;

import com.finantrack.finantrack.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByUsuario_Id(Long usuarioId);

    List<Categoria> findByUsuarioIsNull();
}
