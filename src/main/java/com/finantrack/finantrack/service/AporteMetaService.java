package com.finantrack.finantrack.service;

import com.finantrack.finantrack.model.AporteMeta;
import com.finantrack.finantrack.repository.AporteMetaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AporteMetaService {

    private final AporteMetaRepository aporteMetaRepository;

    public AporteMetaService(AporteMetaRepository aporteMetaRepository) {
        this.aporteMetaRepository = aporteMetaRepository;
    }

    @Transactional(readOnly = true)
    public List<AporteMeta> listarPorMeta(Long metaId) {
        return aporteMetaRepository.findByMeta_IdOrderByFechaDesc(metaId);
    }

    @Transactional(readOnly = true)
    public AporteMeta buscarPorId(Long id) {
        return aporteMetaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el aporte con id " + id));
    }

    @Transactional
    public AporteMeta guardar(AporteMeta aporteMeta) {
        return aporteMetaRepository.save(aporteMeta);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!aporteMetaRepository.existsById(id)) {
            throw new EntityNotFoundException("No existe el aporte con id " + id);
        }
        aporteMetaRepository.deleteById(id);
    }
}
