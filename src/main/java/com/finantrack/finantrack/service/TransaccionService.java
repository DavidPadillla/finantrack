package com.finantrack.finantrack.service;

import com.finantrack.finantrack.model.Transaccion;
import com.finantrack.finantrack.repository.TransaccionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransaccionService {

    private final TransaccionRepository transaccionRepository;

    public TransaccionService(TransaccionRepository transaccionRepository) {
        this.transaccionRepository = transaccionRepository;
    }

    @Transactional(readOnly = true)
    public List<Transaccion> listarPorUsuario(Long usuarioId) {
        return transaccionRepository.findByUsuario_IdOrderByFechaDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public Transaccion buscarPorId(Long id) {
        return transaccionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la transacción con id " + id));
    }

    @Transactional
    public Transaccion guardar(Transaccion transaccion) {
        return transaccionRepository.save(transaccion);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!transaccionRepository.existsById(id)) {
            throw new EntityNotFoundException("No existe la transacción con id " + id);
        }
        transaccionRepository.deleteById(id);
    }
}
