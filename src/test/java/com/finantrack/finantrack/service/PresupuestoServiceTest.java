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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresupuestoServiceTest {

    @Mock
    private PresupuestoRepository presupuestoRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private TransaccionRepository transaccionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private PresupuestoService service;
    private Usuario usuario;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        service = new PresupuestoService(
                presupuestoRepository, categoriaRepository, transaccionRepository, usuarioRepository);
        usuario = new Usuario();
        usuario.setId(12L);
        usuario.setEmail("persona@correo.com");
        categoria = new Categoria();
        categoria.setId(8L);
        categoria.setUsuario(usuario);
        categoria.setTipo(TipoMovimiento.GASTO);
        categoria.setActiva(true);
    }

    @Test
    void creaPresupuestoConCategoriaPropiaYPeriodoValido() {
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(categoriaRepository.findById(8L)).thenReturn(Optional.of(categoria));
        when(presupuestoRepository
                .existsByUsuario_IdAndCategoria_IdAndActivoTrueAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        12L, 8L, LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 1))).thenReturn(false);
        when(presupuestoRepository.save(any(Presupuesto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.crear(usuario.getEmail(), form());

        ArgumentCaptor<Presupuesto> captor = ArgumentCaptor.forClass(Presupuesto.class);
        verify(presupuestoRepository).save(captor.capture());
        assertEquals(usuario, captor.getValue().getUsuario());
        assertEquals(categoria, captor.getValue().getCategoria());
        assertEquals(Boolean.TRUE, captor.getValue().getActivo());
    }

    @Test
    void rechazaPeriodosDuplicadosSolapados() {
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(categoriaRepository.findById(8L)).thenReturn(Optional.of(categoria));
        when(presupuestoRepository
                .existsByUsuario_IdAndCategoria_IdAndActivoTrueAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        12L, 8L, LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 1))).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.crear(usuario.getEmail(), form()));
        verify(presupuestoRepository, never()).save(any());
    }

    @Test
    void rechazaCategoriaDeOtroUsuario() {
        Usuario otro = new Usuario();
        otro.setId(13L);
        categoria.setUsuario(otro);
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(categoriaRepository.findById(8L)).thenReturn(Optional.of(categoria));

        assertThrows(IllegalArgumentException.class, () -> service.crear(usuario.getEmail(), form()));
        verify(presupuestoRepository, never()).save(any());
    }

    private PresupuestoForm form() {
        PresupuestoForm form = new PresupuestoForm();
        form.setCategoriaId(8L);
        form.setMontoLimite(new BigDecimal("500.00"));
        form.setFechaInicio(LocalDate.of(2026, 5, 1));
        form.setFechaFin(LocalDate.of(2026, 5, 31));
        return form;
    }
}
