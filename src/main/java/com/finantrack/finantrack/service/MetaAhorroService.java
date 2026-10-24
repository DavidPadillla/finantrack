package com.finantrack.finantrack.service;

import com.finantrack.finantrack.model.MetaAhorro;
import com.finantrack.finantrack.repository.MetaAhorroRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MetaAhorroService {

    private final MetaAhorroRepository metaAhorroRepository;

    public MetaAhorroService(MetaAhorroRepository metaAhorroRepository) {
        this.metaAhorroRepository = metaAhorroRepository;
    }

    @Transactional(readOnly = true)
    public List<MetaAhorro> listarPorUsuario(Long usuarioId) {
        return metaAhorroRepository.findByUsuario_Id(usuarioId);
    }

    @Transactional(readOnly = true)
    public MetaAhorro buscarPorId(Long id) {
        return metaAhorroRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la meta con id " + id));
    }

    @Transactional
    public MetaAhorro guardar(MetaAhorro metaAhorro) {
        return metaAhorroRepository.save(metaAhorro);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!metaAhorroRepository.existsById(id)) {
            throw new EntityNotFoundException("No existe la meta con id " + id);
        }
        metaAhorroRepository.deleteById(id);
    }
}
