package com.finantrack.finantrack.service;

import com.finantrack.finantrack.dto.TransaccionForm;
import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.Transaccion;
import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.model.Usuario;
import com.finantrack.finantrack.repository.CategoriaRepository;
import com.finantrack.finantrack.repository.TransaccionRepository;
import com.finantrack.finantrack.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransaccionServiceTest {

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private TransaccionService transaccionService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        transaccionService = new TransaccionService(
                transaccionRepository, categoriaRepository, usuarioRepository);
        usuario = new Usuario();
        usuario.setId(7L);
        usuario.setEmail("persona@correo.com");
    }

    @Test
    void creaTransaccionConUsuarioAutenticadoYCategoriaPropia() {
        Categoria categoria = categoria(22L, usuario, TipoMovimiento.GASTO);
        when(usuarioRepository.findByEmail("persona@correo.com")).thenReturn(Optional.of(usuario));
        when(categoriaRepository.findById(22L)).thenReturn(Optional.of(categoria));

        TransaccionForm form = formulario(TipoMovimiento.GASTO, "34.50", 22L);
        transaccionService.crear("persona@correo.com", form);

        ArgumentCaptor<Transaccion> captor = ArgumentCaptor.forClass(Transaccion.class);
        verify(transaccionRepository).save(captor.capture());
        assertEquals(usuario, captor.getValue().getUsuario());
        assertEquals(categoria, captor.getValue().getCategoria());
        assertEquals(new BigDecimal("34.50"), captor.getValue().getMonto());
        assertEquals("Compra", captor.getValue().getDescripcion());
    }

    @Test
    void rechazaCategoriaDeOtroUsuarioSinGuardar() {
        Usuario otroUsuario = new Usuario();
        otroUsuario.setId(8L);
        Categoria categoriaAjena = categoria(22L, otroUsuario, TipoMovimiento.GASTO);
        when(usuarioRepository.findByEmail("persona@correo.com")).thenReturn(Optional.of(usuario));
        when(categoriaRepository.findById(22L)).thenReturn(Optional.of(categoriaAjena));

        assertThrows(IllegalArgumentException.class,
                () -> transaccionService.crear("persona@correo.com",
                        formulario(TipoMovimiento.GASTO, "34.50", 22L)));

        verify(transaccionRepository, never()).save(any());
    }

    @Test
    void rechazaEdicionDeTransaccionDeOtroUsuario() {
        when(usuarioRepository.findByEmail("persona@correo.com")).thenReturn(Optional.of(usuario));

        assertThrows(EntityNotFoundException.class,
                () -> transaccionService.actualizar("persona@correo.com", 99L,
                        formulario(TipoMovimiento.INGRESO, "100.00", null)));

        verify(transaccionRepository, never()).save(any());
    }

    @Test
    void actualizaSoloCamposPermitidosDeUnaTransaccionPropia() {
        Transaccion existente = new Transaccion();
        existente.setId(14L);
        existente.setUsuario(usuario);
        when(usuarioRepository.findByEmail("persona@correo.com")).thenReturn(Optional.of(usuario));
        when(transaccionRepository.findByIdAndUsuario_Id(14L, usuario.getId()))
                .thenReturn(Optional.of(existente));

        TransaccionForm form = formulario(TipoMovimiento.INGRESO, "250.00", null);
        transaccionService.actualizar("persona@correo.com", 14L, form);

        assertEquals(usuario, existente.getUsuario());
        assertEquals(TipoMovimiento.INGRESO, existente.getTipo());
        assertEquals(new BigDecimal("250.00"), existente.getMonto());
        assertNull(existente.getCategoria());
        verify(transaccionRepository).save(existente);
    }

    @Test
    void eliminaSoloTransaccionPropia() {
        Transaccion existente = new Transaccion();
        existente.setId(14L);
        existente.setUsuario(usuario);
        when(usuarioRepository.findByEmail("persona@correo.com")).thenReturn(Optional.of(usuario));
        when(transaccionRepository.findByIdAndUsuario_Id(14L, usuario.getId()))
                .thenReturn(Optional.of(existente));

        transaccionService.eliminar("persona@correo.com", 14L);

        verify(transaccionRepository).delete(existente);
    }

    @Test
    void rechazaImporteCero() {
        TransaccionForm form = formulario(TipoMovimiento.GASTO, "0", null);

        assertThrows(IllegalArgumentException.class,
                () -> transaccionService.crear("persona@correo.com", form));

        verify(usuarioRepository, never()).findByEmail(any());
        verify(transaccionRepository, never()).save(any());
    }

    private TransaccionForm formulario(TipoMovimiento tipo, String monto, Long categoriaId) {
        TransaccionForm form = new TransaccionForm();
        form.setTipo(tipo);
        form.setMonto(new BigDecimal(monto));
        form.setDescripcion(" Compra ");
        form.setFecha(LocalDate.now());
        form.setCategoriaId(categoriaId);
        return form;
    }

    private Categoria categoria(Long id, Usuario propietario, TipoMovimiento tipo) {
        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setUsuario(propietario);
        categoria.setTipo(tipo);
        categoria.setActiva(true);
        categoria.setNombre("Categoría propia");
        return categoria;
    }
}
