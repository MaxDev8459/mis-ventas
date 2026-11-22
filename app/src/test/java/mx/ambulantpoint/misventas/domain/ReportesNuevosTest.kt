package mx.ambulantpoint.misventas.domain

import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.data.local.dao.ClienteConSaldo
import mx.ambulantpoint.misventas.data.local.dao.ProductoVendido
import mx.ambulantpoint.misventas.data.local.dao.ResumenVentas
import mx.ambulantpoint.misventas.data.local.dao.SalidaAjuste
import mx.ambulantpoint.misventas.data.local.dao.TotalFormaPago
import mx.ambulantpoint.misventas.data.local.dao.VentasPorGrupo
import mx.ambulantpoint.misventas.data.local.entity.Cliente
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.puertos.PuertoReportesExtra
import mx.ambulantpoint.misventas.domain.regla.Diferencia
import mx.ambulantpoint.misventas.domain.regla.Periodo
import mx.ambulantpoint.misventas.domain.regla.ReglaComparacion
import mx.ambulantpoint.misventas.domain.regla.ReglaHorario
import mx.ambulantpoint.misventas.domain.regla.ReglaMermas
import mx.ambulantpoint.misventas.domain.regla.ReglaPeriodos
import mx.ambulantpoint.misventas.domain.regla.TipoPeriodo
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteFiado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/** Fase 5: comparación, hora y día, mermas y fiado (docs/11-ESPEC-REPORTES.md). */
class ReportesNuevosTest {
    private val tz = TimeZone.getTimeZone("America/Mexico_City")
    private fun ms(t: String) = LocalDateTime.parse(t).atZone(ZoneId.of("America/Mexico_City")).toInstant().toEpochMilli()

    // ── Periodo anterior: el mismo tramo ──

    @Test fun `hoy a media tarde se compara con ayer hasta la misma hora`() {
        val ahora = ms("2026-11-23T15:30")
        val ant = ReglaComparacion.periodoAnterior(ReglaPeriodos.calcular(TipoPeriodo.HOY, ahora, tz), ahora, tz)
        assertEquals(ms("2026-11-22T00:00"), ant.desde)
        assertEquals(ms("2026-11-22T15:30"), ant.hasta)
    }

    @Test fun `semana de lunes a hoy contra los mismos dias de la semana pasada`() {
        val ahora = ms("2026-11-25T10:00") // miércoles
        val ant = ReglaComparacion.periodoAnterior(ReglaPeriodos.calcular(TipoPeriodo.SEMANA, ahora, tz), ahora, tz)
        assertEquals(ms("2026-11-16T00:00"), ant.desde) // lunes pasado
        assertEquals(ms("2026-11-18T10:00"), ant.hasta) // miércoles pasado a la misma hora
    }

    @Test fun `mes al dia 23 contra el 1 al 23 del mes pasado`() {
        val ahora = ms("2026-11-23T12:00")
        val ant = ReglaComparacion.periodoAnterior(ReglaPeriodos.calcular(TipoPeriodo.MES, ahora, tz), ahora, tz)
        assertEquals(ms("2026-10-01T00:00"), ant.desde)
        assertEquals(ms("2026-10-23T12:00"), ant.hasta)
    }

    @Test fun `mes pasado mas corto se corta en su ultimo dia`() {
        val ahora = ms("2027-03-31T18:00")
        val ant = ReglaComparacion.periodoAnterior(ReglaPeriodos.calcular(TipoPeriodo.MES, ahora, tz), ahora, tz)
        assertEquals(ms("2027-02-01T00:00"), ant.desde)
        assertEquals(ms("2027-02-28T18:00"), ant.hasta)
    }

    @Test fun `enero se compara con diciembre del año anterior`() {
        val ahora = ms("2027-01-10T09:00")
        val ant = ReglaComparacion.periodoAnterior(ReglaPeriodos.calcular(TipoPeriodo.MES, ahora, tz), ahora, tz)
        assertEquals(ms("2026-12-01T00:00"), ant.desde)
        assertEquals(ms("2026-12-10T09:00"), ant.hasta)
    }

    @Test fun `rango pasado contra los mismos dias inmediatamente antes`() {
        val ahora = ms("2026-11-23T12:00")
        val rango = ReglaPeriodos.rango(ms("2026-11-10T08:00"), ms("2026-11-16T08:00"), tz) // 7 días
        val ant = ReglaComparacion.periodoAnterior(rango, ahora, tz)
        assertEquals(ms("2026-11-03T00:00"), ant.desde)
        assertEquals(rango.hasta - 7 * 86_400_000L, ant.hasta)
    }

