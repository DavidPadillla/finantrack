package com.finantrack.finantrack.controller;

import com.finantrack.finantrack.dto.TransaccionForm;
import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.service.TransaccionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/transacciones")
public class TransaccionController {

    private final TransaccionService transaccionService;

    public TransaccionController(TransaccionService transaccionService) {
        this.transaccionService = transaccionService;
    }

    @GetMapping
    public String listar(
            Authentication authentication,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "tipo", required = false) TipoMovimiento tipo,
            @RequestParam(name = "categoriaId", required = false) Long categoriaId,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            Model model) {
        int paginaSegura = Math.max(pagina, 0);
        Page<TransaccionService.TransaccionResumen> movimientos;
        String errorFiltro = null;
        try {
            movimientos = transaccionService.listar(
                    authentication.getName(), desde, hasta, tipo, categoriaId, paginaSegura);
        } catch (IllegalArgumentException exception) {
            movimientos = Page.empty(PageRequest.of(paginaSegura, TransaccionService.TAMANO_PAGINA));
            errorFiltro = exception.getMessage();
        }

        model.addAttribute("movimientos", movimientos);
        model.addAttribute("categorias", transaccionService.categoriasDisponibles(authentication.getName()));
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("tipo", tipo);
        model.addAttribute("categoriaId", categoriaId);
        model.addAttribute("errorFiltro", errorFiltro);
        return "transacciones";
    }

    @GetMapping("/nueva")
    public String nuevo(Authentication authentication, Model model) {
        model.addAttribute("transaccionForm", new TransaccionForm());
        model.addAttribute("categorias", transaccionService.categoriasDisponibles(authentication.getName()));
        model.addAttribute("modoEdicion", false);
        return "transaccion-form";
    }

    @GetMapping("/{id}/editar")
    public String editar(
            Authentication authentication,
            @PathVariable Long id,
            Model model) {
        TransaccionForm form = transaccionService.buscarFormularioPropio(authentication.getName(), id);
        List<Categoria> categorias = transaccionService.categoriasDisponibles(authentication.getName(), form.getCategoriaId());
        model.addAttribute("transaccionForm", form);
        model.addAttribute("categorias", categorias);
        model.addAttribute("transaccionId", id);
        model.addAttribute("modoEdicion", true);
        return "transaccion-form";
    }

    @PostMapping
    public String crear(
            Authentication authentication,
            @Valid @ModelAttribute("transaccionForm") TransaccionForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                transaccionService.crear(authentication.getName(), form);
                redirectAttributes.addFlashAttribute("mensajeExito", "Transacción registrada correctamente.");
                return "redirect:/transacciones";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("transaccion.invalida", exception.getMessage());
            }
        }
        prepararFormulario(authentication, model, false, null);
        return "transaccion-form";
    }

    @PostMapping("/{id}")
    public String actualizar(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("transaccionForm") TransaccionForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                transaccionService.actualizar(authentication.getName(), id, form);
                redirectAttributes.addFlashAttribute("mensajeExito", "Transacción actualizada correctamente.");
                return "redirect:/transacciones";
            } catch (IllegalArgumentException exception) {
                bindingResult.reject("transaccion.invalida", exception.getMessage());
            }
        }
        prepararFormulario(authentication, model, true, id);
        return "transaccion-form";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(
            Authentication authentication,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        transaccionService.eliminar(authentication.getName(), id);
        redirectAttributes.addFlashAttribute("mensajeExito", "Transacción eliminada correctamente.");
        return "redirect:/transacciones";
    }

    private void prepararFormulario(
            Authentication authentication,
            Model model,
            boolean modoEdicion,
            Long transaccionId) {
        List<Categoria> categorias = modoEdicion && transaccionId != null
                ? transaccionService.categoriasDisponibles(authentication.getName(),
                        transaccionService.buscarFormularioPropio(authentication.getName(), transaccionId).getCategoriaId())
                : transaccionService.categoriasDisponibles(authentication.getName());
        if (modoEdicion && transaccionId != null) {
            model.addAttribute("transaccionId", transaccionId);
        }
        model.addAttribute("categorias", categorias);
        model.addAttribute("modoEdicion", modoEdicion);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public String transaccionNoEncontrada(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("mensajeError", "La transacción no existe o no tienes permiso para verla.");
        return "redirect:/transacciones";
    }
}
