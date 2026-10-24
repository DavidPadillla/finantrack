package com.finantrack.finantrack.controller;

import com.finantrack.finantrack.service.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/api")
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/registro")
    public String registrar(
            @RequestParam("name") String nombre,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam(name = "terms", required = false) String terms,
            RedirectAttributes redirectAttributes) {
        if (!"true".equals(terms)) {
            redirectAttributes.addFlashAttribute("registerError", "Debes aceptar los términos para crear tu cuenta.");
            return "redirect:/api/login";
        }

        try {
            usuarioService.registrar(nombre, email, password);
            return "redirect:/api/login?registered";
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("registerError", exception.getMessage());
            return "redirect:/api/login";
        }
    }
}