    // ── Diferencias ──

    @Test fun `subida con porcentaje`() {
        val d = Diferencia(320_000, 286_000)
        assertEquals(34_000L, d.diferencia)
        assertEquals(119L, d.porcentajeDecimas) // +11.9 %
    }

    @Test fun `bajada con porcentaje negativo`() = assertEquals(-250L, Diferencia(75, 100).porcentajeDecimas)
    @Test fun `igual es cero`() = assertEquals(0L, Diferencia(100, 100).porcentajeDecimas)
    @Test fun `sin ventas antes no hay porcentaje`() = assertNull(Diferencia(100, 0).porcentajeDecimas)
    @Test fun `redondeo a una decima`() = assertEquals(333L, Diferencia(4, 3).porcentajeDecimas) // 33.33 → 33.3

    // ── Productos que subieron y bajaron ──

    private fun pv(id: String, nombre: String, importe: Long) = ProductoVendido(id, nombre, "Cat", TipoVenta.UNIDAD, 1_000, importe)

    @Test fun `subieron y bajaron por importe con producto nuevo y que dejo de venderse`() {
        val (sub, baj) = ReglaComparacion.cambiosProductos(
            actual = listOf(pv("c", "Carlota", 10_000), pv("n", "Nuevo", 3_000), pv("q", "Queso", 1_000)),
            anterior = listOf(pv("c", "Carlota", 6_000), pv("v", "Viejo", 2_000), pv("q", "Queso", 4_000)),
        )
        assertEquals(listOf("Carlota" to 4_000L, "Nuevo" to 3_000L), sub.map { it.nombre to it.diferenciaCentavos })
        assertEquals(listOf("Queso" to -3_000L, "Viejo" to -2_000L), baj.map { it.nombre to it.diferenciaCentavos })
    }

    @Test fun `solo 5 de cada uno y sin los que no cambiaron`() {
        val actual = (1..8).map { pv("p$it", "P$it", it * 1_000L) } + pv("x", "Igual", 500)
        val (sub, baj) = ReglaComparacion.cambiosProductos(actual, listOf(pv("x", "Igual", 500)))
        assertEquals(5, sub.size)
        assertEquals("P8", sub.first().nombre)
        assertTrue(baj.isEmpty())
    }

    // ── Hora y día ──

    @Test fun `horas de la primera a la ultima con huecos en cero y la mejor resaltada`() {
        val b = ReglaHorario.porHora(listOf(VentasPorGrupo(9, 2, 4_000), VentasPorGrupo(12, 5, 9_000)))
        assertEquals(listOf("09:00–10:00", "10:00–11:00", "11:00–12:00", "12:00–13:00"), b.map { it.etiqueta })
        assertEquals(listOf(4_000L, 0L, 0L, 9_000L), b.map { it.totalCentavos })
        assertEquals(listOf(false, false, false, true), b.map { it.mejor })
        assertEquals(692L, b.last().porcentajeDecimas)
    }

    @Test fun `dias de lunes a domingo con domingo al final`() {
        val b = ReglaHorario.porDiaSemana(listOf(VentasPorGrupo(0, 3, 5_000), VentasPorGrupo(1, 1, 1_000)))
        assertEquals(7, b.size)
        assertEquals("Lunes", b.first().etiqueta)
        assertEquals("Domingo", b.last().etiqueta)
        assertTrue(b.last().mejor)
    }

    @Test fun `empate la mejor es la primera`() {
        val b = ReglaHorario.porHora(listOf(VentasPorGrupo(10, 1, 1_000), VentasPorGrupo(11, 1, 1_000)))
        assertEquals(listOf(true, false), b.map { it.mejor })
    }

    @Test fun `sin ventas no hay barras`() = assertTrue(ReglaHorario.porHora(emptyList()).isEmpty())

    // ── Mermas ──

    private fun salida(id: String, cant: Long, motivo: MotivoAjuste?, guardado: Long?, actual: Long = 5_000, tipo: TipoVenta = TipoVenta.UNIDAD) =
        SalidaAjuste(id, id.uppercase(), tipo, cant, motivo, guardado, actual)

