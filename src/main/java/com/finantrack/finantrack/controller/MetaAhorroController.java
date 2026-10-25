package com.finantrack.finantrack.controller;

import com.finantrack.finantrack.dto.AporteMetaForm;
import com.finantrack.finantrack.dto.MetaAhorroForm;
import com.finantrack.finantrack.service.AporteMetaService;
import com.finantrack.finantrack.service.MetaAhorroService;
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
@RequestMapping("/metas")
public class MetaAhorroController {

    private final MetaAhorroService metaAhorroService;
    private final AporteMetaService aporteMetaService;

    public MetaAhorroController(MetaAhorroService metaAhorroService, AporteMetaService aporteMetaService) {
        this.metaAhorroService = metaAhorroService;
        this.aporteMetaService = aporteMetaService;
    }

    @GetMapping
    public String listar(Authentication authentication, Model model) {
        model.addAttribute("metas", metaAhorroService.listar(authentication.getName()));
        return "metas";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("metaForm", new MetaAhorroForm());
        model.addAttribute("modoEdicion", false);
        return "meta-form";
    }

    @PostMapping
    public String crear(
            Authentication authentication,
            @Valid @ModelAttribute("metaForm") MetaAhorroForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                metaAhorroService.crear(authentication.getName(), form);
                redirectAttributes.addFlashAttribute("mensajeExito", "Meta creada correctamente.");
                return "redirect:/metas";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("meta.invalida", exception.getMessage());
            }
        }
        model.addAttribute("modoEdicion", false);
        return "meta-form";
    }

    @GetMapping("/{id}/editar")
    public String editar(Authentication authentication, @PathVariable Long id, Model model) {
        model.addAttribute("metaForm", metaAhorroService.buscarFormularioPropio(authentication.getName(), id));
        model.addAttribute("metaId", id);
        model.addAttribute("modoEdicion", true);
        return "meta-form";
    }

    @PostMapping("/{id}")
    public String actualizar(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("metaForm") MetaAhorroForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                metaAhorroService.actualizar(authentication.getName(), id, form);
                redirectAttributes.addFlashAttribute("mensajeExito", "Meta actualizada correctamente.");
                return "redirect:/metas";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("meta.invalida", exception.getMessage());
            }
        }
        model.addAttribute("metaId", id);
        model.addAttribute("modoEdicion", true);
        return "meta-form";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(
            Authentication authentication,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        metaAhorroService.cancelar(authentication.getName(), id);
        redirectAttributes.addFlashAttribute("mensajeExito", "Meta cancelada. Sus aportes se conservan.");
        return "redirect:/metas";
    }

    @GetMapping("/{id}")
    public String detalle(Authentication authentication, @PathVariable Long id, Model model) {
        String email = authentication.getName();
        model.addAttribute("meta", metaAhorroService.listar(email).stream()
                .filter(meta -> meta.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("La meta no existe o no te pertenece.")));
        model.addAttribute("aportes", aporteMetaService.listar(email, id));
        model.addAttribute("aporteForm", new AporteMetaForm());
        return "meta-detalle";
    }

    @PostMapping("/{id}/aportes")
    public String aportar(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("aporteForm") AporteMetaForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        String email = authentication.getName();
        if (!bindingResult.hasErrors()) {
            try {
                aporteMetaService.registrar(email, id, form);
                redirectAttributes.addFlashAttribute("mensajeExito", "Aporte registrado correctamente.");
                return "redirect:/metas/" + id;
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("aporte.invalido", exception.getMessage());
            }
        }
        model.addAttribute("meta", metaAhorroService.listar(email).stream()
                .filter(meta -> meta.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("La meta no existe o no te pertenece.")));
        model.addAttribute("aportes", aporteMetaService.listar(email, id));
        return "meta-detalle";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String noEncontrada(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("mensajeError", "La meta no existe o no te pertenece.");
        return "redirect:/metas";
    }
}
