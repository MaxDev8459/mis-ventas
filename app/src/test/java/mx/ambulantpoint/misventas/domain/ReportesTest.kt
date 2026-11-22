package mx.ambulantpoint.misventas.domain

import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.data.local.dao.ProductoConCategoria
import mx.ambulantpoint.misventas.data.local.dao.ProductoVendido
import mx.ambulantpoint.misventas.data.local.dao.TotalCategoria
import mx.ambulantpoint.misventas.data.local.dao.TotalFormaPago
import mx.ambulantpoint.misventas.data.local.dao.VentasRecientes
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.local.dao.ResumenPeriodo
import mx.ambulantpoint.misventas.data.local.dao.TotalPorDia
import mx.ambulantpoint.misventas.domain.puertos.PuertoReportes
import mx.ambulantpoint.misventas.domain.regla.NivelExistencia
import mx.ambulantpoint.misventas.domain.regla.OrdenMasVendidos
import mx.ambulantpoint.misventas.domain.regla.ReglaPeriodos
import mx.ambulantpoint.misventas.domain.regla.ReglaPorcentaje
import mx.ambulantpoint.misventas.domain.regla.ReglaTicketPromedio
import mx.ambulantpoint.misventas.domain.regla.TipoPeriodo
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteCategorias
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteFormasPago
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteMasVendidos
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteVentas
import mx.ambulantpoint.misventas.domain.usecase.ReporteInventario
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

