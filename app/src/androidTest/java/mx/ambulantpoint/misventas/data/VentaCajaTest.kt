package mx.ambulantpoint.misventas.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.data.local.entity.EstadoCaja
import mx.ambulantpoint.misventas.data.local.entity.EstadoVenta
import mx.ambulantpoint.misventas.data.local.entity.TipoDescuento
import mx.ambulantpoint.misventas.data.local.entity.TipoMovimiento
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.repository.DescuentoVenta
import mx.ambulantpoint.misventas.data.repository.FallaDatos
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

/** Transacciones de venta y caja (RF 3.1–3.3, 5.1–5.3) sobre Room real en el teléfono. */
@RunWith(AndroidJUnit4::class)
class VentaCajaTest {
    private lateinit var p: BaseDatosPrueba

    @Before fun preparar() = runTest {
        p = BaseDatosPrueba()
        p.sembrar()
    }

    @After fun limpiar() = p.cerrar()

    @Test fun semilla_crea_efectivo_y_transferencia_una_sola_vez() = runTest {
        p.sembrar()
        val formas = p.formas.observarTodas().first()
        assertEquals(listOf("Efectivo", "Transferencia"), formas.map { it.nombre })
        assertTrue(formas.first().esEfectivo)
    }

    @Test fun sin_caja_abierta_no_se_vende() = runTest {
        val refresco = p.producto("Refresco", 2000, 10_000)
        val falla = p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 1000)), p.efectivo().id, 2000)).falla()
        assertEquals(FallaDatos.SinCajaAbierta, falla)
    }

    @Test fun solo_una_caja_abierta() = runTest {
        p.cajas.abrir(50_000).exito()
        assertEquals(FallaDatos.CajaYaAbierta, p.cajas.abrir(0).falla())
    }

    @Test fun cobrar_en_efectivo_calcula_cambio_descuenta_y_deja_movimiento() = runTest {
        val refresco = p.producto("Refresco", 2000, 10_000)
        val cacahuate = p.producto("Cacahuate", 8600, 2_000, TipoVenta.PESO)
        p.cajas.abrir(10_000).exito()

        // 2 refrescos ($40.00) + 0.250 kg de cacahuate ($21.50) = $61.50; paga con $100
        val cobrada = p.ventas.cobrar(
            SolicitudCobro(
                lineas = listOf(LineaVenta(refresco.id, 2000), LineaVenta(cacahuate.id, 250)),
                formaPagoId = p.efectivo().id,
                montoRecibidoCentavos = 10_000,
            ),
        ).exito()

        assertEquals(6150L, cobrada.venta.subtotalCentavos)
        assertEquals(6150L, cobrada.venta.totalCentavos)
        assertEquals(3850L, cobrada.venta.cambioCentavos)
        assertEquals(8_000L, p.productos.porId(refresco.id)!!.existenciaMilesimas)
        assertEquals(1_750L, p.productos.porId(cacahuate.id)!!.existenciaMilesimas)
        assertEquals(8_000L, cobrada.existencias[refresco.id])

        val movs = p.inventario.observarMovimientos(refresco.id).first()
        val venta = movs.first { it.tipo == TipoMovimiento.VENTA }
        assertEquals(-2000L, venta.cantidadMilesimas)
        assertEquals(8_000L, venta.existenciaResultanteMilesimas)
    }

    @Test fun venta_que_falla_no_deja_nada_a_medias() = runTest {
        val refresco = p.producto("Refresco", 2000, 10_000)
        val chicle = p.producto("Chicle", 100, 1_000)
        p.cajas.abrir(0).exito()

        // El refresco sí alcanza, el chicle no: no debe descontarse ninguno.
        val falla = p.ventas.cobrar(
            SolicitudCobro(
                listOf(LineaVenta(refresco.id, 1000), LineaVenta(chicle.id, 3000)),
                p.efectivo().id, 100_000,
            ),
        ).falla()

        assertTrue(falla is FallaDatos.ExistenciaInsuficiente)
        falla as FallaDatos.ExistenciaInsuficiente
        assertEquals("Chicle", falla.nombreProducto)
        assertEquals(1_000L, falla.disponibleMilesimas)
        assertEquals(10_000L, p.productos.porId(refresco.id)!!.existenciaMilesimas)
        assertEquals(0, p.ventas.observarHistorial(0, Long.MAX_VALUE).first().size)
    }

    @Test fun monto_recibido_menor_al_total_se_rechaza() = runTest {
        val refresco = p.producto("Refresco", 2000, 10_000)
        p.cajas.abrir(0).exito()
        val falla = p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 1000)), p.efectivo().id, 1999)).falla()
        assertEquals(FallaDatos.MontoRecibidoInsuficiente, falla)
    }

    @Test fun transferencia_no_guarda_monto_ni_cambio() = runTest {
        val refresco = p.producto("Refresco", 2000, 10_000)
        p.cajas.abrir(0).exito()
        val v = p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 1000)), p.transferencia().id, 5000)).exito().venta
        assertNull(v.montoRecibidoCentavos)
        assertNull(v.cambioCentavos)
    }

    @Test fun por_unidad_no_acepta_fracciones() = runTest {
        val refresco = p.producto("Refresco", 2000, 10_000)
        p.cajas.abrir(0).exito()
        val falla = p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 1500)), p.transferencia().id)).falla()
        assertEquals(FallaDatos.CantidadInvalida, falla)
    }

    @Test fun producto_agotado_sale_de_la_pantalla_de_venta() = runTest {
        val refresco = p.producto("Refresco", 2000, 1_000)
        p.cajas.abrir(0).exito()
        assertEquals(1, p.productos.observarParaVenta().first().size)
        p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 1000)), p.transferencia().id)).exito()
        assertEquals(0, p.productos.observarParaVenta().first().size)
    }

    @Test fun descuento_se_guarda_y_resta_del_total() = runTest {
        val refresco = p.producto("Refresco", 2000, 10_000)
        p.cajas.abrir(0).exito()
        val v = p.ventas.cobrar(
            SolicitudCobro(
                listOf(LineaVenta(refresco.id, 5000)), p.transferencia().id,
                descuento = DescuentoVenta(TipoDescuento.PORCENTAJE, 1000, 1000),
            ),
        ).exito().venta
        assertEquals(10_000L, v.subtotalCentavos)
        assertEquals(1_000L, v.descuentoCentavos)
        assertEquals(9_000L, v.totalCentavos)
    }

    @Test fun cancelar_regresa_existencia_y_no_cuenta_en_el_corte() = runTest {
        val refresco = p.producto("Refresco", 2000, 10_000)
        p.cajas.abrir(5_000).exito()
        val v1 = p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 2000)), p.efectivo().id, 4000)).exito().venta
        p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 1000)), p.transferencia().id)).exito()

        val cancelada = p.ventas.cancelar(v1.id).exito()
        assertEquals(EstadoVenta.CANCELADA, cancelada.estado)
        assertEquals(9_000L, p.productos.porId(refresco.id)!!.existenciaMilesimas)
        assertEquals(FallaDatos.VentaNoCancelable, p.ventas.cancelar(v1.id).falla())

        val corte = p.cajas.calcularCorte(5_000).exito()
        assertEquals(1, corte.numeroVentas)
        assertEquals(2_000L, corte.totalVendidoCentavos)
        // Fondo $50 + efectivo $0 (la venta en efectivo se canceló)
        assertEquals(5_000L, corte.efectivoEsperadoCentavos)
        assertEquals(0L, corte.diferenciaCentavos)
    }

    @Test fun corte_calcula_esperado_y_diferencia_por_forma_de_pago() = runTest {
        val refresco = p.producto("Refresco", 2000, 10_000)
        p.cajas.abrir(10_000).exito()
        p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 3000)), p.efectivo().id, 10_000)).exito()
        p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 1000)), p.transferencia().id)).exito()

        // Esperado = $100 fondo + $60 efectivo = $160; contó $150 → faltante de $10
        val corte = p.cajas.calcularCorte(15_000).exito()
        assertEquals(8_000L, corte.totalVendidoCentavos)
        assertEquals(2, corte.numeroVentas)
        assertEquals(16_000L, corte.efectivoEsperadoCentavos)
        assertEquals(-1_000L, corte.diferenciaCentavos)
        assertEquals(
            mapOf("Efectivo" to 6_000L, "Transferencia" to 2_000L),
            corte.porFormaPago.associate { it.nombreFormaPago to it.totalCentavos },
        )
        // El corte no cierra la caja
        assertEquals(EstadoCaja.ABIERTA, p.cajas.abierta()!!.estado)
    }

    @Test fun cerrar_requiere_corte_vigente_y_bloquea_cancelar() = runTest {
        val refresco = p.producto("Refresco", 2000, 10_000)
        p.cajas.abrir(0).exito()
        assertEquals(FallaDatos.CorteNoVigente, p.cajas.cerrar().falla())

        p.cajas.calcularCorte(0).exito()
        val v = p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 1000)), p.transferencia().id)).exito().venta
        // Vendió después del corte: hay que recalcularlo
        assertEquals(FallaDatos.CorteNoVigente, p.cajas.cerrar().falla())

        p.cajas.calcularCorte(0).exito()
        val cerrada = p.cajas.cerrar().exito()
        assertEquals(EstadoCaja.CERRADA, cerrada.estado)
        assertFalse(p.ventas.esCancelable(v.id))
        assertEquals(FallaDatos.VentaNoCancelable, p.ventas.cancelar(v.id).falla())
        assertEquals(FallaDatos.SinCajaAbierta, p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 1000)), p.transferencia().id)).falla())
        assertEquals(1, p.cajas.observarCortes().first().size)
    }
}
