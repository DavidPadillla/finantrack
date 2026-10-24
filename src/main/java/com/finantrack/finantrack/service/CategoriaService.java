package com.finantrack.finantrack.service;

import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.repository.CategoriaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @Transactional(readOnly = true)
    public List<Categoria> listarPorUsuario(Long usuarioId) {
        return categoriaRepository.findByUsuario_Id(usuarioId);
    }

    @Transactional(readOnly = true)
    public List<Categoria> listarGenerales() {
        return categoriaRepository.findByUsuarioIsNull();
    }

    @Transactional(readOnly = true)
    public Categoria buscarPorId(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la categoría con id " + id));
    }

    @Transactional
    public Categoria guardar(Categoria categoria) {
        return categoriaRepository.save(categoria);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!categoriaRepository.existsById(id)) {
            throw new EntityNotFoundException("No existe la categoría con id " + id);
        }
        categoriaRepository.deleteById(id);
    }
}
