package com.finantrack.finantrack.service;

import com.finantrack.finantrack.model.Presupuesto;
import com.finantrack.finantrack.repository.PresupuestoRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PresupuestoService {

    private final PresupuestoRepository presupuestoRepository;

    public PresupuestoService(PresupuestoRepository presupuestoRepository) {
        this.presupuestoRepository = presupuestoRepository;
    }

    @Transactional(readOnly = true)
    public List<Presupuesto> listarPorUsuario(Long usuarioId) {
        return presupuestoRepository.findByUsuario_Id(usuarioId);
    }

    @Transactional(readOnly = true)
    public Presupuesto buscarPorId(Long id) {
        return presupuestoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el presupuesto con id " + id));
    }

    @Transactional
    public Presupuesto guardar(Presupuesto presupuesto) {
        return presupuestoRepository.save(presupuesto);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!presupuestoRepository.existsById(id)) {
            throw new EntityNotFoundException("No existe el presupuesto con id " + id);
        }
        presupuestoRepository.deleteById(id);
    }
}
