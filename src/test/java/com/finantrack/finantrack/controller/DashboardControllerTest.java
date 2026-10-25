package com.finantrack.finantrack.controller;

import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.service.DashboardService;
import com.finantrack.finantrack.service.DashboardService.BudgetProgress;
import com.finantrack.finantrack.service.DashboardService.CategoryExpense;
import com.finantrack.finantrack.service.DashboardService.Comparison;
import com.finantrack.finantrack.service.DashboardService.DashboardData;
import com.finantrack.finantrack.service.DashboardService.GoalProgress;
import com.finantrack.finantrack.service.DashboardService.MonthBar;
import com.finantrack.finantrack.service.DashboardService.TransactionRow;
import com.finantrack.finantrack.security.SecurityConfig;
import com.finantrack.finantrack.service.TransaccionService;
import com.finantrack.finantrack.dto.TransaccionForm;
import com.finantrack.finantrack.model.Transaccion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest({DashboardController.class, TransaccionController.class})
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private TransaccionService transaccionService;

    @Test
    @WithMockUser(username = "persona@correo.com")
    void muestraDashboardConDatosDelUsuarioAutenticado() throws Exception {
        LocalDate hoy = LocalDate.now();
        DashboardData data = new DashboardData(
                "Persona Ejemplo",
                "persona@correo.com",
                "Persona",
                "PE",
                "octubre",
                new BigDecimal("1500.00"),
                new BigDecimal("650.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("850.00"),
                new Comparison(new BigDecimal("10.0"), true, true),
                new Comparison(new BigDecimal("5.0"), true, false),
                List.of(new MonthBar("oct", new BigDecimal("1500"), new BigDecimal("650"), 100, 43)),
                List.of(new CategoryExpense("Comida", new BigDecimal("650"), new BigDecimal("100.0"), "#38B889")),
                "conic-gradient(#38B889 0% 100%)",
                List.of(new TransactionRow("Compra", "Comida", hoy, TipoMovimiento.GASTO, new BigDecimal("650.00"))),
                0,
                3,
                false,
                true,
                List.of(new GoalProgress("Viaje", new BigDecimal("1000"), new BigDecimal("300"), 30, hoy.plusMonths(3))),
                List.of(new BudgetProgress("Comida", new BigDecimal("800"), new BigDecimal("650"), 81, 81, hoy.plusDays(10))),
                "",
                true,
                true,
                true
        );
        when(dashboardService.cargar("persona@correo.com", null, 0)).thenReturn(data);

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Persona Ejemplo")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Últimas transacciones")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gastos por categoría")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Presupuestos activos")));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));

        when(transaccionService.listar("persona@correo.com", null, null, null, null, 0))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, TransaccionService.TAMANO_PAGINA), 0));
        when(transaccionService.categoriasDisponibles("persona@correo.com")).thenReturn(List.of());
        mockMvc.perform(get("/transacciones"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Transacciones")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No hay transacciones para mostrar")));
    }

    @Test
    void protegeDashboardParaUsuariosSinSesion() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/api/login"));
    }

    @Test
    @WithMockUser(username = "persona@correo.com")
    void muestraFormularioYRegistraUnaTransaccionConCsrf() throws Exception {
        when(transaccionService.categoriasDisponibles("persona@correo.com")).thenReturn(List.of());
        when(transaccionService.crear(eq("persona@correo.com"), any(TransaccionForm.class)))
                .thenReturn(new Transaccion());

        mockMvc.perform(get("/transacciones/nueva"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaccion-form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registrar movimiento")));

        mockMvc.perform(post("/transacciones")
                        .with(csrf())
                        .param("tipo", "GASTO")
                        .param("monto", "25.50")
                        .param("descripcion", "Almuerzo")
                        .param("fecha", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/transacciones"));

        verify(transaccionService).crear(eq("persona@correo.com"), any(TransaccionForm.class));
    }
}
