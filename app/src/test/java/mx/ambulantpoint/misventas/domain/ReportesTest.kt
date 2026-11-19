package mx.ambulantpoint.misventas.domain

import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.data.local.dao.ResumenPeriodo
import mx.ambulantpoint.misventas.data.local.dao.TotalPorDia
import mx.ambulantpoint.misventas.domain.puertos.PuertoReportes
import mx.ambulantpoint.misventas.domain.regla.ReglaPeriodos
import mx.ambulantpoint.misventas.domain.regla.ReglaTicketPromedio
import mx.ambulantpoint.misventas.domain.regla.TipoPeriodo
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteVentas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

private val ZONA = TimeZone.getTimeZone("America/Mexico_City")

/** Epoch ms de una fecha y hora en la zona de prueba (mes 1–12). */
private fun ms(anio: Int, mes: Int, dia: Int, hora: Int = 0, min: Int = 0, seg: Int = 0, mili: Int = 0): Long =
    Calendar.getInstance(ZONA).apply { clear(); set(anio, mes - 1, dia, hora, min, seg); set(Calendar.MILLISECOND, mili) }.timeInMillis

private val FIN_DEL_18 = ms(2026, 11, 18, 23, 59, 59, 999)

/** Módulo 6: periodos. El 18-nov-2026 es miércoles. */
class ReglaPeriodosTest {
    private val miercoles = ms(2026, 11, 18, 15, 30)

    @Test fun `hoy va de medianoche al ultimo milisegundo del dia`() {
        val p = ReglaPeriodos.calcular(TipoPeriodo.HOY, miercoles, ZONA)
        assertEquals(ms(2026, 11, 18), p.desde)
        assertEquals(FIN_DEL_18, p.hasta)
    }

    @Test fun `esta semana empieza el lunes`() {
        val p = ReglaPeriodos.calcular(TipoPeriodo.SEMANA, miercoles, ZONA)
        assertEquals(ms(2026, 11, 16), p.desde)
        assertEquals(FIN_DEL_18, p.hasta)
    }

    @Test fun `en domingo la semana empezo el lunes anterior`() {
        val domingo = ms(2026, 11, 22, 10)
        assertEquals(ms(2026, 11, 16), ReglaPeriodos.calcular(TipoPeriodo.SEMANA, domingo, ZONA).desde)
    }

    @Test fun `en lunes la semana empieza ese mismo dia`() {
        val lunes = ms(2026, 11, 16, 8)
        assertEquals(ms(2026, 11, 16), ReglaPeriodos.calcular(TipoPeriodo.SEMANA, lunes, ZONA).desde)
    }

    @Test fun `la semana puede empezar en el mes anterior`() {
        val martes = ms(2026, 12, 1, 9) // lunes 30-nov
        assertEquals(ms(2026, 11, 30), ReglaPeriodos.calcular(TipoPeriodo.SEMANA, martes, ZONA).desde)
    }

    @Test fun `este mes empieza el dia primero`() {
        val p = ReglaPeriodos.calcular(TipoPeriodo.MES, miercoles, ZONA)
        assertEquals(ms(2026, 11, 1), p.desde)
        assertEquals(FIN_DEL_18, p.hasta)
    }

    @Test fun `rango personalizado incluye completos ambos dias y se ordena`() {
        val p = ReglaPeriodos.rango(ms(2026, 11, 10), ms(2026, 11, 3, 12), ZONA)
        assertEquals(TipoPeriodo.RANGO, p.tipo)
        assertEquals(ms(2026, 11, 3), p.desde)
        assertEquals(ms(2026, 11, 10, 23, 59, 59, 999), p.hasta)
    }
}

/** RF 6.1: ticket promedio = total vendido ÷ número de ventas. */
class ReglaTicketPromedioTest {
    @Test fun `division exacta`() = assertEquals(5000L, ReglaTicketPromedio.calcular(15000, 3))
    @Test fun `redondea a centavos mitad hacia arriba`() {
        assertEquals(3334L, ReglaTicketPromedio.calcular(10001, 3)) // 33.3367 → 33.34
        assertEquals(3333L, ReglaTicketPromedio.calcular(10000, 3)) // 33.3333 → 33.33
        assertEquals(1L, ReglaTicketPromedio.calcular(1, 2)) // 0.005 → 0.01
    }
    @Test fun `sin ventas el ticket es cero`() = assertEquals(0L, ReglaTicketPromedio.calcular(0, 0))
}

private class ReportesFalsos(private val resumen: ResumenPeriodo, private val dias: List<TotalPorDia>) : PuertoReportes {
    var consultasPorDia = 0
    override suspend fun resumenPeriodo(desde: Long, hasta: Long) = resumen
    override suspend fun totalesPorDia(desde: Long, hasta: Long): List<TotalPorDia> { consultasPorDia++; return dias }
}

/** RF 6.1 — Reporte de ventas por periodo. */
class GenerarReporteVentasTest {
    private val periodo = ReglaPeriodos.calcular(TipoPeriodo.SEMANA, ms(2026, 11, 18, 12), ZONA)

    @Test fun `arma el reporte con ticket promedio y desglose por dia`() = runTest {
        val dias = listOf(TotalPorDia("2026-11-16", 2, 9000), TotalPorDia("2026-11-18", 1, 6001))
        val r = GenerarReporteVentas(ReportesFalsos(ResumenPeriodo(3, 15001, 500, 1, 2000), dias))(periodo)
        assertEquals(15001L, r.totalVendidoCentavos)
        assertEquals(3, r.numeroVentas)
        assertEquals(5000L, r.ticketPromedioCentavos)
        assertEquals(500L, r.descuentosCentavos)
        assertEquals(1, r.numeroCanceladas)
        assertEquals(2000L, r.importeCanceladasCentavos)
        assertEquals(dias, r.porDia)
        assertFalse(r.sinVentas)
    }

    @Test fun `sin ventas ni canceladas muestra el mensaje del DFR y no consulta el desglose`() = runTest {
        val puerto = ReportesFalsos(ResumenPeriodo(0, 0, 0, 0, 0), emptyList())
        val r = GenerarReporteVentas(puerto)(periodo)
        assertTrue(r.sinVentas)
        assertEquals(0L, r.ticketPromedioCentavos)
        assertEquals(0, puerto.consultasPorDia)
    }

    @Test fun `si solo hubo canceladas se muestra el reporte con ellas`() = runTest {
        val r = GenerarReporteVentas(ReportesFalsos(ResumenPeriodo(0, 0, 0, 2, 4500), emptyList()))(periodo)
        assertFalse(r.sinVentas)
        assertEquals(2, r.numeroCanceladas)
        assertTrue(r.porDia.isEmpty())
    }
}
