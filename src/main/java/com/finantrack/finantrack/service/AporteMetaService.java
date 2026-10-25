package com.finantrack.finantrack.service;

import com.finantrack.finantrack.dto.AporteMetaForm;
import com.finantrack.finantrack.model.AporteMeta;
import com.finantrack.finantrack.model.EstadoMeta;
import com.finantrack.finantrack.model.MetaAhorro;
import com.finantrack.finantrack.repository.AporteMetaRepository;
import com.finantrack.finantrack.repository.MetaAhorroRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class AporteMetaService {

    private final AporteMetaRepository aporteMetaRepository;
    private final MetaAhorroRepository metaAhorroRepository;
    private final MetaAhorroService metaAhorroService;

    public AporteMetaService(
            AporteMetaRepository aporteMetaRepository,
            MetaAhorroRepository metaAhorroRepository,
            MetaAhorroService metaAhorroService) {
        this.aporteMetaRepository = aporteMetaRepository;
        this.metaAhorroRepository = metaAhorroRepository;
        this.metaAhorroService = metaAhorroService;
    }

    @Transactional(readOnly = true)
    public List<AporteMeta> listar(String email, Long metaId) {
        MetaAhorro meta = metaAhorroService.buscarPropia(email, metaId);
        return aporteMetaRepository.findByMeta_IdOrderByFechaDesc(meta.getId());
    }

    @Transactional
    public AporteMeta registrar(String email, Long metaId, AporteMetaForm form) {
        validar(form);
        MetaAhorro meta = metaAhorroService.buscarPropia(email, metaId);
        if (meta.getEstado() != EstadoMeta.ACTIVA) {
            throw new IllegalArgumentException("Solo se pueden aportar fondos a metas activas.");
        }
        AporteMeta aporte = new AporteMeta();
        aporte.setMeta(meta);
        aporte.setMonto(form.getMonto());
        aporte.setDescripcion(form.getDescripcion() == null || form.getDescripcion().isBlank()
                ? null
                : form.getDescripcion().trim());
        aporte.setFecha(form.getFecha());
        aporteMetaRepository.save(aporte);

        BigDecimal total = metaAhorroRepository.totalAportesPorMeta(List.of(meta.getId())).stream()
                .map(MetaAhorroRepository.AportesPorMeta::getTotal)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        if (total.compareTo(meta.getMontoObjetivo()) >= 0) {
            meta.setEstado(EstadoMeta.COMPLETADA);
            metaAhorroRepository.save(meta);
        }
        return aporte;
    }

    private void validar(AporteMetaForm form) {
        BigDecimal monto = form.getMonto();
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2
                || monto.precision() - monto.scale() > 12) {
            throw new IllegalArgumentException("El aporte debe ser positivo y admitir hasta 12 enteros y 2 decimales.");
        }
        if (form.getFecha() == null) {
            throw new IllegalArgumentException("La fecha del aporte es obligatoria.");
        }
        if (form.getFecha().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha del aporte no puede ser futura.");
        }
        if (form.getDescripcion() != null && form.getDescripcion().length() > 255) {
            throw new IllegalArgumentException("La descripción no puede superar los 255 caracteres.");
        }
    }
}
