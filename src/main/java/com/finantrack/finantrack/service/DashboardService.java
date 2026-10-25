package com.finantrack.finantrack.service;

import com.finantrack.finantrack.model.EstadoMeta;
import com.finantrack.finantrack.model.MetaAhorro;
import com.finantrack.finantrack.model.Presupuesto;
import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.model.Transaccion;
import com.finantrack.finantrack.model.Usuario;
import com.finantrack.finantrack.repository.MetaAhorroRepository;
import com.finantrack.finantrack.repository.PresupuestoRepository;
import com.finantrack.finantrack.repository.TransaccionRepository;
import com.finantrack.finantrack.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DashboardService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final List<String> PALETTE = List.of(
            "#5B8DEF", "#8B6EE8", "#38B889", "#E6A23C", "#E66B75", "#45A9C7"
    );

    private final UsuarioRepository usuarioRepository;
    private final TransaccionRepository transaccionRepository;
    private final PresupuestoRepository presupuestoRepository;
    private final MetaAhorroRepository metaAhorroRepository;

    public DashboardService(
            UsuarioRepository usuarioRepository,
            TransaccionRepository transaccionRepository,
            PresupuestoRepository presupuestoRepository,
            MetaAhorroRepository metaAhorroRepository) {
        this.usuarioRepository = usuarioRepository;
        this.transaccionRepository = transaccionRepository;
        this.presupuestoRepository = presupuestoRepository;
        this.metaAhorroRepository = metaAhorroRepository;
    }

    @Transactional(readOnly = true)
    public DashboardData cargar(String emailAutenticado, String busqueda) {
        return cargar(emailAutenticado, busqueda, 0);
    }

    @Transactional(readOnly = true)
    public DashboardData cargar(String emailAutenticado, String busqueda, int pagina) {
        Usuario usuario = usuarioRepository.findByEmail(emailAutenticado)
                .orElseThrow(() -> new EntityNotFoundException("No se encontró el usuario autenticado."));
        Long usuarioId = usuario.getId();
        LocalDate hoy = LocalDate.now();
        YearMonth mesActual = YearMonth.from(hoy);
        YearMonth mesAnterior = mesActual.minusMonths(1);
        LocalDate finPeriodoActual = hoy;
        LocalDate finPeriodoAnterior = mesAnterior.atDay(Math.min(hoy.getDayOfMonth(), mesAnterior.lengthOfMonth()));

        BigDecimal ingresosMes = total(usuarioId, TipoMovimiento.INGRESO, mesActual.atDay(1), finPeriodoActual);
        BigDecimal gastosMes = total(usuarioId, TipoMovimiento.GASTO, mesActual.atDay(1), finPeriodoActual);
        BigDecimal ingresosAnterior = total(usuarioId, TipoMovimiento.INGRESO, mesAnterior.atDay(1), finPeriodoAnterior);
        BigDecimal gastosAnterior = total(usuarioId, TipoMovimiento.GASTO, mesAnterior.atDay(1), finPeriodoAnterior);
        BigDecimal ingresosAcumulados = valor(transaccionRepository.totalPorUsuarioYTipo(usuarioId, TipoMovimiento.INGRESO));
        BigDecimal gastosAcumulados = valor(transaccionRepository.totalPorUsuarioYTipo(usuarioId, TipoMovimiento.GASTO));

        List<MonthBar> meses = cargarMeses(usuarioId, mesActual, hoy);
        List<CategoryExpense> categorias = cargarCategorias(usuarioId, mesActual, hoy);
        TransactionPage transacciones = cargarTransacciones(usuario, busqueda, pagina);
        List<GoalProgress> metas = cargarMetas(usuarioId);
        List<BudgetProgress> presupuestos = cargarPresupuestos(usuarioId, hoy);

        return new DashboardData(
                usuario.getNombre(),
                usuario.getEmail(),
                nombreCorto(usuario.getNombre()),
                iniciales(usuario.getNombre()),
                mesActual.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")),
                ingresosMes,
                gastosMes,
                ingresosAcumulados.subtract(gastosAcumulados),
                ingresosMes.subtract(gastosMes),
                comparar(ingresosMes, ingresosAnterior, true),
                comparar(gastosMes, gastosAnterior, false),
                meses,
                categorias,
                crearEstiloDona(categorias),
                transacciones.movimientos(),
                transacciones.pagina(),
                transacciones.totalPaginas(),
                transacciones.tieneAnterior(),
                transacciones.tieneSiguiente(),
                metas,
                presupuestos,
                busqueda == null ? "" : busqueda.trim(),
                !meses.isEmpty() && meses.stream().anyMatch(mes -> mes.ingresos().signum() > 0 || mes.gastos().signum() > 0),
                !categorias.isEmpty(),
                !transacciones.movimientos().isEmpty()
        );
    }

    private BigDecimal total(Long usuarioId, TipoMovimiento tipo, LocalDate inicio, LocalDate fin) {
        return valor(transaccionRepository.totalPorUsuarioTipoYPeriodo(
                usuarioId, tipo, inicio, fin));
    }

    private List<MonthBar> cargarMeses(Long usuarioId, YearMonth mesActual, LocalDate hoy) {
        YearMonth primerMes = mesActual.minusMonths(5);
        Map<YearMonth, MonthlyAmounts> porMes = new HashMap<>();
        for (TransaccionRepository.MonthlyTotal total : transaccionRepository.totalesMensuales(
                usuarioId, primerMes.atDay(1), hoy)) {
            YearMonth mes = YearMonth.of(total.getYear(), total.getMonth());
            MonthlyAmounts amounts = porMes.computeIfAbsent(mes, ignored -> new MonthlyAmounts());
            if (TipoMovimiento.INGRESO.name().equals(total.getTipo())) {
                amounts.ingresos = valor(total.getTotal());
            } else if (TipoMovimiento.GASTO.name().equals(total.getTipo())) {
                amounts.gastos = valor(total.getTotal());
            }
        }

        BigDecimal maximo = porMes.values().stream()
                .map(amounts -> amounts.ingresos.max(amounts.gastos))
                .max(BigDecimal::compareTo)
                .orElse(ZERO);
        List<MonthBar> resultado = new ArrayList<>();
        for (int offset = 5; offset >= 0; offset--) {
            YearMonth mes = mesActual.minusMonths(offset);
            MonthlyAmounts amounts = porMes.getOrDefault(mes, new MonthlyAmounts());
            resultado.add(new MonthBar(
                    mes.getMonth().getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es")),
                    amounts.ingresos,
                    amounts.gastos,
                    altura(amounts.ingresos, maximo),
                    altura(amounts.gastos, maximo)
            ));
        }
        return resultado;
    }

    private List<CategoryExpense> cargarCategorias(Long usuarioId, YearMonth mes, LocalDate hoy) {
        List<TransaccionRepository.CategoriaTotal> totales =
                transaccionRepository.gastosPorCategoriaYPeriodo(
                        usuarioId, TipoMovimiento.GASTO, mes.atDay(1), hoy);
        BigDecimal suma = totales.stream()
                .map(TransaccionRepository.CategoriaTotal::getTotal)
                .map(DashboardService::valor)
                .reduce(ZERO, BigDecimal::add);

        List<CategoryExpense> resultado = new ArrayList<>();
        for (int index = 0; index < totales.size(); index++) {
            TransaccionRepository.CategoriaTotal total = totales.get(index);
            BigDecimal monto = valor(total.getTotal());
            BigDecimal porcentaje = suma.signum() == 0
                    ? ZERO
                    : monto.multiply(BigDecimal.valueOf(100)).divide(suma, 1, RoundingMode.HALF_UP);
            String color = total.getColor() != null && HEX_COLOR.matcher(total.getColor()).matches()
                    ? total.getColor()
                    : PALETTE.get(index % PALETTE.size());
            resultado.add(new CategoryExpense(
                    total.getNombre(),
                    monto,
                    porcentaje,
                    color
            ));
        }
        return resultado;
    }

    private TransactionPage cargarTransacciones(Usuario usuario, String busqueda, int pagina) {
        Long usuarioId = usuario.getId();
        int paginaSegura = Math.max(pagina, 0);
        var paginaMovimientos = busqueda == null || busqueda.isBlank()
                ? transaccionRepository.findByUsuario_IdOrderByFechaDescIdDesc(usuarioId, PageRequest.of(paginaSegura, 8))
                : transaccionRepository.buscarRecientes(usuarioId, busqueda.trim(), PageRequest.of(paginaSegura, 8));

        List<TransactionRow> movimientos = paginaMovimientos.getContent().stream()
                .map(transaccion -> new TransactionRow(
                        transaccion.getDescripcion() == null || transaccion.getDescripcion().isBlank()
                                ? "Movimiento"
                                : transaccion.getDescripcion(),
                        transaccion.getCategoria() == null
                                || (transaccion.getCategoria().getUsuario() != null
                                && !transaccion.getCategoria().getUsuario().getId().equals(usuarioId))
                                ? "Sin categoría"
                                : transaccion.getCategoria().getNombre(),
                        transaccion.getFecha(),
                        transaccion.getTipo(),
                        transaccion.getMonto()
                ))
                .toList();
        return new TransactionPage(
                movimientos,
                paginaMovimientos.getNumber(),
                paginaMovimientos.getTotalPages(),
                paginaMovimientos.hasPrevious(),
                paginaMovimientos.hasNext()
        );
    }

    private List<GoalProgress> cargarMetas(Long usuarioId) {
        List<MetaAhorro> metas = metaAhorroRepository.findByUsuario_Id(usuarioId).stream()
                .filter(meta -> meta.getEstado() == EstadoMeta.ACTIVA)
                .toList();
        if (metas.isEmpty()) {
            return List.of();
        }

        Map<Long, BigDecimal> aportes = new HashMap<>();
        List<Long> metaIds = metas.stream().map(MetaAhorro::getId).toList();
        metaAhorroRepository.totalAportesPorMeta(metaIds)
                .forEach(aporte -> aportes.put(aporte.getMetaId(), valor(aporte.getTotal())));

        return metas.stream()
                .map(meta -> {
                    BigDecimal aportado = aportes.getOrDefault(meta.getId(), ZERO);
                    BigDecimal objetivo = meta.getMontoObjetivo();
                    int porcentaje = objetivo.signum() > 0
                            ? aportado.multiply(BigDecimal.valueOf(100))
                                    .divide(objetivo, 0, RoundingMode.HALF_UP)
                                    .min(BigDecimal.valueOf(100))
                                    .intValue()
                            : 0;
                    return new GoalProgress(
                            meta.getNombre(),
                            objetivo,
                            aportado,
                            porcentaje,
                            meta.getFechaObjetivo()
                    );
                })
                .toList();
    }

    private List<BudgetProgress> cargarPresupuestos(Long usuarioId, LocalDate hoy) {
        return presupuestoRepository.findByUsuario_IdAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        usuarioId, hoy, hoy)
                .stream()
                .map(presupuesto -> crearProgresoPresupuesto(usuarioId, presupuesto, hoy))
                .toList();
    }

    private BudgetProgress crearProgresoPresupuesto(Long usuarioId, Presupuesto presupuesto, LocalDate hoy) {
        BigDecimal gastado = valor(transaccionRepository.totalPorCategoriaYPeriodo(
                usuarioId,
                presupuesto.getCategoria().getId(),
                TipoMovimiento.GASTO,
                presupuesto.getFechaInicio(),
                hoy.isBefore(presupuesto.getFechaFin()) ? hoy : presupuesto.getFechaFin()
        ));
        BigDecimal limite = presupuesto.getMontoLimite();
        int porcentaje = calcularPorcentaje(gastado, limite);
        return new BudgetProgress(
                presupuesto.getCategoria().getNombre(),
                limite,
                gastado,
                porcentaje,
                Math.min(porcentaje, 100),
                presupuesto.getFechaFin()
        );
    }

    private Comparison comparar(BigDecimal actual, BigDecimal anterior, boolean subirEsBueno) {
        if (anterior.signum() == 0) {
            return null;
        }
        BigDecimal cambio = actual.subtract(anterior)
                .multiply(BigDecimal.valueOf(100))
                .divide(anterior, 1, RoundingMode.HALF_UP);
        return new Comparison(
                cambio.abs(),
                cambio.signum() > 0,
                cambio.signum() == 0 || (cambio.signum() > 0) == subirEsBueno
        );
    }

    private String crearEstiloDona(List<CategoryExpense> categorias) {
        if (categorias.isEmpty()) {
            return "conic-gradient(#e9edf3 0% 100%)";
        }
        StringBuilder gradient = new StringBuilder("conic-gradient(");
        BigDecimal cursor = ZERO;
        for (int index = 0; index < categorias.size(); index++) {
            CategoryExpense categoria = categorias.get(index);
            BigDecimal siguiente = index == categorias.size() - 1
                    ? BigDecimal.valueOf(100)
                    : cursor.add(categoria.porcentaje()).min(BigDecimal.valueOf(100));
            gradient.append(categoria.color())
                    .append(' ')
                    .append(cursor.stripTrailingZeros().toPlainString())
                    .append("% ")
                    .append(siguiente.stripTrailingZeros().toPlainString())
                    .append("%, ");
            cursor = siguiente;
        }
        if (cursor.compareTo(BigDecimal.valueOf(100)) < 0) {
            gradient.append("#dfe5ee ").append(cursor.stripTrailingZeros().toPlainString()).append("% 100%, ");
        }
        gradient.setLength(gradient.length() - 2);
        return gradient.append(')').toString();
    }

    private int altura(BigDecimal monto, BigDecimal maximo) {
        if (monto.signum() <= 0 || maximo.signum() <= 0) {
            return 0;
        }
        return Math.max(4, monto.multiply(BigDecimal.valueOf(100))
                .divide(maximo, 0, RoundingMode.HALF_UP).intValue());
    }

    private int calcularPorcentaje(BigDecimal valor, BigDecimal limite) {
        if (limite.signum() <= 0) {
            return 0;
        }
        return valor.multiply(BigDecimal.valueOf(100))
                .divide(limite, 0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(Integer.MAX_VALUE))
                .intValue();
    }

    private static BigDecimal valor(BigDecimal monto) {
        return monto == null ? ZERO : monto;
    }

    private String nombreCorto(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "Hola";
        }
        return nombre.trim().split("\\s+")[0];
    }

    private String iniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "?";
        }
        String[] partes = nombre.trim().split("\\s+");
        if (partes.length == 1) {
            return partes[0].substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return (partes[0].substring(0, 1) + partes[partes.length - 1].substring(0, 1))
                .toUpperCase(Locale.ROOT);
    }

    private static class MonthlyAmounts {
        private BigDecimal ingresos = ZERO;
        private BigDecimal gastos = ZERO;
    }

    public record DashboardData(
            String nombreCompleto,
            String email,
            String nombreCorto,
            String iniciales,
            String nombreMes,
            BigDecimal ingresosMes,
            BigDecimal gastosMes,
            BigDecimal saldoNeto,
            BigDecimal ahorroNetoMes,
            Comparison comparacionIngresos,
            Comparison comparacionGastos,
            List<MonthBar> meses,
            List<CategoryExpense> categorias,
            String estiloDona,
            List<TransactionRow> transacciones,
            int pagina,
            int totalPaginas,
            boolean tieneAnterior,
            boolean tieneSiguiente,
            List<GoalProgress> metas,
            List<BudgetProgress> presupuestos,
            String busqueda,
            boolean hayDatosGrafico,
            boolean hayGastosPorCategoria,
            boolean hayTransacciones) {
    }

    public record Comparison(BigDecimal porcentaje, boolean aumentaron, boolean favorable) {
    }

    public record MonthBar(String etiqueta, BigDecimal ingresos, BigDecimal gastos, int alturaIngresos, int alturaGastos) {
    }

    public record CategoryExpense(String nombre, BigDecimal monto, BigDecimal porcentaje, String color) {
    }

    public record TransactionRow(String descripcion, String categoria, LocalDate fecha, TipoMovimiento tipo, BigDecimal monto) {
    }

    private record TransactionPage(
            List<TransactionRow> movimientos,
            int pagina,
            int totalPaginas,
            boolean tieneAnterior,
            boolean tieneSiguiente) {
    }

    public record GoalProgress(
            String nombre,
            BigDecimal objetivo,
            BigDecimal aportado,
            int porcentaje,
            LocalDate fechaObjetivo) {
    }

    public record BudgetProgress(
            String categoria,
            BigDecimal limite,
            BigDecimal gastado,
            int porcentaje,
            int porcentajeBarra,
            LocalDate fechaFin) {

        public boolean isAlerta() {
            return porcentaje >= 80;
        }
    }
}
