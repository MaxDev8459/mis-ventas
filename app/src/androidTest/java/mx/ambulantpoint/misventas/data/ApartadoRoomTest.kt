package mx.ambulantpoint.misventas.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.data.local.entity.EstadoApartado
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.repository.ApartadoRepo
import mx.ambulantpoint.misventas.data.repository.DatosApartado
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.data.repository.LineaVenta
import mx.ambulantpoint.misventas.data.repository.SolicitudCobro
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Fase 4 (apartados) sobre Room real: existencia separada, entrega, ajustes y liberación al cerrar caja. */
@RunWith(AndroidJUnit4::class)
class ApartadoRoomTest {
    private lateinit var p: BaseDatosPrueba
    private lateinit var apartados: ApartadoRepo
    private val zona = ZoneId.systemDefault()
    private val dia = LocalDate.of(2026, 11, 24)

    /** Epoch ms del [dia] + [dias] a la [hora] (hora local del teléfono, igual que la app). */
    private fun en(dias: Long, hora: Int, minuto: Int = 0) =
        dia.plusDays(dias).atTime(hora, minuto).atZone(zona).toInstant().toEpochMilli()

    @Before fun preparar() = runTest {
        p = BaseDatosPrueba()
        p.sembrar()
        p.reloj.ms = en(-3, 9)
        apartados = ApartadoRepo(p.db, p.reloj) { zona }
    }

    @After fun limpiar() = p.cerrar()

    private suspend fun apartar(productoId: String, piezas: Long, entregaEn: Long, nombre: String = "Juan") =
        apartados.apartar(DatosApartado(nombre, "", "Parque central", entregaEn), listOf(LineaVenta(productoId, piezas * 1000)))

    private suspend fun pendiente(id: String) = p.db.apartadoDao().porId(id)!!.estado

