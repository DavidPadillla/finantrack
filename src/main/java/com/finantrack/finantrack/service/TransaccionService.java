package com.finantrack.finantrack.service;

import com.finantrack.finantrack.dto.TransaccionForm;
import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.model.Transaccion;
import com.finantrack.finantrack.model.Usuario;
import com.finantrack.finantrack.repository.CategoriaRepository;
import com.finantrack.finantrack.repository.TransaccionRepository;
import com.finantrack.finantrack.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

@Service
public class TransaccionService {

    public static final int TAMANO_PAGINA = 12;

    private final TransaccionRepository transaccionRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public TransaccionService(
            TransaccionRepository transaccionRepository,
            CategoriaRepository categoriaRepository,
            UsuarioRepository usuarioRepository) {
        this.transaccionRepository = transaccionRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public Page<Transaccion> buscar(
            String email,
            LocalDate desde,
            LocalDate hasta,
            TipoMovimiento tipo,
            Long categoriaId,
            int pagina) {
        validarFechas(desde, hasta);
        Usuario usuario = buscarUsuario(email);
        if (categoriaId != null) {
            validarPropiedadCategoria(categoriaId, usuario);
        }
        Pageable pageable = PageRequest.of(
                Math.max(pagina, 0),
                TAMANO_PAGINA,
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id")));
        return transaccionRepository.findAll(filtros(usuario, desde, hasta, tipo, categoriaId), pageable);
    }

    @Transactional(readOnly = true)
    public List<TransaccionResumen> exportar(
            String email,
            LocalDate desde,
            LocalDate hasta,
            TipoMovimiento tipo,
            Long categoriaId) {
        validarFechas(desde, hasta);
        Usuario usuario = buscarUsuario(email);
        if (categoriaId != null) {
            validarPropiedadCategoria(categoriaId, usuario);
        }
        return transaccionRepository.findAll(
                filtros(usuario, desde, hasta, tipo, categoriaId),
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id")))
                .stream()
                .map(transaccion -> new TransaccionResumen(
                        transaccion.getId(),
                        transaccion.getDescripcion(),
                        nombreCategoriaVisible(transaccion.getCategoria(), usuario),
                        transaccion.getFecha(),
                        transaccion.getTipo(),
                        transaccion.getMonto()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Categoria> categoriasDisponibles(String email) {
        return categoriaRepository.buscarDisponibles(buscarUsuario(email).getId(), null);
    }

    @Transactional(readOnly = true)
    public List<Categoria> categoriasDisponibles(String email, Long categoriaActualId) {
        List<Categoria> categorias = new java.util.ArrayList<>(categoriasDisponibles(email));
        if (categoriaActualId != null && categorias.stream().noneMatch(categoria -> categoria.getId().equals(categoriaActualId))) {
            Usuario usuario = buscarUsuario(email);
            Categoria categoriaActual = categoriaRepository.findById(categoriaActualId)
                    .orElseThrow(() -> new EntityNotFoundException("No se encontró la categoría de esta transacción."));
            if (esVisiblePara(categoriaActual, usuario)) {
                categorias.add(categoriaActual);
            }
        }
        return categorias;
    }

    @Transactional(readOnly = true)
    public TransaccionForm buscarFormularioPropio(String email, Long transaccionId) {
        Usuario usuario = buscarUsuario(email);
        Transaccion transaccion = transaccionRepository.findByIdAndUsuario_Id(transaccionId, usuario.getId())
                .orElseThrow(() -> new EntityNotFoundException("No se encontró la transacción solicitada."));
        TransaccionForm form = new TransaccionForm();
        form.setTipo(transaccion.getTipo());
        form.setMonto(transaccion.getMonto());
        form.setDescripcion(transaccion.getDescripcion());
        form.setFecha(transaccion.getFecha());
        Categoria categoria = transaccion.getCategoria();
        if (categoria != null && esVisiblePara(categoria, usuario)) {
            form.setCategoriaId(categoria.getId());
        }
        return form;
    }

    @Transactional(readOnly = true)
    public Page<TransaccionResumen> listar(
            String email,
            LocalDate desde,
            LocalDate hasta,
            TipoMovimiento tipo,
            Long categoriaId,
            int pagina) {
        Usuario usuario = buscarUsuario(email);
        return buscar(email, desde, hasta, tipo, categoriaId, pagina)
                .map(transaccion -> new TransaccionResumen(
                        transaccion.getId(),
                        transaccion.getDescripcion(),
                        nombreCategoriaVisible(transaccion.getCategoria(), usuario),
                        transaccion.getFecha(),
                        transaccion.getTipo(),
                        transaccion.getMonto()
                ));
    }

    @Transactional
    public Transaccion crear(String email, TransaccionForm form) {
        validar(form);
        Usuario usuario = buscarUsuario(email);
        Transaccion transaccion = new Transaccion();
        transaccion.setUsuario(usuario);
        aplicarCambios(transaccion, form, usuario);
        return transaccionRepository.save(transaccion);
    }

    @Transactional
    public Transaccion actualizar(String email, Long transaccionId, TransaccionForm form) {
        validar(form);
        Usuario usuario = buscarUsuario(email);
        Transaccion transaccion = transaccionRepository.findByIdAndUsuario_Id(transaccionId, usuario.getId())
                .orElseThrow(() -> new EntityNotFoundException("No se encontró la transacción solicitada."));
        aplicarCambios(transaccion, form, usuario);
        return transaccionRepository.save(transaccion);
    }

    @Transactional
    public void eliminar(String email, Long transaccionId) {
        Usuario usuario = buscarUsuario(email);
        Transaccion transaccion = transaccionRepository.findByIdAndUsuario_Id(transaccionId, usuario.getId())
                .orElseThrow(() -> new EntityNotFoundException("No se encontró la transacción solicitada."));
        transaccionRepository.delete(transaccion);
    }

    private void aplicarCambios(Transaccion transaccion, TransaccionForm form, Usuario usuario) {
        transaccion.setTipo(form.getTipo());
        transaccion.setMonto(form.getMonto());
        transaccion.setDescripcion(form.getDescripcion() == null || form.getDescripcion().isBlank()
                ? null
                : form.getDescripcion().trim());
        transaccion.setFecha(form.getFecha());
        transaccion.setCategoria(buscarCategoria(
                form.getCategoriaId(), form.getTipo(), usuario, transaccion.getCategoria()));
    }

    private Categoria buscarCategoria(
            Long categoriaId,
            TipoMovimiento tipo,
            Usuario usuario,
            Categoria categoriaActual) {
        if (categoriaId == null) {
            return null;
        }
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("La categoría seleccionada no existe."));
        if (!esVisiblePara(categoria, usuario)) {
            throw new IllegalArgumentException("No puedes usar una categoría de otro usuario.");
        }
        if (categoria.getTipo() != tipo) {
            throw new IllegalArgumentException("La categoría debe tener el mismo tipo que la transacción.");
        }
        boolean conservarInactiva = categoriaActual != null
                && categoriaActual.getId().equals(categoria.getId());
        if (!Boolean.TRUE.equals(categoria.getActiva()) && !conservarInactiva) {
            throw new IllegalArgumentException("La categoría seleccionada está inactiva.");
        }
        return categoria;
    }

    private void validarPropiedadCategoria(Long categoriaId, Usuario usuario) {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("La categoría seleccionada no existe."));
        if (!esVisiblePara(categoria, usuario)) {
            throw new IllegalArgumentException("No puedes usar una categoría de otro usuario.");
        }
    }

    private Specification<Transaccion> filtros(
            Usuario usuario,
            LocalDate desde,
            LocalDate hasta,
            TipoMovimiento tipo,
            Long categoriaId) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("usuario").get("id"), usuario.getId()));
            if (desde != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("fecha"), desde));
            }
            if (hasta != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("fecha"), hasta));
            }
            if (tipo != null) {
                predicates.add(criteriaBuilder.equal(root.get("tipo"), tipo));
            }
            if (categoriaId != null) {
                predicates.add(criteriaBuilder.equal(root.get("categoria").get("id"), categoriaId));
            }
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private void validarFechas(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }

    private boolean esVisiblePara(Categoria categoria, Usuario usuario) {
        return categoria.getUsuario() == null
                || categoria.getUsuario().getId().equals(usuario.getId());
    }

    private String nombreCategoriaVisible(Categoria categoria, Usuario usuario) {
        return categoria == null || !esVisiblePara(categoria, usuario)
                ? "Sin categoría"
                : categoria.getNombre();
    }

    private Usuario buscarUsuario(String email) {
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        return usuarioRepository.findByEmail(emailNormalizado)
                .orElseThrow(() -> new EntityNotFoundException("No se encontró el usuario autenticado."));
    }

    private void validar(TransaccionForm form) {
        if (form.getTipo() == null) {
            throw new IllegalArgumentException("Selecciona el tipo de movimiento.");
        }
        if (form.getMonto() == null || form.getMonto().signum() <= 0
                || form.getMonto().scale() > 2 || form.getMonto().precision() - form.getMonto().scale() > 12) {
            throw new IllegalArgumentException("El importe debe ser positivo y tener como máximo 12 enteros y 2 decimales.");
        }
        if (form.getFecha() == null) {
            throw new IllegalArgumentException("La fecha es obligatoria.");
        }
        if (form.getFecha().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha no puede ser futura.");
        }
        if (form.getDescripcion() != null && form.getDescripcion().length() > 255) {
            throw new IllegalArgumentException("La descripción no puede superar los 255 caracteres.");
        }
    }

    public record TransaccionResumen(
            Long id,
            String descripcion,
            String categoria,
            LocalDate fecha,
            TipoMovimiento tipo,
            BigDecimal monto) {
    }
}
