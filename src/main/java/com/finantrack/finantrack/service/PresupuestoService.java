package com.finantrack.finantrack.service;

import com.finantrack.finantrack.dto.PresupuestoForm;
import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.Presupuesto;
import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.model.Usuario;
import com.finantrack.finantrack.repository.CategoriaRepository;
import com.finantrack.finantrack.repository.PresupuestoRepository;
import com.finantrack.finantrack.repository.TransaccionRepository;
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
public class PresupuestoService {

    private final PresupuestoRepository presupuestoRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransaccionRepository transaccionRepository;
    private final UsuarioRepository usuarioRepository;

    public PresupuestoService(
            PresupuestoRepository presupuestoRepository,
            CategoriaRepository categoriaRepository,
            TransaccionRepository transaccionRepository,
            UsuarioRepository usuarioRepository) {
        this.presupuestoRepository = presupuestoRepository;
        this.categoriaRepository = categoriaRepository;
        this.transaccionRepository = transaccionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<PresupuestoResumen> listar(String email) {
        Usuario usuario = buscarUsuario(email);
        List<Presupuesto> presupuestos = presupuestoRepository.findByUsuario_IdOrderByFechaInicioDesc(usuario.getId());
        Map<Long, BigDecimal> gastos = cargarGastos(usuario.getId());
        return presupuestos.stream()
                .map(presupuesto -> resumen(presupuesto, gastos.getOrDefault(presupuesto.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PresupuestoForm buscarFormularioPropio(String email, Long id) {
        Presupuesto presupuesto = buscarPropio(email, id);
        PresupuestoForm form = new PresupuestoForm();
        form.setCategoriaId(presupuesto.getCategoria().getId());
        form.setMontoLimite(presupuesto.getMontoLimite());
        form.setFechaInicio(presupuesto.getFechaInicio());
        form.setFechaFin(presupuesto.getFechaFin());
        return form;
    }

    @Transactional
    public Presupuesto crear(String email, PresupuestoForm form) {
        validar(form);
        Usuario usuario = buscarUsuario(email);
        Categoria categoria = buscarCategoria(form.getCategoriaId(), usuario);
        validarDuplicado(usuario, categoria, form, null);
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.setUsuario(usuario);
        presupuesto.setCategoria(categoria);
        presupuesto.setActivo(true);
        aplicar(presupuesto, form);
        return presupuestoRepository.save(presupuesto);
    }

    @Transactional
    public Presupuesto actualizar(String email, Long id, PresupuestoForm form) {
        validar(form);
        Presupuesto presupuesto = buscarPropio(email, id);
        if (!Boolean.TRUE.equals(presupuesto.getActivo())) {
            throw new IllegalArgumentException("No se puede editar un presupuesto desactivado.");
        }
        Usuario usuario = presupuesto.getUsuario();
        Categoria categoria = buscarCategoria(form.getCategoriaId(), usuario);
        validarDuplicado(usuario, categoria, form, id);
        presupuesto.setCategoria(categoria);
        aplicar(presupuesto, form);
        return presupuestoRepository.save(presupuesto);
    }

    @Transactional
    public void desactivar(String email, Long id) {
        Presupuesto presupuesto = buscarPropio(email, id);
        presupuesto.setActivo(false);
        presupuestoRepository.save(presupuesto);
    }

    private Map<Long, BigDecimal> cargarGastos(Long usuarioId) {
        Map<Long, BigDecimal> gastos = new HashMap<>();
        transaccionRepository.totalesPorPresupuesto(usuarioId)
                .forEach(total -> gastos.put(total.getPresupuestoId(), total.getTotal()));
        return gastos;
    }

    private PresupuestoResumen resumen(Presupuesto presupuesto, BigDecimal gasto) {
        BigDecimal limite = presupuesto.getMontoLimite();
        int porcentaje = limite.signum() > 0
                ? gasto.multiply(BigDecimal.valueOf(100)).divide(limite, 0, RoundingMode.HALF_UP).intValue()
                : 0;
        BigDecimal restante = limite.subtract(gasto).max(BigDecimal.ZERO);
        return new PresupuestoResumen(
                presupuesto.getId(),
                presupuesto.getCategoria().getNombre(),
                limite,
                gasto,
                restante,
                porcentaje,
                Math.min(porcentaje, 100),
                presupuesto.getFechaInicio(),
                presupuesto.getFechaFin(),
                Boolean.TRUE.equals(presupuesto.getActivo())
        );
    }

    private Presupuesto buscarPropio(String email, Long id) {
        Usuario usuario = buscarUsuario(email);
        return presupuestoRepository.findByIdAndUsuario_Id(id, usuario.getId())
                .orElseThrow(() -> new EntityNotFoundException("El presupuesto no existe o no te pertenece."));
    }

    private Categoria buscarCategoria(Long id, Usuario usuario) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La categoría seleccionada no existe."));
        boolean visible = categoria.getUsuario() == null
                || categoria.getUsuario().getId().equals(usuario.getId());
        if (!visible) {
            throw new IllegalArgumentException("No puedes usar una categoría de otro usuario.");
        }
        if (!Boolean.TRUE.equals(categoria.getActiva())) {
            throw new IllegalArgumentException("La categoría seleccionada está desactivada.");
        }
        if (categoria.getTipo() != TipoMovimiento.GASTO) {
            throw new IllegalArgumentException("Un presupuesto solo puede asociarse a una categoría de gasto.");
        }
        return categoria;
    }

    private void validarDuplicado(Usuario usuario, Categoria categoria, PresupuestoForm form, Long id) {
        boolean existe = id == null
                ? presupuestoRepository.existsByUsuario_IdAndCategoria_IdAndActivoTrueAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        usuario.getId(), categoria.getId(), form.getFechaFin(), form.getFechaInicio())
                : presupuestoRepository.existsByUsuario_IdAndCategoria_IdAndActivoTrueAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqualAndIdNot(
                        usuario.getId(), categoria.getId(), form.getFechaFin(), form.getFechaInicio(), id);
        if (existe) {
            throw new IllegalArgumentException(
                    "Ya existe un presupuesto activo para esa categoría durante parte de ese período.");
        }
    }

    private void aplicar(Presupuesto presupuesto, PresupuestoForm form) {
        presupuesto.setMontoLimite(form.getMontoLimite());
        presupuesto.setFechaInicio(form.getFechaInicio());
        presupuesto.setFechaFin(form.getFechaFin());
    }

    private void validar(PresupuestoForm form) {
        BigDecimal monto = form.getMontoLimite();
        if (form.getCategoriaId() == null) {
            throw new IllegalArgumentException("Selecciona una categoría.");
        }
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2
                || monto.precision() - monto.scale() > 12) {
            throw new IllegalArgumentException("El límite debe ser positivo y admitir hasta 12 enteros y 2 decimales.");
        }
        if (form.getFechaInicio() == null || form.getFechaFin() == null) {
            throw new IllegalArgumentException("Las fechas inicial y final son obligatorias.");
        }
        if (form.getFechaFin().isBefore(form.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
        }
    }

    private Usuario buscarUsuario(String email) {
        String normalizado = email.trim().toLowerCase(Locale.ROOT);
        return usuarioRepository.findByEmail(normalizado)
                .orElseThrow(() -> new EntityNotFoundException("No se encontró el usuario autenticado."));
    }

    public record PresupuestoResumen(
            Long id,
            String categoria,
            BigDecimal limite,
            BigDecimal gastado,
            BigDecimal restante,
            int porcentaje,
            int porcentajeBarra,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            boolean activo) {
    }
}