    @Test fun apartar_separa_sin_mover_la_existencia_y_no_se_vende_lo_apartado() = runTest {
        val carlota = p.producto("Carlota", 4_000, 5_000)
        apartar(carlota.id, 3, en(1, 11)).exito()
        assertEquals(5_000L, p.productos.porId(carlota.id)!!.existenciaMilesimas)

        p.cajas.abrir(0).exito()
        val f = p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(carlota.id, 3_000)), p.efectivo().id, 20_000)).falla()
        assertEquals(2_000L, (f as FallaDatos.ExistenciaInsuficiente).disponibleMilesimas)
        p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(carlota.id, 2_000)), p.efectivo().id, 20_000)).exito()
        assertEquals(3_000L, p.productos.porId(carlota.id)!!.existenciaMilesimas)
    }

    @Test fun no_se_aparta_mas_de_lo_disponible() = runTest {
        val carlota = p.producto("Carlota", 4_000, 5_000)
        apartar(carlota.id, 3, en(1, 11)).exito()
        assertTrue(apartar(carlota.id, 3, en(1, 12), "Ana").falla() is FallaDatos.ExistenciaInsuficiente)
        apartar(carlota.id, 2, en(1, 12), "Ana").exito()
    }

    @Test fun no_se_aparta_en_el_pasado() = runTest {
        val carlota = p.producto("Carlota", 4_000, 5_000)
        assertTrue(apartar(carlota.id, 1, en(-4, 11)).falla() is FallaDatos.Regla)
    }

    @Test fun entregar_cobra_el_precio_fijo_y_entra_al_corte() = runTest {
        val carlota = p.producto("Carlota", 4_000, 5_000)
        val a = apartar(carlota.id, 3, en(1, 11)).exito()
        // Sube el precio después de apartar: se cobra el de cuando se apartó.
        p.db.productoDao().actualizar(p.productos.porId(carlota.id)!!.copy(precioCentavos = 5_000))
        p.reloj.ms = en(1, 10)
        p.cajas.abrir(10_000).exito()
        val v = p.ventas.cobrar(SolicitudCobro(emptyList(), p.efectivo().id, 20_000, apartadoId = a.id)).exito()
        assertEquals(12_000L, v.venta.totalCentavos)
        assertEquals(8_000L, v.venta.cambioCentavos)
        val ap = p.db.apartadoDao().porId(a.id)!!
        assertEquals(EstadoApartado.ENTREGADO, ap.estado)
        assertEquals(v.venta.id, ap.ventaId)
        assertEquals(2_000L, p.productos.porId(carlota.id)!!.existenciaMilesimas)
        val corte = p.cajas.calcularCorte(22_000).exito()
        assertEquals(12_000L, corte.totalVendidoCentavos)
        assertEquals(22_000L, corte.efectivoEsperadoCentavos)
        // No se entrega dos veces.
        assertTrue(p.ventas.cobrar(SolicitudCobro(emptyList(), p.efectivo().id, 20_000, apartadoId = a.id)).falla() is FallaDatos.Regla)
    }

    @Test fun entregar_requiere_caja_abierta() = runTest {
        val carlota = p.producto("Carlota", 4_000, 5_000)
        val a = apartar(carlota.id, 1, en(1, 11)).exito()
        assertEquals(FallaDatos.SinCajaAbierta, p.ventas.cobrar(SolicitudCobro(emptyList(), p.efectivo().id, 5_000, apartadoId = a.id)).falla())
        assertEquals(EstadoApartado.PENDIENTE, pendiente(a.id))
    }

    @Test fun entregar_aunque_el_producto_se_desactive() = runTest {
        val carlota = p.producto("Carlota", 4_000, 5_000)
        val a = apartar(carlota.id, 1, en(1, 11)).exito()
        p.productos.cambiarEstado(carlota.id, activo = false).exito()
        p.cajas.abrir(0).exito()
        p.ventas.cobrar(SolicitudCobro(emptyList(), p.efectivo().id, 5_000, apartadoId = a.id)).exito()
    }

    @Test fun ajuste_o_merma_no_baja_de_lo_apartado() = runTest {
        val carlota = p.producto("Carlota", 4_000, 5_000)
        apartar(carlota.id, 3, en(1, 11)).exito()
        assertTrue(p.inventario.ajustar(carlota.id, false, 3_000, MotivoAjuste.MERMA).falla() is FallaDatos.Regla)
        assertEquals(5_000L, p.productos.porId(carlota.id)!!.existenciaMilesimas)
        p.inventario.ajustar(carlota.id, false, 2_000, MotivoAjuste.MERMA).exito()
        assertEquals(3_000L, p.productos.porId(carlota.id)!!.existenciaMilesimas)
    }

    @Test fun cerrar_caja_libera_los_de_ese_dia_y_anteriores_pero_no_los_futuros() = runTest {
        val carlota = p.producto("Carlota", 4_000, 10_000)
        val ayer = apartar(carlota.id, 1, en(-1, 11), "Ayer").exito()
        val hoy = apartar(carlota.id, 1, en(0, 18), "Hoy").exito()
        val nocheHoy = apartar(carlota.id, 1, en(0, 23, 59), "Noche").exito()
        val manana = apartar(carlota.id, 1, en(1, 9), "Mañana").exito()

        p.reloj.ms = en(0, 9)
        p.cajas.abrir(0).exito()
        assertEquals(setOf("Ayer", "Hoy", "Noche"), apartados.porLiberar().map { it.nombre }.toSet())
        p.cajas.calcularCorte(0).exito()
        p.reloj.ms = en(1, 0, 30) // se cierra pasada la medianoche: el día sigue siendo el de la apertura
        p.cajas.cerrar().exito()

        assertEquals(EstadoApartado.LIBERADO, pendiente(ayer.id))
        assertEquals(EstadoApartado.LIBERADO, pendiente(hoy.id))
        assertEquals(EstadoApartado.LIBERADO, pendiente(nocheHoy.id))
        assertEquals(EstadoApartado.PENDIENTE, pendiente(manana.id))
        assertEquals(mapOf(carlota.id to 1_000L), apartados.observarApartadoPorProducto().first())
    }

    @Test fun cancelar_libera_el_producto() = runTest {
        val carlota = p.producto("Carlota", 4_000, 5_000)
        val a = apartar(carlota.id, 5, en(1, 11)).exito()
        assertEquals(mapOf(carlota.id to 5_000L), apartados.observarApartadoPorProducto().first())
        apartados.cancelar(a.id).exito()
        assertEquals(EstadoApartado.CANCELADO, pendiente(a.id))
        assertTrue(apartados.observarApartadoPorProducto().first().isEmpty())
        assertTrue(apartados.cancelar(a.id).falla() is FallaDatos.Regla)
    }

    @Test fun editar_cambia_los_datos_de_entrega_solo_si_esta_pendiente() = runTest {
        val carlota = p.producto("Carlota", 4_000, 5_000)
        val a = apartar(carlota.id, 1, en(1, 11)).exito()
        val e = apartados.editar(a.id, DatosApartado(" Juan  Pérez ", "7341234567", "Kiosco", en(2, 13))).exito()
        assertEquals("Juan Pérez", e.nombre)
        assertEquals("7341234567", e.telefono)
        assertEquals(en(2, 13), e.entregaEn)
        apartados.cancelar(a.id).exito()
        assertTrue(apartados.editar(a.id, DatosApartado("Juan", "", "Kiosco", en(2, 13))).falla() is FallaDatos.Regla)
    }

    @Test fun precio_fijo_y_total_se_guardan_al_apartar() = runTest {
        val carlota = p.producto("Carlota", 4_000, 5_000)
        val queso = p.producto("Queso", 20_000, 3_000, mx.ambulantpoint.misventas.data.local.entity.TipoVenta.PESO)
        val a = apartados.apartar(
            DatosApartado("Juan", "", "Parque", en(1, 11)),
            listOf(LineaVenta(carlota.id, 2_000), LineaVenta(queso.id, 500)),
        ).exito()
        assertEquals(8_000L + 10_000L, a.totalCentavos)
        assertEquals(2, p.db.apartadoDao().detalles(a.id).size)
    }
}
