package com.finantrack.finantrack.service;

import com.finantrack.finantrack.dto.CategoriaForm;
import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.Usuario;
import com.finantrack.finantrack.repository.CategoriaRepository;
import com.finantrack.finantrack.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public CategoriaService(CategoriaRepository categoriaRepository, UsuarioRepository usuarioRepository) {
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<Categoria> listar(String email) {
        Usuario usuario = buscarUsuario(email);
        List<Categoria> categorias = new ArrayList<>(categoriaRepository.findByUsuarioIsNullOrderByNombreAsc());
        categorias.addAll(categoriaRepository.findByUsuario_IdOrderByNombreAsc(usuario.getId()));
        return categorias;
    }

    @Transactional(readOnly = true)
    public Categoria buscarPropia(String email, Long id) {
        Usuario usuario = buscarUsuario(email);
        return categoriaRepository.findByIdAndUsuario_Id(id, usuario.getId())
                .orElseThrow(() -> new EntityNotFoundException("La categoría no existe o no te pertenece."));
    }

    @Transactional
    public Categoria crear(String email, CategoriaForm form) {
        validar(form);
        Usuario usuario = buscarUsuario(email);
        String nombre = form.getNombre().trim();
        if (categoriaRepository.existsByUsuario_IdAndNombreIgnoreCaseAndTipo(
                usuario.getId(), nombre, form.getTipo())) {
            throw new IllegalArgumentException("Ya existe una categoría de ese tipo con ese nombre.");
        }
        Categoria categoria = new Categoria();
        categoria.setUsuario(usuario);
        aplicar(categoria, form, nombre);
        return categoriaRepository.save(categoria);
    }

    @Transactional
    public Categoria actualizar(String email, Long id, CategoriaForm form) {
        validar(form);
        Categoria categoria = buscarPropia(email, id);
        if (!Boolean.TRUE.equals(categoria.getActiva())) {
            throw new IllegalArgumentException("No se puede editar una categoría desactivada.");
        }
        String nombre = form.getNombre().trim();
        Usuario usuario = categoria.getUsuario();
        if ((!categoria.getNombre().equalsIgnoreCase(nombre) || categoria.getTipo() != form.getTipo())
                && categoriaRepository.existsByUsuario_IdAndNombreIgnoreCaseAndTipo(
                        usuario.getId(), nombre, form.getTipo())) {
            throw new IllegalArgumentException("Ya existe una categoría de ese tipo con ese nombre.");
        }
        aplicar(categoria, form, nombre);
        return categoriaRepository.save(categoria);
    }

    @Transactional
    public void desactivar(String email, Long id) {
        Categoria categoria = buscarPropia(email, id);
        categoria.setActiva(false);
        categoriaRepository.save(categoria);
    }

    private void aplicar(Categoria categoria, CategoriaForm form, String nombre) {
        categoria.setNombre(nombre);
        categoria.setTipo(form.getTipo());
        String color = form.getColor();
        categoria.setColor(color == null || color.isBlank() ? null : color.toUpperCase(Locale.ROOT));
    }

    private void validar(CategoriaForm form) {
        if (form.getNombre() == null || form.getNombre().isBlank() || form.getNombre().trim().length() > 80) {
            throw new IllegalArgumentException("El nombre es obligatorio y admite hasta 80 caracteres.");
        }
        if (form.getTipo() == null) {
            throw new IllegalArgumentException("Selecciona el tipo de categoría.");
        }
        String color = form.getColor();
        if (color != null && !color.isBlank() && !color.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("El color debe tener formato hexadecimal, por ejemplo #38B889.");
        }
    }

    private Usuario buscarUsuario(String email) {
        String normalizado = email.trim().toLowerCase(Locale.ROOT);
        return usuarioRepository.findByEmail(normalizado)
                .orElseThrow(() -> new EntityNotFoundException("No se encontró el usuario autenticado."));
    }
}
