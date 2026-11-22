package mx.ambulantpoint.misventas.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.MovimientoInventario
import mx.ambulantpoint.misventas.data.local.entity.TipoMovimiento
import mx.ambulantpoint.misventas.data.repository.DatosFiado
import mx.ambulantpoint.misventas.data.repository.FiadoRepo
import mx.ambulantpoint.misventas.data.repository.LineaVenta
import mx.ambulantpoint.misventas.data.repository.SolicitudCobro
import mx.ambulantpoint.misventas.domain.regla.ReglaPeriodos
import mx.ambulantpoint.misventas.domain.regla.TipoPeriodo
import mx.ambulantpoint.misventas.domain.usecase.GenerarComparacion
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteFiado
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteHorario
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteMermas
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Fase 5 (reportes nuevos) sobre Room real: consultas en hora local del teléfono. */
@RunWith(AndroidJUnit4::class)
class ReportesNuevosRoomTest {
    private lateinit var p: BaseDatosPrueba
    private val zona = ZoneId.systemDefault()
    private val lunes = LocalDate.of(2026, 11, 23) // lunes

    private fun en(dias: Long, hora: Int, minuto: Int = 0) =
        lunes.plusDays(dias).atTime(hora, minuto).atZone(zona).toInstant().toEpochMilli()

    @Before fun preparar() = runTest {
        p = BaseDatosPrueba()
        p.sembrar()
        p.reloj.ms = en(0, 8)
    }

    @After fun limpiar() = p.cerrar()

    private suspend fun vender(productoId: String, piezas: Long, cuando: Long) {
        p.reloj.ms = cuando
        p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(productoId, piezas * 1000)), p.transferencia().id)).exito()
    }

    @Test fun ventas_por_hora_y_dia_en_hora_local() = runTest {
        val c = p.producto("Carlota", 4_000, 100_000)
        p.cajas.abrir(0).exito()
        vender(c.id, 1, en(0, 9, 15))
        vender(c.id, 2, en(0, 9, 50))
        vender(c.id, 1, en(0, 13, 5))
        vender(c.id, 5, en(1, 13, 30)) // martes
        val semana = ReglaPeriodos.calcular(TipoPeriodo.SEMANA, en(1, 20))
        val r = GenerarReporteHorario(p.reportes)(semana)
        assertEquals(listOf("09:00–10:00", "10:00–11:00", "11:00–12:00", "12:00–13:00", "13:00–14:00"), r.porHora.map { it.etiqueta })
        assertEquals(12_000L, r.porHora.first().totalCentavos)
        assertEquals(2, r.porHora.first().numeroVentas)
        assertTrue(r.porHora.last().mejor) // 13 h: $40 + $200
        val dias = r.porDia.associateBy { it.etiqueta }
        assertEquals(16_000L, dias.getValue("Lunes").totalCentavos)
        assertEquals(20_000L, dias.getValue("Martes").totalCentavos)
        assertTrue(dias.getValue("Martes").mejor)
    }

    @Test fun el_ajuste_guarda_el_precio_y_la_merma_se_valora_con_el() = runTest {
        val c = p.producto("Carlota", 4_000, 10_000)
        p.inventario.ajustar(c.id, false, 2_000, MotivoAjuste.MERMA).exito()
        // Sube el precio después: la merma ya registrada se queda con $40.
        p.db.productoDao().actualizar(p.productos.porId(c.id)!!.copy(precioCentavos = 5_000))
        p.inventario.ajustar(c.id, false, 1_000, MotivoAjuste.CORRECCION_CONTEO).exito() // no cuenta
        p.inventario.ajustar(c.id, true, 1_000, MotivoAjuste.MERMA).exito() // aumento: no cuenta
        // Una merma "vieja" (antes de la 1.6.0) sin precio guardado: se valora con el actual ($50).
        p.db.movimientoDao().insertar(
            MovimientoInventario(nuevoId(), c.id, p.reloj.ahora(), TipoMovimiento.AJUSTE, -1_000, 6_000, MotivoAjuste.PRODUCTO_CADUCADO, creadoEn = p.reloj.ahora()),
        )
        val r = GenerarReporteMermas(p.reportes)(ReglaPeriodos.calcular(TipoPeriodo.HOY, p.reloj.ahora()))
        assertEquals(8_000L + 5_000L, r.totalCentavos)
        assertEquals(listOf(MotivoAjuste.MERMA, MotivoAjuste.PRODUCTO_CADUCADO), r.porMotivo.map { it.motivo })
        assertEquals(3_000L, r.porProducto.single().cantidadMilesimas)
        assertTrue(r.algunaConPrecioActual)
    }

    @Test fun reporte_de_fiado_del_periodo_y_por_cobrar() = runTest {
        val c = p.producto("Carlota", 4_000, 100_000)
        val fiado = FiadoRepo(p.db, p.reloj)
        p.cajas.abrir(0).exito()
        val juan = fiado.guardarCliente(null, "Juan", "").exito()
        p.reloj.ms = en(0, 10)
        p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(c.id, 5_000)), null, fiado = DatosFiado(juan.id, 5_000, p.efectivo().id))).exito()
        p.reloj.ms = en(0, 11)
        fiado.registrarAbono(juan.id, 3_000, p.transferencia().id).exito()
        val r = GenerarReporteFiado(p.reportes)(ReglaPeriodos.calcular(TipoPeriodo.HOY, p.reloj.ahora()))
        assertEquals(1, r.ventasFiadas)
        assertEquals(20_000L, r.fiadoCentavos)
        assertEquals(8_000L, r.abonosCentavos)
        assertEquals(12_000L, r.porCobrarCentavos)
        assertEquals("Juan", r.clientes.single().cliente.nombre)
        // Al día siguiente, el periodo "hoy" no tiene movimientos, pero el saldo de hoy sigue.
        val manana = GenerarReporteFiado(p.reportes)(ReglaPeriodos.calcular(TipoPeriodo.HOY, en(1, 9)))
        assertEquals(0, manana.ventasFiadas)
        assertEquals(12_000L, manana.porCobrarCentavos)
        assertFalse(manana.sinDatos)
    }

    @Test fun comparacion_con_el_mismo_tramo_de_ayer() = runTest {
        val c = p.producto("Carlota", 4_000, 100_000)
        val q = p.producto("Queso", 10_000, 100_000)
        p.cajas.abrir(0).exito()
        vender(c.id, 1, en(0, 10)) // lunes 10:00
        vender(q.id, 1, en(0, 12))
        vender(c.id, 5, en(0, 18)) // lunes 18:00: después de la hora de corte, no cuenta
        vender(c.id, 3, en(1, 9)) // martes
        val ahora = en(1, 15)
        val r = GenerarComparacion(p.reportes) { ahora }(ReglaPeriodos.calcular(TipoPeriodo.HOY, ahora))
        assertEquals(12_000L, r.total.actual)
        assertEquals(14_000L, r.total.anterior) // solo hasta las 15:00 de ayer
        assertEquals(-143L, r.total.porcentajeDecimas)
        assertEquals(listOf("Carlota"), r.subieron.map { it.nombre })
        assertEquals(listOf("Queso"), r.bajaron.map { it.nombre })
    }
}
