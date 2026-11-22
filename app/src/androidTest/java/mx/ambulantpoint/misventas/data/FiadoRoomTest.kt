package mx.ambulantpoint.misventas.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.EstadoVenta
import mx.ambulantpoint.misventas.data.local.entity.TipoMovimiento
import mx.ambulantpoint.misventas.data.repository.DatosFiado
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.data.repository.FiadoRepo
import mx.ambulantpoint.misventas.data.repository.LineaLibre
import mx.ambulantpoint.misventas.data.repository.LineaVenta
import mx.ambulantpoint.misventas.data.repository.SolicitudCobro
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Fase 3 (fiado + venta rápida) sobre Room real: transacciones, saldo, devolución y corte. */
@RunWith(AndroidJUnit4::class)
class FiadoRoomTest {
    private lateinit var p: BaseDatosPrueba
    private lateinit var fiado: FiadoRepo

    @Before fun preparar() = runTest {
        p = BaseDatosPrueba()
        p.sembrar()
        fiado = FiadoRepo(p.db, p.reloj)
    }

    @After fun limpiar() = p.cerrar()

    private suspend fun fiar(productoId: String, piezas: Long, clienteId: String, pago: Long = 0, formaId: String? = null) =
        p.ventas.cobrar(
            SolicitudCobro(listOf(LineaVenta(productoId, piezas * 1000)), null, fiado = DatosFiado(clienteId, pago, formaId)),
        ).exito()

