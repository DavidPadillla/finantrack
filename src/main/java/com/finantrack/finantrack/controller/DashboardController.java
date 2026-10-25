package com.finantrack.finantrack.controller;

import com.finantrack.finantrack.service.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"/dashboard", "/api/dashboard"})
    public String dashboard(
            Authentication authentication,
            @RequestParam(name = "q", required = false) String busqueda,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            Model model) {
        model.addAttribute("dashboard", dashboardService.cargar(authentication.getName(), busqueda, pagina));
        return "dashboard";
    }

    @GetMapping("/api/transacciones")
    public String rutaAlternativaTransacciones() {
        return "redirect:/transacciones";
    }

}