private class ReportesFalsos(
    private val resumen: ResumenPeriodo = ResumenPeriodo(0, 0, 0, 0, 0),
    private val dias: List<TotalPorDia> = emptyList(),
    private val formas: List<TotalFormaPago> = emptyList(),
    private val productos: List<ProductoVendido> = emptyList(),
    private val categorias: List<TotalCategoria> = emptyList(),
) : PuertoReportes {
    var consultasPorDia = 0
    override suspend fun resumenPeriodo(desde: Long, hasta: Long) = resumen
    override suspend fun totalesPorDia(desde: Long, hasta: Long): List<TotalPorDia> { consultasPorDia++; return dias }
    override suspend fun totalesPorFormaPago(desde: Long, hasta: Long) = formas
    override suspend fun productosVendidos(desde: Long, hasta: Long) = productos
    override suspend fun totalesPorCategoria(desde: Long, hasta: Long) = categorias
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

/** RF 6.2 / 6.4: porcentaje respecto al total, en décimas de punto. */
class ReglaPorcentajeTest {
    @Test fun `exacto`() = assertEquals(250L, ReglaPorcentaje.decimas(2500, 10000))
    @Test fun `redondea mitad hacia arriba`() {
        assertEquals(333L, ReglaPorcentaje.decimas(1, 3)) // 33.33 → 33.3
        assertEquals(667L, ReglaPorcentaje.decimas(2, 3)) // 66.67 → 66.7
        assertEquals(1L, ReglaPorcentaje.decimas(5, 10000)) // 0.05 → 0.1
    }
    @Test fun `total cero da cero`() = assertEquals(0L, ReglaPorcentaje.decimas(0, 0))
    @Test fun `formato`() {
        assertEquals("45.3 %", Formato.porcentaje(453))
        assertEquals("100.0 %", Formato.porcentaje(1000))
        assertEquals("0.1 %", Formato.porcentaje(1))
    }
}

/** RF 6.2 — Ventas por forma de pago. */
class GenerarReporteFormasPagoTest {
    private val periodo = ReglaPeriodos.calcular(TipoPeriodo.HOY, ms(2026, 11, 18, 12), ZONA)

    @Test fun `numero de ventas, importe y porcentaje, de mayor a menor`() = runTest {
        val formas = listOf(
            TotalFormaPago("t", "Transferencia", false, 1, 2500),
            TotalFormaPago("e", "Efectivo", true, 3, 7500),
        )
        val r = GenerarReporteFormasPago(ReportesFalsos(formas = formas))(periodo)
        assertEquals(10000L, r.totalCentavos)
        assertEquals(listOf("Efectivo", "Transferencia"), r.filas.map { it.nombre })
        assertEquals(listOf(3, 1), r.filas.map { it.numeroVentas })
        assertEquals(listOf(750L, 250L), r.filas.map { it.porcentajeDecimas })
        assertFalse(r.sinVentas)
    }

    @Test fun `sin ventas`() = runTest {
        assertTrue(GenerarReporteFormasPago(ReportesFalsos())(periodo).sinVentas)
    }
}

/** RF 6.4 — Ventas por categoría. */
class GenerarReporteCategoriasTest {
    private val periodo = ReglaPeriodos.calcular(TipoPeriodo.HOY, ms(2026, 11, 18, 12), ZONA)

    @Test fun `importe y porcentaje, de mayor a menor`() = runTest {
        val cats = listOf(TotalCategoria("b", "Bebidas", 1000), TotalCategoria("d", "Dulces", 3000))
        val r = GenerarReporteCategorias(ReportesFalsos(categorias = cats))(periodo)
        assertEquals(listOf("Dulces", "Bebidas"), r.filas.map { it.nombre })
        assertEquals(listOf(750L, 250L), r.filas.map { it.porcentajeDecimas })
    }
}

/** RF 6.3 — Productos más vendidos. */
class GenerarReporteMasVendidosTest {
    private val periodo = ReglaPeriodos.calcular(TipoPeriodo.HOY, ms(2026, 11, 18, 12), ZONA)
    private val productos = listOf(
        ProductoVendido("a", "Papas", "Botanas", TipoVenta.UNIDAD, 3_000, 4_500),
        ProductoVendido("b", "Cacahuate", "Botanas", TipoVenta.PESO, 2_500, 20_000),
        ProductoVendido("c", "Refresco", "Bebidas", TipoVenta.UNIDAD, 10_000, 20_000),
        ProductoVendido("d", "Gomitas", "Dulces", TipoVenta.PESO, 500, 6_000),
    )

    @Test fun `por importe es una sola lista de mayor a menor`() = runTest {
        val r = GenerarReporteMasVendidos(ReportesFalsos(productos = productos))(periodo, OrdenMasVendidos.IMPORTE)
        // Empate en importe (Cacahuate y Refresco): por nombre.
        assertEquals(listOf("Cacahuate", "Refresco", "Gomitas", "Papas"), r.filas.map { it.nombre })
    }

    @Test fun `por cantidad se ordena por separado cada tipo de venta`() = runTest {
        val r = GenerarReporteMasVendidos(ReportesFalsos(productos = productos))(periodo, OrdenMasVendidos.CANTIDAD)
        assertEquals(listOf("Refresco", "Papas", "Cacahuate", "Gomitas"), r.filas.map { it.nombre })
    }

    @Test fun `sin ventas`() = runTest {
        assertTrue(GenerarReporteMasVendidos(ReportesFalsos())(periodo, OrdenMasVendidos.CANTIDAD).sinVentas)
    }
}

/** RF 6.5 — Reporte de inventario. */
class ReporteInventarioTest {
    private fun prod(id: String, tipo: TipoVenta, existencia: Long, activo: Boolean = true) = ProductoConCategoria(
        Producto(id, id, id, "cat", tipo, 1000, existencia, "/img.jpg", activo, creadoEn = 0, actualizadoEn = 0),
        "Categoría",
    )

    @Test fun `cuenta activos, con existencia baja y agotados`() {
        val productos = listOf(
            prod("normal", TipoVenta.UNIDAD, 20_000),
            prod("baja-umbral", TipoVenta.UNIDAD, 5_000),     // sin 7 días con ventas: ≤ 5 pzas
            prod("baja-peso", TipoVenta.PESO, 1_000),         // ≤ 1 kg
            prod("baja-promedio", TipoVenta.UNIDAD, 20_000),  // 7 días, 70 pzas ⇒ 10/día ⇒ baja si ≤ 20
            prod("agotado", TipoVenta.UNIDAD, 0),
            prod("desactivado", TipoVenta.UNIDAD, 0, activo = false),
        )
        val recientes = mapOf("baja-promedio" to VentasRecientes(7, 70_000))
        val r = ReporteInventario.armar(productos, recientes)
        assertEquals(5, r.activos)
        assertEquals(3, r.conExistenciaBaja)
        assertEquals(1, r.agotados)
        assertEquals(NivelExistencia.NORMAL, r.filas.first { it.producto.producto.id == "normal" }.nivel)
        assertEquals(NivelExistencia.AGOTADO, r.filas.first { it.producto.producto.id == "agotado" }.nivel)
    }

    @Test fun `con 7 dias de ventas y existencia sobrada no es baja`() {
        val r = ReporteInventario.armar(listOf(prod("x", TipoVenta.UNIDAD, 3_000)), mapOf("x" to VentasRecientes(7, 7_000)))
        // promedio 1 pza/día ⇒ baja si ≤ 2 pzas; hay 3
        assertEquals(NivelExistencia.NORMAL, r.filas.single().nivel)
    }
}
