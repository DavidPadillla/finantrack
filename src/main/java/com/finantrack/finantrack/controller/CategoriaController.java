package com.finantrack.finantrack.controller;

import com.finantrack.finantrack.dto.CategoriaForm;
import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.service.CategoriaService;
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

@Controller
@RequestMapping("/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public String listar(Authentication authentication, Model model) {
        model.addAttribute("categorias", categoriaService.listar(authentication.getName()));
        return "categorias";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("categoriaForm", new CategoriaForm());
        model.addAttribute("modoEdicion", false);
        return "categoria-form";
    }

    @PostMapping
    public String crear(
            Authentication authentication,
            @Valid @ModelAttribute("categoriaForm") CategoriaForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                categoriaService.crear(authentication.getName(), form);
                redirectAttributes.addFlashAttribute("mensajeExito", "Categoría creada correctamente.");
                return "redirect:/categorias";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("categoria.invalida", exception.getMessage());
            }
        }
        model.addAttribute("modoEdicion", false);
        return "categoria-form";
    }

    @GetMapping("/{id}/editar")
    public String editar(Authentication authentication, @PathVariable Long id, Model model) {
        Categoria categoria = categoriaService.buscarPropia(authentication.getName(), id);
        model.addAttribute("categoriaForm", formulario(categoria));
        model.addAttribute("categoriaId", id);
        model.addAttribute("modoEdicion", true);
        return "categoria-form";
    }

    @PostMapping("/{id}")
    public String actualizar(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("categoriaForm") CategoriaForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                categoriaService.actualizar(authentication.getName(), id, form);
                redirectAttributes.addFlashAttribute("mensajeExito", "Categoría actualizada correctamente.");
                return "redirect:/categorias";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("categoria.invalida", exception.getMessage());
            }
        }
        model.addAttribute("categoriaId", id);
        model.addAttribute("modoEdicion", true);
        return "categoria-form";
    }

    @PostMapping("/{id}/desactivar")
    public String desactivar(
            Authentication authentication,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        categoriaService.desactivar(authentication.getName(), id);
        redirectAttributes.addFlashAttribute("mensajeExito", "Categoría desactivada. Su historial se conserva.");
        return "redirect:/categorias";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String noEncontrada(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("mensajeError", "La categoría no existe o no te pertenece.");
        return "redirect:/categorias";
    }

    private CategoriaForm formulario(Categoria categoria) {
        CategoriaForm form = new CategoriaForm();
        form.setNombre(categoria.getNombre());
        form.setTipo(categoria.getTipo());
        form.setColor(categoria.getColor());
        return form;
    }
}
