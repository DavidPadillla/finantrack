package com.finantrack.finantrack.service;

import com.finantrack.finantrack.dto.AporteMetaForm;
import com.finantrack.finantrack.model.AporteMeta;
import com.finantrack.finantrack.model.EstadoMeta;
import com.finantrack.finantrack.model.MetaAhorro;
import com.finantrack.finantrack.model.Usuario;
import com.finantrack.finantrack.repository.AporteMetaRepository;
import com.finantrack.finantrack.repository.MetaAhorroRepository;
import com.finantrack.finantrack.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AporteMetaServiceTest {

    @Mock
    private AporteMetaRepository aporteRepository;
    @Mock
    private MetaAhorroRepository metaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private AporteMetaService service;
    private MetaAhorro meta;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        MetaAhorroService metaService = new MetaAhorroService(metaRepository, usuarioRepository);
        service = new AporteMetaService(aporteRepository, metaRepository, metaService);
        usuario = new Usuario();
        usuario.setId(12L);
        usuario.setEmail("persona@correo.com");
        meta = new MetaAhorro();
        meta.setId(3L);
        meta.setUsuario(usuario);
        meta.setMontoObjetivo(new BigDecimal("100.00"));
        meta.setEstado(EstadoMeta.ACTIVA);
    }

    @Test
    void aporteQueAlcanzaObjetivoCompletaMeta() {
        prepararMetaPropia();
        when(metaRepository.totalAportesPorMeta(List.of(3L))).thenReturn(List.of(
                new MetaAhorroRepository.AportesPorMeta() {
                    @Override
                    public Long getMetaId() {
                        return 3L;
                    }

                    @Override
                    public BigDecimal getTotal() {
                        return new BigDecimal("100.00");
                    }
                }));

        service.registrar(usuario.getEmail(), 3L, form("100.00"));

        assertEquals(EstadoMeta.COMPLETADA, meta.getEstado());
        verify(aporteRepository).save(any(AporteMeta.class));
        verify(metaRepository).save(meta);
    }

    @Test
    void noPermiteImportesNoPositivos() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(usuario.getEmail(), 3L, form("0")));
        verify(aporteRepository, never()).save(any());
    }

    @Test
    void rechazaMetaDeOtroUsuario() {
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(metaRepository.findByIdAndUsuario_Id(3L, 12L)).thenReturn(Optional.empty());
        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> service.registrar(usuario.getEmail(), 3L, form("10.00")));
        verify(aporteRepository, never()).save(any());
    }

    private AporteMetaForm form(String monto) {
        AporteMetaForm form = new AporteMetaForm();
        form.setMonto(new BigDecimal(monto));
        form.setFecha(LocalDate.now());
        return form;
    }

    private void prepararMetaPropia() {
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(metaRepository.findByIdAndUsuario_Id(3L, 12L)).thenReturn(Optional.of(meta));
    }
}