    @Test fun `merma con precio guardado usa ese precio`() =
        assertEquals(8_000L, ReglaMermas.valor(salida("c", -2_000, MotivoAjuste.MERMA, guardado = 4_000)))

    @Test fun `merma anterior a la version usa el precio actual`() =
        assertEquals(10_000L, ReglaMermas.valor(salida("c", -2_000, MotivoAjuste.MERMA, guardado = null)))

    @Test fun `merma por peso`() =
        assertEquals(5_000L, ReglaMermas.valor(salida("q", -250, MotivoAjuste.MERMA, guardado = 20_000, tipo = TipoVenta.PESO)))

    @Test fun `la correccion de conteo y los aumentos no cuentan`() {
        assertFalse(ReglaMermas.cuenta(salida("c", -1_000, MotivoAjuste.CORRECCION_CONTEO, 4_000)))
        assertFalse(ReglaMermas.cuenta(salida("c", 1_000, MotivoAjuste.MERMA, 4_000)))
        assertTrue(ReglaMermas.cuenta(salida("c", -1_000, MotivoAjuste.CONSUMO_PROPIO, 4_000)))
    }

    @Test fun `por motivo y por producto con total`() {
        val s = listOf(
            salida("c", -2_000, MotivoAjuste.MERMA, 4_000),
            salida("c", -1_000, MotivoAjuste.PRODUCTO_CADUCADO, null),
            salida("q", -1_000, MotivoAjuste.MERMA, 2_000),
            salida("c", -5_000, MotivoAjuste.CORRECCION_CONTEO, 4_000),
        )
        assertEquals(15_000L, ReglaMermas.total(s))
        val m = ReglaMermas.porMotivo(s)
        assertEquals(listOf(MotivoAjuste.MERMA to 10_000L, MotivoAjuste.PRODUCTO_CADUCADO to 5_000L), m.map { it.motivo to it.valorCentavos })
        assertEquals(2, m.first().registros)
        assertEquals(667L, m.first().porcentajeDecimas)
        val p = ReglaMermas.porProducto(s)
        assertEquals(listOf("C" to 13_000L, "Q" to 2_000L), p.map { it.nombre to it.valorCentavos })
        assertEquals(3_000L, p.first().cantidadMilesimas)
        assertTrue(p.first().conPrecioActual)
        assertFalse(p.last().conPrecioActual)
    }

    // ── Fiado ──

    private object Extra : PuertoReportesExtra {
        private fun cliente(id: String, nombre: String) = Cliente(id, nombre, nombre.lowercase(), null, 0, 0)
        override suspend fun ventasPorHora(desde: Long, hasta: Long) = emptyList<VentasPorGrupo>()
        override suspend fun ventasPorDiaSemana(desde: Long, hasta: Long) = emptyList<VentasPorGrupo>()
        override suspend fun salidasPorAjuste(desde: Long, hasta: Long) = emptyList<SalidaAjuste>()
        override suspend fun fiadoEnPeriodo(desde: Long, hasta: Long) = ResumenVentas(2, 16_000, 0)
        override suspend fun abonosEnPeriodo(desde: Long, hasta: Long) =
            listOf(TotalFormaPago("ef", "Efectivo", true, 2, 6_000), TotalFormaPago("tr", "Transferencia", false, 1, 3_000))
        override suspend fun devolucionesEnPeriodo(desde: Long, hasta: Long) = 1_000L
        override suspend fun clientesConSaldo() = listOf(
            ClienteConSaldo(cliente("a", "Ana"), 5_000), ClienteConSaldo(cliente("b", "Beto"), 0), ClienteConSaldo(cliente("j", "Juan"), 20_000),
        )
        override suspend fun avisoSaldo() = 15_000L
    }

    @Test fun `reporte de fiado del periodo y lo que se debe hoy`() = runTest {
        val r = GenerarReporteFiado(Extra)(Periodo(TipoPeriodo.HOY, 0, 1))
        assertEquals(2, r.ventasFiadas)
        assertEquals(16_000L, r.fiadoCentavos)
        assertEquals(9_000L, r.abonosCentavos)
        assertEquals(listOf(667L, 333L), r.abonosPorForma.map { it.porcentajeDecimas })
        assertEquals(1_000L, r.devolucionesCentavos)
        assertEquals(25_000L, r.porCobrarCentavos)
        assertEquals(listOf("Juan", "Ana"), r.clientes.map { it.cliente.nombre }) // sin los de $0
        assertFalse(r.sinDatos)
    }
}
