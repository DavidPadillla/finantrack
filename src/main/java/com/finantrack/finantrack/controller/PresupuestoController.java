package com.finantrack.finantrack.controller;

import com.finantrack.finantrack.dto.PresupuestoForm;
import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.service.CategoriaService;
import com.finantrack.finantrack.service.PresupuestoService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/presupuestos")
public class PresupuestoController {

    private final PresupuestoService presupuestoService;
    private final CategoriaService categoriaService;

    public PresupuestoController(PresupuestoService presupuestoService, CategoriaService categoriaService) {
        this.presupuestoService = presupuestoService;
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public String listar(Authentication authentication, Model model) {
        model.addAttribute("presupuestos", presupuestoService.listar(authentication.getName()));
        return "presupuestos";
    }

    @GetMapping("/nuevo")
    public String nuevo(Authentication authentication, Model model) {
        model.addAttribute("presupuestoForm", new PresupuestoForm());
        model.addAttribute("categorias", categoriasDisponibles(authentication.getName()));
        model.addAttribute("modoEdicion", false);
        return "presupuesto-form";
    }

    @PostMapping
    public String crear(
            Authentication authentication,
            @Valid @ModelAttribute("presupuestoForm") PresupuestoForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                presupuestoService.crear(authentication.getName(), form);
                redirectAttributes.addFlashAttribute("mensajeExito", "Presupuesto creado correctamente.");
                return "redirect:/presupuestos";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("presupuesto.invalido", exception.getMessage());
            }
        }
        prepararFormulario(authentication, model, false, null);
        return "presupuesto-form";
    }

    @GetMapping("/{id}/editar")
    public String editar(Authentication authentication, @PathVariable Long id, Model model) {
        model.addAttribute("presupuestoForm", presupuestoService.buscarFormularioPropio(authentication.getName(), id));
        model.addAttribute("presupuestoId", id);
        model.addAttribute("categorias", categoriasDisponibles(authentication.getName()));
        model.addAttribute("modoEdicion", true);
        return "presupuesto-form";
    }

    @PostMapping("/{id}")
    public String actualizar(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("presupuestoForm") PresupuestoForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                presupuestoService.actualizar(authentication.getName(), id, form);
                redirectAttributes.addFlashAttribute("mensajeExito", "Presupuesto actualizado correctamente.");
                return "redirect:/presupuestos";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("presupuesto.invalido", exception.getMessage());
            }
        }
        prepararFormulario(authentication, model, true, id);
        return "presupuesto-form";
    }

    @PostMapping("/{id}/desactivar")
    public String desactivar(
            Authentication authentication,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        presupuestoService.desactivar(authentication.getName(), id);
        redirectAttributes.addFlashAttribute("mensajeExito", "Presupuesto desactivado.");
        return "redirect:/presupuestos";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String noEncontrado(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("mensajeError", "El presupuesto no existe o no te pertenece.");
        return "redirect:/presupuestos";
    }

    private void prepararFormulario(
            Authentication authentication,
            Model model,
            boolean modoEdicion,
            Long presupuestoId) {
        model.addAttribute("categorias", categoriasDisponibles(authentication.getName()));
        model.addAttribute("modoEdicion", modoEdicion);
        if (presupuestoId != null) {
            model.addAttribute("presupuestoId", presupuestoId);
        }
    }

    private List<Categoria> categoriasDisponibles(String email) {
        return categoriaService.listar(email).stream()
                .filter(categoria -> Boolean.TRUE.equals(categoria.getActiva()))
                .filter(categoria -> categoria.getTipo() == TipoMovimiento.GASTO)
                .toList();
    }
}
