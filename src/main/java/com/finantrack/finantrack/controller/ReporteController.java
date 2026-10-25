package com.finantrack.finantrack.controller;

import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.service.ReporteService;
import com.finantrack.finantrack.service.ReporteService.ReportData;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;

@Controller
@RequestMapping("/reportes")
public class ReporteController {

    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @GetMapping
    public String reportes(
            Authentication authentication,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "tipo", required = false) TipoMovimiento tipo,
            @RequestParam(name = "categoriaId", required = false) Long categoriaId,
            Model model) {
        LocalDate fechaHasta = hasta == null ? LocalDate.now() : hasta;
        LocalDate fechaDesde = desde == null ? YearMonth.from(fechaHasta).minusMonths(11).atDay(1) : desde;
        ReportData reporte = null;
        String errorFiltro = null;
        try {
            reporte = reporteService.generar(authentication.getName(), fechaDesde, fechaHasta, tipo, categoriaId);
        } catch (IllegalArgumentException exception) {
            errorFiltro = exception.getMessage();
        }

        model.addAttribute("reporte", reporte);
        model.addAttribute("categorias", reporteService.categorias(authentication.getName()));
        model.addAttribute("desde", fechaDesde);
        model.addAttribute("hasta", fechaHasta);
        model.addAttribute("tipo", tipo);
        model.addAttribute("categoriaId", categoriaId);
        model.addAttribute("errorFiltro", errorFiltro);
        return "reportes";
    }

    @GetMapping("/exportar.csv")
    public ResponseEntity<byte[]> exportarReporte(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) Long categoriaId) {
        try {
            ReportData reporte = reporteService.generar(authentication.getName(), desde, hasta, tipo, categoriaId);
            return csv(reporteService.exportarReporteCsv(reporte), "finantrack-reporte.csv");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .cacheControl(CacheControl.noStore())
                    .body(exception.getMessage().getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/exportar-transacciones.csv")
    public ResponseEntity<byte[]> exportarTransacciones(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) Long categoriaId) {
        try {
            String csv = reporteService.exportarTransaccionesCsv(
                    authentication.getName(), desde, hasta, tipo, categoriaId);
            return csv(csv, "finantrack-transacciones.csv");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .cacheControl(CacheControl.noStore())
                    .body(exception.getMessage().getBytes(StandardCharsets.UTF_8));
        }
    }

    private ResponseEntity<byte[]> csv(String csv, String filename) {
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
