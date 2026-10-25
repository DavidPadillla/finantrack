package com.finantrack.finantrack.controller;

import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.Presupuesto;
import com.finantrack.finantrack.model.MetaAhorro;
import com.finantrack.finantrack.service.AporteMetaService;
import com.finantrack.finantrack.service.CategoriaService;
import com.finantrack.finantrack.service.MetaAhorroService;
import com.finantrack.finantrack.service.PresupuestoService;
import com.finantrack.finantrack.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({CategoriaController.class, PresupuestoController.class, MetaAhorroController.class})
@Import(SecurityConfig.class)
class GestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoriaService categoriaService;
    @MockBean
    private PresupuestoService presupuestoService;
    @MockBean
    private MetaAhorroService metaAhorroService;
    @MockBean
    private AporteMetaService aporteMetaService;

    @Test
    @WithMockUser(username = "persona@correo.com")
    void renderizaPaginasYFormulariosDeGestion() throws Exception {
        when(categoriaService.listar("persona@correo.com")).thenReturn(List.of());
        when(presupuestoService.listar("persona@correo.com")).thenReturn(List.of());
        when(metaAhorroService.listar("persona@correo.com")).thenReturn(List.of());

        mockMvc.perform(get("/categorias")).andExpect(status().isOk()).andExpect(view().name("categorias"));
        mockMvc.perform(get("/categorias/nueva")).andExpect(status().isOk()).andExpect(view().name("categoria-form"));
        mockMvc.perform(get("/presupuestos")).andExpect(status().isOk()).andExpect(view().name("presupuestos"));
        mockMvc.perform(get("/presupuestos/nuevo")).andExpect(status().isOk()).andExpect(view().name("presupuesto-form"));
        mockMvc.perform(get("/metas")).andExpect(status().isOk()).andExpect(view().name("metas"));
        mockMvc.perform(get("/metas/nueva")).andExpect(status().isOk()).andExpect(view().name("meta-form"));
    }

    @Test
    @WithMockUser(username = "persona@correo.com")
    void creaCategoriaConCsrfYUsuarioAutenticado() throws Exception {
        when(categoriaService.crear(eq("persona@correo.com"), any())).thenReturn(new Categoria());

        mockMvc.perform(post("/categorias")
                        .with(csrf())
                        .param("nombre", "Transporte")
                        .param("tipo", "GASTO")
                        .param("color", "#38B889"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categorias"));
    }

    @Test
    @WithMockUser(username = "persona@correo.com")
    void creaPresupuestoYMetaConCsrf() throws Exception {
        when(categoriaService.listar("persona@correo.com")).thenReturn(List.of());
        when(presupuestoService.crear(eq("persona@correo.com"), any())).thenReturn(new Presupuesto());
        when(metaAhorroService.crear(eq("persona@correo.com"), any())).thenReturn(new MetaAhorro());

        mockMvc.perform(post("/presupuestos")
                        .with(csrf())
                        .param("categoriaId", "1")
                        .param("montoLimite", "250.00")
                        .param("fechaInicio", "2026-05-01")
                        .param("fechaFin", "2026-05-31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/presupuestos"));

        mockMvc.perform(post("/metas")
                        .with(csrf())
                        .param("nombre", "Fondo de emergencia")
                        .param("montoObjetivo", "1000.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/metas"));
    }
}