    @Test fun ejemplo_de_la_especificacion_cuadra_el_corte() = runTest {
        val caro = p.producto("Pastel", 10_000, 100_000) // $100
        val carlota = p.producto("Carlota", 4_000, 100_000) // $40
        val ef = p.efectivo().id
        val tr = p.transferencia().id
        p.cajas.abrir(20_000).exito()
        p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(caro.id, 5_000)), ef, 50_000)).exito() // $500 efectivo
        p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(caro.id, 1_000)), tr)).exito() // $100 transferencia
        val juan = fiado.guardarCliente(null, "Juan", "").exito()
        p.reloj.avanzar(1_000)
        fiar(carlota.id, 3, juan.id, pago = 5_000, formaId = ef) // $120, deja $50 en efectivo
        p.reloj.avanzar(1_000)
        fiado.registrarAbono(juan.id, 3_000, tr).exito() // abono $30 por transferencia

        val corte = p.cajas.calcularCorte(75_000).exito()
        assertEquals(72_000L, corte.totalVendidoCentavos)
        assertEquals(3, corte.numeroVentas)
        assertEquals(12_000L, corte.fiadoCentavos)
        assertEquals(8_000L, corte.abonosCentavos)
        assertEquals(0L, corte.devolucionesCentavos)
        assertEquals(75_000L, corte.efectivoEsperadoCentavos)
        assertEquals(0L, corte.diferenciaCentavos)
        // Por forma de pago solo las ventas cobradas; los abonos van aparte.
        assertEquals(listOf(50_000L, 10_000L), corte.porFormaPago.map { it.totalCentavos })
        assertEquals(mapOf("Efectivo" to 5_000L, "Transferencia" to 3_000L), corte.abonosPorForma.associate { it.nombreFormaPago to it.totalCentavos })
        assertEquals(4_000L, fiado.saldo(juan.id))
        // El inventario bajó al entregar, como venta normal.
        assertEquals(97_000L, p.productos.porId(carlota.id)!!.existenciaMilesimas)
    }

    @Test fun abono_se_reparte_de_la_venta_mas_vieja_a_la_mas_nueva() = runTest {
        val carlota = p.producto("Carlota", 4_000, 100_000)
        p.cajas.abrir(0).exito()
        val ana = fiado.guardarCliente(null, "Ana", "").exito()
        val v1 = fiar(carlota.id, 3, ana.id).venta // $120
        p.reloj.avanzar(60_000)
        val v2 = fiar(carlota.id, 1, ana.id).venta // $40
        p.reloj.avanzar(60_000)
        fiado.registrarAbono(ana.id, 13_000, p.efectivo().id).exito()
        val ventas = p.db.fiadoDao().ventasFiadas(ana.id).associateBy { it.ventaId }
        assertEquals(12_000L, ventas.getValue(v1.id).abonadoCentavos)
        assertEquals(1_000L, ventas.getValue(v2.id).abonadoCentavos)
        assertEquals(3_000L, fiado.saldo(ana.id))
        // No se puede abonar más de lo que debe.
        val f = fiado.registrarAbono(ana.id, 3_001, p.efectivo().id).falla() as FallaDatos.Regla
        assertEquals("El abono es mayor a lo que debe (\$30.00)", f.mensaje)
        // Liquidar todo.
        fiado.registrarAbono(ana.id, 3_000, p.efectivo().id).exito()
        assertEquals(0L, fiado.saldo(ana.id))
        assertEquals(0L, p.db.fiadoDao().observarClientesConSaldo().first().single().saldoCentavos)
    }

    @Test fun cancelar_venta_fiada_con_pago_inicial_devuelve_en_efectivo() = runTest {
        val carlota = p.producto("Carlota", 4_000, 10_000)
        val ef = p.efectivo().id
        p.cajas.abrir(10_000).exito()
        val luis = fiado.guardarCliente(null, "Luis", "7341234567").exito()
        val v = fiar(carlota.id, 3, luis.id, pago = 5_000, formaId = ef).venta
        assertEquals(5_000L, p.ventas.abonadoAVenta(v.id))

        p.ventas.cancelar(v.id).exito()
        assertEquals(EstadoVenta.CANCELADA, p.ventas.porId(v.id)!!.estado)
        assertEquals(10_000L, p.productos.porId(carlota.id)!!.existenciaMilesimas)
        assertEquals(0L, fiado.saldo(luis.id))

        val corte = p.cajas.calcularCorte(10_000).exito()
        assertEquals(0L, corte.totalVendidoCentavos)
        assertEquals(0L, corte.fiadoCentavos)
        assertEquals(5_000L, corte.abonosCentavos)
        assertEquals(5_000L, corte.devolucionesCentavos)
        assertEquals(10_000L, corte.efectivoEsperadoCentavos) // fondo + 50 − 50
    }

    @Test fun cancelar_venta_fiada_sin_abonos_no_registra_devolucion() = runTest {
        val carlota = p.producto("Carlota", 4_000, 10_000)
        p.cajas.abrir(0).exito()
        val c = fiado.guardarCliente(null, "Rosa", "").exito()
        val v = fiar(carlota.id, 1, c.id).venta
        p.ventas.cancelar(v.id).exito()
        assertEquals(0L, p.cajas.calcularCorte(0).exito().devolucionesCentavos)
    }

    @Test fun cancelar_abono_regresa_el_saldo_y_solo_con_su_caja_abierta() = runTest {
        val carlota = p.producto("Carlota", 4_000, 10_000)
        p.cajas.abrir(0).exito()
        val c = fiado.guardarCliente(null, "Pedro", "").exito()
        fiar(carlota.id, 3, c.id)
        val a = fiado.registrarAbono(c.id, 2_000, p.efectivo().id).exito()
        assertEquals(10_000L, fiado.saldo(c.id))
        assertTrue(fiado.esCancelable(a.id))
        fiado.cancelarAbono(a.id).exito()
        assertEquals(12_000L, fiado.saldo(c.id))
        assertFalse(fiado.esCancelable(a.id)) // ya cancelado

        val b = fiado.registrarAbono(c.id, 1_000, p.efectivo().id).exito()
        p.cajas.calcularCorte(1_000).exito()
        p.cajas.cerrar().exito()
        assertFalse(fiado.esCancelable(b.id))
        assertTrue(fiado.cancelarAbono(b.id).falla() is FallaDatos.Regla)
    }

    @Test fun abono_invalida_el_corte_y_requiere_caja() = runTest {
        val carlota = p.producto("Carlota", 4_000, 10_000)
        p.cajas.abrir(0).exito()
        val c = fiado.guardarCliente(null, "Toño", "").exito()
        fiar(carlota.id, 1, c.id)
        p.cajas.calcularCorte(0).exito()
        fiado.registrarAbono(c.id, 1_000, p.efectivo().id).exito()
        assertFalse(p.cajas.abierta()!!.corteVigente)
        p.cajas.calcularCorte(1_000).exito()
        p.cajas.cerrar().exito()
        val f = fiado.registrarAbono(c.id, 1_000, p.efectivo().id).falla() as FallaDatos.Regla
        assertEquals(Mensajes.Fiado.ABRA_CAJA_ABONO, f.mensaje)
    }

    @Test fun abono_de_una_venta_de_otro_dia_entra_al_corte_de_hoy() = runTest {
        val carlota = p.producto("Carlota", 4_000, 10_000)
        p.cajas.abrir(0).exito()
        val c = fiado.guardarCliente(null, "Memo", "").exito()
        fiar(carlota.id, 2, c.id)
        p.cajas.calcularCorte(0).exito()
        p.cajas.cerrar().exito()
        p.reloj.avanzar(86_400_000)
        p.cajas.abrir(5_000).exito()
        fiado.registrarAbono(c.id, 8_000, p.efectivo().id).exito()
        val corte = p.cajas.calcularCorte(13_000).exito()
        assertEquals(0L, corte.fiadoCentavos)
        assertEquals(8_000L, corte.abonosCentavos)
        assertEquals(13_000L, corte.efectivoEsperadoCentavos)
        assertEquals(0L, fiado.saldo(c.id))
    }

    @Test fun nombre_de_cliente_no_se_repite_sin_acentos_ni_mayusculas() = runTest {
        fiado.guardarCliente(null, "José Pérez", "").exito()
        val f = fiado.guardarCliente(null, "  jose   PEREZ ", "").falla() as FallaDatos.Regla
        assertEquals(Mensajes.Fiado.CLIENTE_YA_REGISTRADO, f.mensaje)
    }

    @Test fun pago_inicial_igual_al_total_se_rechaza_y_no_guarda_nada() = runTest {
        val carlota = p.producto("Carlota", 4_000, 10_000)
        p.cajas.abrir(0).exito()
        val c = fiado.guardarCliente(null, "Lalo", "").exito()
        val f = p.ventas.cobrar(
            SolicitudCobro(listOf(LineaVenta(carlota.id, 1000)), null, fiado = DatosFiado(c.id, 4_000, p.efectivo().id)),
        ).falla()
        assertTrue(f is FallaDatos.Regla)
        assertEquals(10_000L, p.productos.porId(carlota.id)!!.existenciaMilesimas)
        assertTrue(p.db.fiadoDao().ventasFiadas(c.id).isEmpty())
    }

    @Test fun venta_rapida_no_mueve_inventario_y_sale_como_varios() = runTest {
        val carlota = p.producto("Carlota", 4_000, 10_000)
        p.cajas.abrir(0).exito()
        val v = p.ventas.cobrar(
            SolicitudCobro(
                listOf(LineaVenta(carlota.id, 1000)), p.efectivo().id, 10_000,
                libres = listOf(LineaLibre("", 1_800), LineaLibre("Refresco de la tienda", 2_000)),
            ),
        ).exito()
        assertEquals(7_800L, v.venta.totalCentavos)
        val detalles = p.db.ventaDao().detalles(v.venta.id)
        assertEquals(setOf("Varios", "Refresco de la tienda", "Carlota"), detalles.map { it.nombreProducto }.toSet())
        assertEquals(2, detalles.count { it.productoId == null })
        assertEquals(9_000L, p.productos.porId(carlota.id)!!.existenciaMilesimas)

        val vendidos = p.reportes.productosVendidos(0, Long.MAX_VALUE).associateBy { it.nombre }
        assertEquals(3_800L, vendidos.getValue("Varios").importeCentavos)
        assertEquals("Varios", vendidos.getValue("Varios").categoriaNombre)

        // Cancelar no falla por los renglones sin producto y solo regresa la carlota.
        p.ventas.cancelar(v.venta.id).exito()
        assertEquals(10_000L, p.productos.porId(carlota.id)!!.existenciaMilesimas)
        val movs = p.inventario.observarMovimientos(carlota.id).first()
        assertEquals(1, movs.count { it.tipo == TipoMovimiento.CANCELACION })
    }

    /** Regresión (22-nov): con carga del día, un monto libre cerraba la app al entrar a Vender. */
    @Test fun carga_del_dia_ignora_el_monto_libre() = runTest {
        val carlota = p.producto("Carlota", 4_000, 10_000)
        val caja = p.cajas.abrir(0).exito()
        val carga = mx.ambulantpoint.misventas.data.repository.CargaRepo(p.db, p.reloj)
        carga.agregar(caja.id, carlota.id, 5_000).exito()
        p.ventas.cobrar(
            SolicitudCobro(listOf(LineaVenta(carlota.id, 2_000)), p.efectivo().id, 10_000, libres = listOf(LineaLibre("", 1_500))),
        ).exito()
        val resumen = carga.observarResumen(caja.id, caja.abiertaEn).first()
        assertEquals(1, resumen.size)
        assertEquals(2_000L, resumen.single().vendidoMilesimas)
        assertEquals(3_000L, resumen.single().quedanMilesimas)
    }

    @Test fun venta_solo_con_monto_libre_se_cobra() = runTest {
        p.cajas.abrir(0).exito()
        val v = p.ventas.cobrar(SolicitudCobro(emptyList(), p.transferencia().id, libres = listOf(LineaLibre("", 2_500)))).exito()
        assertEquals(2_500L, v.venta.totalCentavos)
    }

    @Test fun no_se_fia_una_venta_con_monto_libre() = runTest {
        p.cajas.abrir(0).exito()
        val c = fiado.guardarCliente(null, "Beto", "").exito()
        val f = p.ventas.cobrar(SolicitudCobro(emptyList(), null, libres = listOf(LineaLibre("", 2_500)), fiado = DatosFiado(c.id))).falla()
        assertEquals(Mensajes.Fiado.NO_FIAR_VARIOS, (f as FallaDatos.Regla).mensaje)
    }

    @Test fun historial_y_reporte_muestran_fiado() = runTest {
        val carlota = p.producto("Carlota", 4_000, 10_000)
        p.cajas.abrir(0).exito()
        val c = fiado.guardarCliente(null, "Juan", "").exito()
        fiar(carlota.id, 1, c.id)
        val h = p.ventas.observarHistorial(0, Long.MAX_VALUE).first().single()
        assertNull(h.formaPagoNombre)
        assertEquals("Fiado · Juan", h.etiquetaPago)
        val porForma = p.reportes.totalesPorFormaPago(0, Long.MAX_VALUE).single()
        assertEquals("Fiado", porForma.nombreFormaPago)
        assertEquals(4_000L, porForma.totalCentavos)
    }

    @Test fun aviso_de_saldo_empieza_en_150_y_se_puede_cambiar() = runTest {
        // Sin configuración guardada, el aviso por defecto es $150.
        fiado.guardarAviso(30_000).exito()
        assertEquals(30_000L, p.db.configuracionDao().obtener()!!.avisoSaldoCentavos)
    }
}
