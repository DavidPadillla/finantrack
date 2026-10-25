package com.finantrack.finantrack.service;

import com.finantrack.finantrack.model.EstadoMeta;
import com.finantrack.finantrack.model.Categoria;
import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.model.Transaccion;
import com.finantrack.finantrack.model.Usuario;
import com.finantrack.finantrack.repository.CategoriaRepository;
import com.finantrack.finantrack.repository.TransaccionRepository;
import com.finantrack.finantrack.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ReporteService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final List<String> PALETA = List.of(
            "#4b79e4", "#21a675", "#7955c8", "#d59735", "#c84d5a", "#45a9c7"
    );

    private final TransaccionRepository transaccionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransaccionService transaccionService;
    private final PresupuestoService presupuestoService;
    private final MetaAhorroService metaAhorroService;

    public ReporteService(
            TransaccionRepository transaccionRepository,
            UsuarioRepository usuarioRepository,
            CategoriaRepository categoriaRepository,
            TransaccionService transaccionService,
            PresupuestoService presupuestoService,
            MetaAhorroService metaAhorroService) {
        this.transaccionRepository = transaccionRepository;
        this.usuarioRepository = usuarioRepository;
        this.categoriaRepository = categoriaRepository;
        this.transaccionService = transaccionService;
        this.presupuestoService = presupuestoService;
        this.metaAhorroService = metaAhorroService;
    }

    @Transactional(readOnly = true)
    public ReportData generar(
            String email,
            LocalDate desde,
            LocalDate hasta,
            TipoMovimiento tipo,
            Long categoriaId) {
        validarFiltros(desde, hasta);
        Usuario usuario = buscarUsuario(email);
        validarCategoria(categoriaId, usuario);

        List<TransaccionRepository.ReporteAgregado> actuales =
                transaccionRepository.agregadosDeReporte(usuario.getId(), desde, hasta);
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        LocalDate finAnterior = desde.minusDays(1);
        LocalDate inicioAnterior = finAnterior.minusDays(dias - 1);
        List<TransaccionRepository.ReporteAgregado> anteriores =
                transaccionRepository.agregadosDeReporte(usuario.getId(), inicioAnterior, finAnterior);

        Totals totales = resumir(actuales, tipo, categoriaId);
        Totals totalesAnteriores = resumir(anteriores, tipo, categoriaId);
        TipoMovimiento tipoGrafica = tipo == TipoMovimiento.INGRESO
                ? TipoMovimiento.INGRESO
                : TipoMovimiento.GASTO;
        List<CategoryAmount> categorias = distribucion(actuales, tipoGrafica, categoriaId);
        List<MonthAmount> meses = evolucion(actuales, desde, hasta, tipo, categoriaId);
        List<CategoryIncrease> aumentos = aumentosDeGasto(actuales, anteriores, tipo, categoriaId);

        LocalDate hoy = LocalDate.now();
        List<PresupuestoService.PresupuestoResumen> presupuestosCercanos =
                presupuestoService.listar(email).stream()
                        .filter(PresupuestoService.PresupuestoResumen::activo)
                        .filter(p -> !p.fechaInicio().isAfter(hoy) && !p.fechaFin().isBefore(hoy))
                        .filter(p -> p.porcentaje() >= 80)
                        .toList();
        List<MetaAhorroService.MetaResumen> metasActivas =
                metaAhorroService.listar(email).stream()
                        .filter(meta -> meta.estado() == EstadoMeta.ACTIVA)
                        .toList();

        BigDecimal tasaAhorro = totales.ingresos().signum() > 0
                ? porcentaje(totales.ahorro(), totales.ingresos(), 1)
                : null;
        FinancialHealth salud = crearSalud(totales);

        return new ReportData(
                desde,
                hasta,
                tipo,
                categoriaId,
                totales.ingresos(),
                totales.gastos(),
                totales.ahorro(),
                tasaAhorro,
                comparar(totales.ingresos(), totalesAnteriores.ingresos()),
                comparar(totales.gastos(), totalesAnteriores.gastos()),
                comparar(totales.ahorro(), totalesAnteriores.ahorro()),
                categorias,
                meses,
                aumentos,
                presupuestosCercanos,
                metasActivas,
                salud,
                totales.hayMovimientos()
        );
    }

    @Transactional(readOnly = true)
    public List<CategoryOption> categorias(String email) {
        Usuario usuario = buscarUsuario(email);
        List<Categoria> categorias = new ArrayList<>(categoriaRepository.findByUsuarioIsNullOrderByNombreAsc());
        categorias.addAll(categoriaRepository.findByUsuario_IdOrderByNombreAsc(usuario.getId()));
        return categorias.stream()
                .map(categoria -> new CategoryOption(
                        categoria.getId(),
                        categoria.getNombre(),
                        categoria.getTipo(),
                        Boolean.TRUE.equals(categoria.getActiva())))
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportarReporteCsv(ReportData reporte) {
        List<String> filas = new ArrayList<>();
        filas.add(csv("Sección", "Concepto", "Desde", "Hasta", "Importe", "Variación frente al período anterior (%)"));
        filas.add(csv("Resumen", "Ingresos", reporte.desde(), reporte.hasta(),
                reporte.ingresos().toPlainString(), porcentajeCsv(reporte.comparacionIngresos())));
        filas.add(csv("Resumen", "Gastos", reporte.desde(), reporte.hasta(),
                reporte.gastos().toPlainString(), porcentajeCsv(reporte.comparacionGastos())));
        filas.add(csv("Resumen", "Ahorro neto", reporte.desde(), reporte.hasta(),
                reporte.ahorroNeto().toPlainString(), porcentajeCsv(reporte.comparacionAhorro())));
        filas.add("");
        filas.add(csv("Sección", "Período", "Ingresos", "Gastos", "Ahorro neto"));
        for (MonthAmount mes : reporte.meses()) {
            filas.add(csv("Evolución mensual", mes.etiqueta(), mes.ingresos().toPlainString(),
                    mes.gastos().toPlainString(), mes.ahorroNeto().toPlainString()));
        }
        filas.add("");
        filas.add(csv("Sección", "Categoría", "Importe", "Porcentaje"));
        for (CategoryAmount categoria : reporte.categorias()) {
            filas.add(csv("Distribución", categoria.nombre(), categoria.total().toPlainString(),
                    categoria.porcentaje().toPlainString()));
        }
        return "\uFEFF" + String.join("\r\n", filas) + "\r\n";
    }

    @Transactional(readOnly = true)
    public String exportarTransaccionesCsv(
            String email,
            LocalDate desde,
            LocalDate hasta,
            TipoMovimiento tipo,
            Long categoriaId) {
        validarFiltros(desde, hasta);
        List<TransaccionService.TransaccionResumen> transacciones =
                transaccionService.exportar(email, desde, hasta, tipo, categoriaId);
        List<String> filas = new ArrayList<>();
        filas.add(csv("Fecha", "Tipo", "Categoría", "Descripción", "Importe"));
        for (TransaccionService.TransaccionResumen transaccion : transacciones) {
            filas.add(csv(
                    transaccion.fecha(),
                    transaccion.tipo(),
                    transaccion.categoria(),
                    transaccion.descripcion() == null ? "" : transaccion.descripcion(),
                    transaccion.monto().toPlainString()
            ));
        }
        return "\uFEFF" + String.join("\r\n", filas) + "\r\n";
    }

    private List<MonthAmount> evolucion(
            List<TransaccionRepository.ReporteAgregado> agregados,
            LocalDate desde,
            LocalDate hasta,
            TipoMovimiento tipo,
            Long categoriaId) {
        Map<YearMonth, Totals> porMes = new TreeMap<>();
        for (YearMonth mes = YearMonth.from(desde); !mes.isAfter(YearMonth.from(hasta)); mes = mes.plusMonths(1)) {
            porMes.put(mes, new Totals());
        }
        for (TransaccionRepository.ReporteAgregado agregado : agregados) {
            if (!coincide(agregado, tipo, categoriaId)) {
                continue;
            }
            YearMonth mes = YearMonth.of(agregado.getAnio(), agregado.getMes());
            Totals total = porMes.get(mes);
            if (total != null) {
                total.agregar(agregado);
            }
        }
        BigDecimal maximo = porMes.values().stream()
                .map(total -> total.ingresos().max(total.gastos()))
                .max(BigDecimal::compareTo)
                .orElse(ZERO);
        List<MonthAmount> meses = new ArrayList<>();
        porMes.forEach((mes, total) -> meses.add(new MonthAmount(
                mes.getMonth().getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es")),
                total.ingresos(),
                total.gastos(),
                total.ahorro(),
                ancho(total.ingresos(), maximo),
                ancho(total.gastos(), maximo)
        )));
        return meses;
    }

    private List<CategoryAmount> distribucion(
            List<TransaccionRepository.ReporteAgregado> agregados,
            TipoMovimiento tipo,
            Long categoriaId) {
        Map<Long, CategoryAccumulator> porCategoria = new LinkedHashMap<>();
        for (TransaccionRepository.ReporteAgregado agregado : agregados) {
            if (!tipo.name().equals(agregado.getTipo())
                    || (categoriaId != null && !categoriaId.equals(agregado.getCategoriaId()))) {
                continue;
            }
            Long key = agregado.getCategoriaId();
            CategoryAccumulator accumulator = porCategoria.computeIfAbsent(key, ignored ->
                    new CategoryAccumulator(agregado.getCategoriaNombre(), agregado.getColor()));
            accumulator.total = accumulator.total.add(valor(agregado.getTotal()));
        }
        BigDecimal suma = porCategoria.values().stream()
                .map(category -> category.total)
                .reduce(ZERO, BigDecimal::add);
        List<CategoryAccumulator> ordenadas = porCategoria.values().stream()
                .sorted(Comparator.comparing((CategoryAccumulator category) -> category.total).reversed())
                .toList();
        List<CategoryAmount> resultado = new ArrayList<>();
        for (int index = 0; index < ordenadas.size(); index++) {
            CategoryAccumulator category = ordenadas.get(index);
            resultado.add(new CategoryAmount(
                    category.nombre,
                    category.total,
                    suma.signum() == 0 ? ZERO : porcentaje(category.total, suma, 1),
                    colorValido(category.color) ? category.color : PALETA.get(index % PALETA.size())
            ));
        }
        return resultado;
    }

    private List<CategoryIncrease> aumentosDeGasto(
            List<TransaccionRepository.ReporteAgregado> actuales,
            List<TransaccionRepository.ReporteAgregado> anteriores,
            TipoMovimiento tipo,
            Long categoriaId) {
        if (tipo == TipoMovimiento.INGRESO) {
            return List.of();
        }
        Map<Long, CategoryAccumulator> gastosActuales = gastosPorCategoria(actuales, categoriaId);
        Map<Long, CategoryAccumulator> gastosAnteriores = gastosPorCategoria(anteriores, categoriaId);
        return gastosActuales.entrySet().stream()
                .filter(entry -> entry.getValue().total.signum() > 0)
                .filter(entry -> gastosAnteriores.containsKey(entry.getKey())
                        && gastosAnteriores.get(entry.getKey()).total.signum() > 0)
                .map(entry -> {
                    BigDecimal anterior = gastosAnteriores.get(entry.getKey()).total;
                    BigDecimal actual = entry.getValue().total;
                    BigDecimal cambio = porcentaje(actual.subtract(anterior), anterior, 1);
                    return new CategoryIncrease(entry.getValue().nombre, actual, anterior, cambio);
                })
                .filter(aumento -> aumento.variacionPorcentual().compareTo(BigDecimal.valueOf(20)) >= 0)
                .sorted(Comparator.comparing(CategoryIncrease::variacionPorcentual).reversed())
                .limit(5)
                .toList();
    }

    private Map<Long, CategoryAccumulator> gastosPorCategoria(
            List<TransaccionRepository.ReporteAgregado> agregados,
            Long categoriaId) {
        Map<Long, CategoryAccumulator> gastos = new LinkedHashMap<>();
        for (TransaccionRepository.ReporteAgregado agregado : agregados) {
            if (!TipoMovimiento.GASTO.name().equals(agregado.getTipo())
                    || (categoriaId != null && !categoriaId.equals(agregado.getCategoriaId()))) {
                continue;
            }
            CategoryAccumulator accumulator = gastos.computeIfAbsent(agregado.getCategoriaId(), ignored ->
                    new CategoryAccumulator(agregado.getCategoriaNombre(), agregado.getColor()));
            accumulator.total = accumulator.total.add(valor(agregado.getTotal()));
        }
        return gastos;
    }

    private Totals resumir(
            List<TransaccionRepository.ReporteAgregado> agregados,
            TipoMovimiento tipo,
            Long categoriaId) {
        Totals total = new Totals();
        for (TransaccionRepository.ReporteAgregado agregado : agregados) {
            if (coincide(agregado, tipo, categoriaId)) {
                total.agregar(agregado);
            }
        }
        return total;
    }

    private boolean coincide(
            TransaccionRepository.ReporteAgregado agregado,
            TipoMovimiento tipo,
            Long categoriaId) {
        return (tipo == null || tipo.name().equals(agregado.getTipo()))
                && (categoriaId == null || categoriaId.equals(agregado.getCategoriaId()));
    }

    private FinancialHealth crearSalud(Totals total) {
        if (!total.hayMovimientos()) {
            return new FinancialHealth("Sin datos suficientes",
                    "No hay movimientos en este período para evaluar el flujo.");
        }
        if (total.ahorro().signum() > 0) {
            return new FinancialHealth("Flujo positivo",
                    "Los ingresos registrados superan los gastos registrados en el período.");
        }
        if (total.ahorro().signum() == 0) {
            return new FinancialHealth("Flujo equilibrado",
                    "Los ingresos y gastos registrados son iguales en el período.");
        }
        return new FinancialHealth("Flujo negativo",
                "Los gastos registrados superan los ingresos registrados en el período.");
    }

    private Comparison comparar(BigDecimal actual, BigDecimal anterior) {
        if (anterior.signum() <= 0) {
            return null;
        }
        return new Comparison(porcentaje(actual.subtract(anterior), anterior, 1));
    }

    private int ancho(BigDecimal monto, BigDecimal maximo) {
        if (monto.signum() <= 0 || maximo.signum() <= 0) {
            return 0;
        }
        return monto.multiply(CIEN).divide(maximo, 0, RoundingMode.HALF_UP).intValue();
    }

    private BigDecimal porcentaje(BigDecimal parte, BigDecimal total, int escala) {
        if (total.signum() == 0) {
            return ZERO;
        }
        return parte.multiply(CIEN).divide(total, escala, RoundingMode.HALF_UP);
    }

    private BigDecimal valor(BigDecimal monto) {
        return monto == null ? ZERO : monto;
    }

    private boolean colorValido(String color) {
        return color != null && color.matches("^#[0-9a-fA-F]{6}$");
    }

    private Object porcentajeCsv(Comparison comparison) {
        return comparison == null ? "Sin base comparable" : comparison.variacionPorcentual();
    }

    private String csv(Object... valores) {
        List<String> campos = new ArrayList<>(valores.length);
        for (Object valor : valores) {
            String campo = valor == null ? "" : valor.toString();
            if (valor instanceof String && !campo.isEmpty() && "=+-@\t\r".indexOf(campo.charAt(0)) >= 0) {
                campo = "'" + campo;
            }
            campos.add("\"" + campo.replace("\"", "\"\"") + "\"");
        }
        return String.join(",", campos);
    }

    private void validarFiltros(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Selecciona las fechas inicial y final.");
        }
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final.");
        }
        if (hasta.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha final no puede estar en el futuro.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > 1096) {
            throw new IllegalArgumentException("El período máximo de consulta es de tres años.");
        }
    }

    private void validarCategoria(Long categoriaId, Usuario usuario) {
        if (categoriaId == null) {
            return;
        }
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("La categoría seleccionada no existe."));
        if (categoria.getUsuario() != null && !categoria.getUsuario().getId().equals(usuario.getId())) {
            throw new IllegalArgumentException("La categoría seleccionada no te pertenece.");
        }
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new EntityNotFoundException("No se encontró el usuario autenticado."));
    }

    private static final class Totals {
        private BigDecimal ingresos = ZERO;
        private BigDecimal gastos = ZERO;

        private void agregar(TransaccionRepository.ReporteAgregado agregado) {
            BigDecimal monto = agregado.getTotal() == null ? ZERO : agregado.getTotal();
            if (TipoMovimiento.INGRESO.name().equals(agregado.getTipo())) {
                ingresos = ingresos.add(monto);
            } else if (TipoMovimiento.GASTO.name().equals(agregado.getTipo())) {
                gastos = gastos.add(monto);
            }
        }

        private BigDecimal ingresos() {
            return ingresos;
        }

        private BigDecimal gastos() {
            return gastos;
        }

        private BigDecimal ahorro() {
            return ingresos.subtract(gastos);
        }

        private boolean hayMovimientos() {
            return ingresos.signum() > 0 || gastos.signum() > 0;
        }
    }

    private static final class CategoryAccumulator {
        private final String nombre;
        private final String color;
        private BigDecimal total = ZERO;

        private CategoryAccumulator(String nombre, String color) {
            this.nombre = nombre;
            this.color = color;
        }
    }

    public record ReportData(
            LocalDate desde,
            LocalDate hasta,
            TipoMovimiento tipo,
            Long categoriaId,
            BigDecimal ingresos,
            BigDecimal gastos,
            BigDecimal ahorroNeto,
            BigDecimal tasaAhorro,
            Comparison comparacionIngresos,
            Comparison comparacionGastos,
            Comparison comparacionAhorro,
            List<CategoryAmount> categorias,
            List<MonthAmount> meses,
            List<CategoryIncrease> aumentosGasto,
            List<PresupuestoService.PresupuestoResumen> presupuestosCercanos,
            List<MetaAhorroService.MetaResumen> metasActivas,
            FinancialHealth salud,
            boolean hayMovimientos) {
    }

    public record Comparison(BigDecimal variacionPorcentual) {
    }

    public record CategoryOption(Long id, String nombre, TipoMovimiento tipo, boolean activa) {
    }

    public record CategoryAmount(String nombre, BigDecimal total, BigDecimal porcentaje, String color) {
    }

    public record MonthAmount(
            String etiqueta,
            BigDecimal ingresos,
            BigDecimal gastos,
            BigDecimal ahorroNeto,
            int anchoIngresos,
            int anchoGastos) {
    }

    public record CategoryIncrease(
            String nombre,
            BigDecimal actual,
            BigDecimal anterior,
            BigDecimal variacionPorcentual) {
    }

    public record FinancialHealth(String estado, String explicacion) {
    }
}
