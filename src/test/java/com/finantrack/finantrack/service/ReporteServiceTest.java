package com.finantrack.finantrack.service;

import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.model.Usuario;
import com.finantrack.finantrack.repository.CategoriaRepository;
import com.finantrack.finantrack.repository.TransaccionRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock
    private TransaccionRepository transaccionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private TransaccionService transaccionService;
    @Mock
    private PresupuestoService presupuestoService;
    @Mock
    private MetaAhorroService metaAhorroService;

    private ReporteService reporteService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        reporteService = new ReporteService(
                transaccionRepository,
                usuarioRepository,
                categoriaRepository,
                transaccionService,
                presupuestoService,
                metaAhorroService);
        usuario = new Usuario();
        usuario.setId(15L);
        usuario.setEmail("persona@correo.com");
    }

    @Test
    void calculaTotalesComparacionDistribucionYAlertaConDatosDelUsuario() {
        LocalDate hasta = LocalDate.now();
        LocalDate desde = hasta.minusDays(14);
        List<TransaccionRepository.ReporteAgregado> actuales = List.of(
                agregado(hasta, TipoMovimiento.INGRESO, null, "Sin categoría", "2000.00"),
                agregado(hasta, TipoMovimiento.GASTO, 2L, "Alimentación", "800.00"),
                agregado(hasta, TipoMovimiento.GASTO, 3L, "Hogar", "400.00")
        );
        List<TransaccionRepository.ReporteAgregado> anteriores = List.of(
                agregado(desde.minusDays(1), TipoMovimiento.INGRESO, null, "Sin categoría", "1800.00"),
                agregado(desde.minusDays(1), TipoMovimiento.GASTO, 2L, "Alimentación", "500.00"),
                agregado(desde.minusDays(1), TipoMovimiento.GASTO, 3L, "Hogar", "500.00")
        );
        when(usuarioRepository.findByEmail("persona@correo.com")).thenReturn(Optional.of(usuario));
        when(transaccionRepository.agregadosDeReporte(15L, desde, hasta)).thenReturn(actuales);
        when(transaccionRepository.agregadosDeReporte(15L, desde.minusDays(15), desde.minusDays(1)))
                .thenReturn(anteriores);
        when(presupuestoService.listar("persona@correo.com")).thenReturn(List.of());
        when(metaAhorroService.listar("persona@correo.com")).thenReturn(List.of());

        ReporteService.ReportData reporte = reporteService.generar(
                "persona@correo.com", desde, hasta, null, null);

        assertEquals(new BigDecimal("2000.00"), reporte.ingresos());
        assertEquals(new BigDecimal("1200.00"), reporte.gastos());
        assertEquals(new BigDecimal("800.00"), reporte.ahorroNeto());
        assertEquals(new BigDecimal("66.7"), reporte.categorias().get(0).porcentaje());
        assertEquals("Alimentación", reporte.aumentosGasto().get(0).nombre());
        assertEquals(new BigDecimal("60.0"), reporte.aumentosGasto().get(0).variacionPorcentual());
        assertEquals("Flujo positivo", reporte.salud().estado());
    }

    @Test
    void rechazaCategoriaDeOtroUsuarioAntesDeConsultarAgregados() {
        LocalDate hoy = LocalDate.now();
        Usuario otroUsuario = new Usuario();
        otroUsuario.setId(99L);
        Categoria categoriaAjena = new Categoria();
        categoriaAjena.setId(32L);
        categoriaAjena.setUsuario(otroUsuario);
        when(usuarioRepository.findByEmail("persona@correo.com")).thenReturn(Optional.of(usuario));
        when(categoriaRepository.findById(32L)).thenReturn(Optional.of(categoriaAjena));

        assertThrows(IllegalArgumentException.class,
                () -> reporteService.generar("persona@correo.com", hoy.minusDays(1), hoy, null, 32L));

        verify(transaccionRepository, never()).agregadosDeReporte(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rechazaPeríodoSuperiorATresAños() {
        LocalDate hasta = LocalDate.now();

        assertThrows(IllegalArgumentException.class,
                () -> reporteService.generar(
                        "persona@correo.com", hasta.minusDays(1096), hasta, null, null));

        verify(usuarioRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void csvProtegeValoresQuePodrianEjecutarFormulasEnHojasDeCalculo() {
        LocalDate fecha = LocalDate.now();
        when(transaccionService.exportar(
                "persona@correo.com", fecha.minusDays(1), fecha, null, null))
                .thenReturn(List.of(new TransaccionService.TransaccionResumen(
                        4L, "=HYPERLINK(\"https://example.invalid\")", "Hogar, \"principal\"",
                        fecha, TipoMovimiento.GASTO, new BigDecimal("10.00"))));

        String csv = reporteService.exportarTransaccionesCsv(
                "persona@correo.com", fecha.minusDays(1), fecha, null, null);

        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"Hogar, \"\"principal\"\"\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("'=HYPERLINK"));
    }

    private TransaccionRepository.ReporteAgregado agregado(
            LocalDate fecha,
            TipoMovimiento tipo,
            Long categoriaId,
            String categoria,
            String monto) {
        return new TestAggregate(
                fecha.getYear(), fecha.getMonthValue(), tipo.name(), categoriaId, categoria, null,
                new BigDecimal(monto));
    }

    private record TestAggregate(
            Integer anio,
            Integer mes,
            String tipo,
            Long categoriaId,
            String categoriaNombre,
            String color,
            BigDecimal total) implements TransaccionRepository.ReporteAgregado {
        @Override
        public Integer getAnio() {
            return anio;
        }

        @Override
        public Integer getMes() {
            return mes;
        }

        @Override
        public String getTipo() {
            return tipo;
        }

        @Override
        public Long getCategoriaId() {
            return categoriaId;
        }

        @Override
        public String getCategoriaNombre() {
            return categoriaNombre;
        }

        @Override
        public String getColor() {
            return color;
        }

        @Override
        public BigDecimal getTotal() {
            return total;
        }
    }
}
