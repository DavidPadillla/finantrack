package com.finantrack.finantrack.service;

import com.finantrack.finantrack.dto.CategoriaForm;
import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.model.Usuario;
import com.finantrack.finantrack.repository.CategoriaRepository;
import com.finantrack.finantrack.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private CategoriaService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new CategoriaService(categoriaRepository, usuarioRepository);
        usuario = new Usuario();
        usuario.setId(12L);
        usuario.setEmail("persona@correo.com");
    }

    @Test
    void creaCategoriaDelUsuarioAutenticado() {
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(categoriaRepository.existsByUsuario_IdAndNombreIgnoreCaseAndTipo(
                12L, "Transporte", TipoMovimiento.GASTO)).thenReturn(false);
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoriaForm form = new CategoriaForm();
        form.setNombre(" Transporte ");
        form.setTipo(TipoMovimiento.GASTO);
        form.setColor("#38b889");

        Categoria creada = service.crear(usuario.getEmail(), form);

        assertEquals(usuario, creada.getUsuario());
        assertEquals("Transporte", creada.getNombre());
        assertEquals("#38B889", creada.getColor());
        assertEquals(TipoMovimiento.GASTO, creada.getTipo());
    }

    @Test
    void desactivaSinEliminarLaCategoria() {
        Categoria categoria = new Categoria();
        categoria.setId(4L);
        categoria.setUsuario(usuario);
        categoria.setActiva(true);
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(categoriaRepository.findByIdAndUsuario_Id(4L, 12L)).thenReturn(Optional.of(categoria));

        service.desactivar(usuario.getEmail(), 4L);

        assertFalse(categoria.getActiva());
        verify(categoriaRepository).save(categoria);
        verify(categoriaRepository, never()).deleteById(4L);
    }

    @Test
    void noPermiteEditarCategoriaAjena() {
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(categoriaRepository.findByIdAndUsuario_Id(4L, 12L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.buscarPropia(usuario.getEmail(), 4L));
        verify(categoriaRepository, never()).save(any());
    }
}
