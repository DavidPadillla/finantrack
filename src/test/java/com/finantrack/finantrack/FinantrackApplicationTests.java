package com.finantrack.finantrack;

import com.finantrack.finantrack.model.TipoMovimiento;
import com.finantrack.finantrack.repository.AporteMetaRepository;
import com.finantrack.finantrack.repository.CategoriaRepository;
import com.finantrack.finantrack.repository.MetaAhorroRepository;
import com.finantrack.finantrack.repository.PresupuestoRepository;
import com.finantrack.finantrack.repository.TransaccionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

@SpringBootTest
class FinantrackApplicationTests {

	@Autowired
	private TransaccionRepository transaccionRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private PresupuestoRepository presupuestoRepository;

	@Autowired
	private MetaAhorroRepository metaAhorroRepository;

	@Autowired
	private AporteMetaRepository aporteMetaRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void dashboardQueriesWorkForUserWithoutMovements() {
		Long usuarioInexistente = Long.MAX_VALUE;
		LocalDate inicio = LocalDate.now().withDayOfMonth(1);
		LocalDate hoy = LocalDate.now();

		Assertions.assertTrue(transaccionRepository.totalPorUsuarioYTipo(
				usuarioInexistente, TipoMovimiento.INGRESO).signum() == 0);
		Assertions.assertTrue(transaccionRepository.totalPorUsuarioTipoYPeriodo(
				usuarioInexistente, TipoMovimiento.GASTO, inicio, hoy).signum() == 0);
		Assertions.assertTrue(transaccionRepository.gastosPorCategoriaYPeriodo(
				usuarioInexistente, TipoMovimiento.GASTO, inicio, hoy).isEmpty());
		Assertions.assertTrue(transaccionRepository.totalesMensuales(
				usuarioInexistente, inicio.minusMonths(5), hoy).isEmpty());
		Assertions.assertTrue(transaccionRepository.findByUsuario_IdOrderByFechaDescIdDesc(
				usuarioInexistente, org.springframework.data.domain.PageRequest.of(0, 8)).isEmpty());
		Assertions.assertTrue(transaccionRepository.buscarRecientes(
				usuarioInexistente, "prueba", org.springframework.data.domain.PageRequest.of(0, 8)).isEmpty());
		Assertions.assertTrue(transaccionRepository.totalPorCategoriaYPeriodo(
				usuarioInexistente, Long.MAX_VALUE, TipoMovimiento.GASTO, inicio, hoy).signum() == 0);
		Assertions.assertTrue(categoriaRepository.findByUsuario_Id(usuarioInexistente).isEmpty());
		Assertions.assertTrue(categoriaRepository.buscarDisponibles(usuarioInexistente, null).isEmpty());
		Assertions.assertTrue(presupuestoRepository.findByUsuario_IdAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
				usuarioInexistente, hoy, hoy).isEmpty());
		Assertions.assertTrue(metaAhorroRepository.findByUsuario_Id(usuarioInexistente).isEmpty());
		Assertions.assertTrue(metaAhorroRepository.totalAportesPorMeta(List.of(Long.MAX_VALUE)).isEmpty());
		Assertions.assertTrue(aporteMetaRepository.findByMeta_IdOrderByFechaDesc(Long.MAX_VALUE).isEmpty());
	}
}
