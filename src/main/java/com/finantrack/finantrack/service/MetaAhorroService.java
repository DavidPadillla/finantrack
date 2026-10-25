package com.finantrack.finantrack.service;

import com.finantrack.finantrack.dto.MetaAhorroForm;
import com.finantrack.finantrack.model.EstadoMeta;
import com.finantrack.finantrack.model.MetaAhorro;
import com.finantrack.finantrack.model.Usuario;
import com.finantrack.finantrack.repository.MetaAhorroRepository;
import com.finantrack.finantrack.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MetaAhorroService {

    private final MetaAhorroRepository metaAhorroRepository;
    private final UsuarioRepository usuarioRepository;

    public MetaAhorroService(MetaAhorroRepository metaAhorroRepository, UsuarioRepository usuarioRepository) {
        this.metaAhorroRepository = metaAhorroRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<MetaResumen> listar(String email) {
        Usuario usuario = buscarUsuario(email);
        List<MetaAhorro> metas = metaAhorroRepository.findByUsuario_IdOrderByFechaCreacionDesc(usuario.getId());
        if (metas.isEmpty()) {
            return List.of();
        }
        List<Long> ids = metas.stream().map(MetaAhorro::getId).toList();
        Map<Long, BigDecimal> aportes = new HashMap<>();
        metaAhorroRepository.totalAportesPorMeta(ids)
                .forEach(aporte -> aportes.put(aporte.getMetaId(), aporte.getTotal()));
        return metas.stream()
                .map(meta -> resumen(meta, aportes.getOrDefault(meta.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public MetaAhorroForm buscarFormularioPropio(String email, Long id) {
        MetaAhorro meta = buscarPropia(email, id);
        MetaAhorroForm form = new MetaAhorroForm();
        form.setNombre(meta.getNombre());
        form.setDescripcion(meta.getDescripcion());
        form.setMontoObjetivo(meta.getMontoObjetivo());
        form.setFechaObjetivo(meta.getFechaObjetivo());
        return form;
    }

    @Transactional
    public MetaAhorro crear(String email, MetaAhorroForm form) {
        validar(form);
        MetaAhorro meta = new MetaAhorro();
        meta.setUsuario(buscarUsuario(email));
        meta.setEstado(EstadoMeta.ACTIVA);
        aplicar(meta, form);
        return metaAhorroRepository.save(meta);
    }

    @Transactional
    public MetaAhorro actualizar(String email, Long id, MetaAhorroForm form) {
        validar(form);
        MetaAhorro meta = buscarPropia(email, id);
        if (meta.getEstado() != EstadoMeta.ACTIVA) {
            throw new IllegalArgumentException("Solo se pueden editar metas activas.");
        }
        aplicar(meta, form);
        return metaAhorroRepository.save(meta);
    }

    @Transactional
    public void cancelar(String email, Long id) {
        MetaAhorro meta = buscarPropia(email, id);
        if (meta.getEstado() != EstadoMeta.ACTIVA) {
            throw new IllegalArgumentException("Solo se pueden cancelar metas activas.");
        }
        meta.setEstado(EstadoMeta.CANCELADA);
        metaAhorroRepository.save(meta);
    }

    @Transactional(readOnly = true)
    MetaAhorro buscarPropia(String email, Long id) {
        Usuario usuario = buscarUsuario(email);
        return metaAhorroRepository.findByIdAndUsuario_Id(id, usuario.getId())
                .orElseThrow(() -> new EntityNotFoundException("La meta no existe o no te pertenece."));
    }

    private MetaResumen resumen(MetaAhorro meta, BigDecimal aportado) {
        BigDecimal objetivo = meta.getMontoObjetivo();
        BigDecimal restante = objetivo.subtract(aportado).max(BigDecimal.ZERO);
        int porcentaje = objetivo.signum() > 0
                ? aportado.multiply(BigDecimal.valueOf(100))
                        .divide(objetivo, 0, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100))
                        .intValue()
                : 0;
        return new MetaResumen(
                meta.getId(),
                meta.getNombre(),
                meta.getDescripcion(),
                objetivo,
                aportado,
                restante,
                porcentaje,
                meta.getFechaObjetivo(),
                meta.getEstado()
        );
    }

    private void aplicar(MetaAhorro meta, MetaAhorroForm form) {
        meta.setNombre(form.getNombre().trim());
        meta.setDescripcion(form.getDescripcion() == null || form.getDescripcion().isBlank()
                ? null
                : form.getDescripcion().trim());
        meta.setMontoObjetivo(form.getMontoObjetivo());
        meta.setFechaObjetivo(form.getFechaObjetivo());
    }

    private void validar(MetaAhorroForm form) {
        BigDecimal objetivo = form.getMontoObjetivo();
        if (form.getNombre() == null || form.getNombre().isBlank()
                || form.getNombre().trim().length() > 120) {
            throw new IllegalArgumentException("El nombre es obligatorio y admite hasta 120 caracteres.");
        }
        if (form.getDescripcion() != null && form.getDescripcion().length() > 255) {
            throw new IllegalArgumentException("La descripción no puede superar los 255 caracteres.");
        }
        if (objetivo == null || objetivo.signum() <= 0 || objetivo.scale() > 2
                || objetivo.precision() - objetivo.scale() > 12) {
            throw new IllegalArgumentException("El monto objetivo debe ser positivo y admitir hasta 12 enteros y 2 decimales.");
        }
    }

    private Usuario buscarUsuario(String email) {
        String normalizado = email.trim().toLowerCase(Locale.ROOT);
        return usuarioRepository.findByEmail(normalizado)
                .orElseThrow(() -> new EntityNotFoundException("No se encontró el usuario autenticado."));
    }

    public record MetaResumen(
            Long id,
            String nombre,
            String descripcion,
            BigDecimal objetivo,
            BigDecimal aportado,
            BigDecimal restante,
            int porcentaje,
            LocalDate fechaObjetivo,
            EstadoMeta estado) {
    }
}
