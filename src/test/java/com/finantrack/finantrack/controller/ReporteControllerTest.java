package com.finantrack.finantrack.controller;

import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.security.SecurityConfig;
import com.finantrack.finantrack.service.ReporteService;
import com.finantrack.finantrack.service.ReporteService.FinancialHealth;
import com.finantrack.finantrack.service.ReporteService.ReportData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReporteController.class)
@Import(SecurityConfig.class)
class ReporteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReporteService reporteService;

    @Test
    void protegeLosReportesYLasExportaciones() throws Exception {
        mockMvc.perform(get("/reportes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/api/login"));

        mockMvc.perform(get("/reportes/exportar.csv")
                        .param("desde", LocalDate.now().minusDays(1).toString())
                        .param("hasta", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void renderizaFiltrosYEstadoVacioSinInventarGraficos() throws Exception {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = hoy.minusDays(30);
        when(reporteService.generar("persona@correo.com", desde, hoy, null, null))
                .thenReturn(reporteVacio(desde, hoy));
        when(reporteService.categorias("persona@correo.com")).thenReturn(List.of());

        mockMvc.perform(get("/reportes")
                        .with(user("persona@correo.com"))
                        .param("desde", desde.toString())
                        .param("hasta", hoy.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("reportes"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reportes financieros")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sin movimientos en este período")));
    }

    @Test
    void exportaCsvSoloParaElUsuarioAutenticado() throws Exception {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = hoy.minusDays(30);
        when(reporteService.generar("persona@correo.com", desde, hoy, TipoMovimiento.GASTO, null))
                .thenReturn(reporteVacio(desde, hoy));
        when(reporteService.exportarReporteCsv(org.mockito.ArgumentMatchers.any()))
                .thenReturn("\uFEFFSección,Concepto\r\nResumen,Gastos\r\n");

        mockMvc.perform(get("/reportes/exportar.csv")
                        .with(user("persona@correo.com"))
                        .param("desde", desde.toString())
                        .param("hasta", hoy.toString())
                        .param("tipo", "GASTO"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"finantrack-reporte.csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Resumen,Gastos")));

        verify(reporteService).generar("persona@correo.com", desde, hoy, TipoMovimiento.GASTO, null);
    }

    private ReportData reporteVacio(LocalDate desde, LocalDate hasta) {
        return new ReportData(
                desde, hasta, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
                null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new FinancialHealth("Sin datos suficientes", "No hay movimientos."),
                false);
    }
}
