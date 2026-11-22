package mx.ambulantpoint.misventas.domain

import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.dao.VentasRecientes
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.local.entity.Venta
import mx.ambulantpoint.misventas.data.repository.SolicitudCobro
import mx.ambulantpoint.misventas.data.repository.VentaCobrada
import mx.ambulantpoint.misventas.domain.puertos.PuertoVentas
import mx.ambulantpoint.misventas.domain.puertos.PuertoVentasRecientes
import mx.ambulantpoint.misventas.domain.regla.Aplicacion
import mx.ambulantpoint.misventas.domain.regla.ConceptosCorte
import mx.ambulantpoint.misventas.domain.regla.FallaCampos
import mx.ambulantpoint.misventas.domain.regla.FallaMensaje
import mx.ambulantpoint.misventas.domain.regla.ReglaCorte
import mx.ambulantpoint.misventas.domain.regla.ReglaFiado
import mx.ambulantpoint.misventas.domain.regla.SaldoVenta
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.CancelarVenta
import mx.ambulantpoint.misventas.domain.usecase.CobrarVenta
import mx.ambulantpoint.misventas.domain.usecase.EvaluarExistenciaBaja
import mx.ambulantpoint.misventas.domain.usecase.FiadoCapturado
import mx.ambulantpoint.misventas.domain.usecase.PrepararCobro
import mx.ambulantpoint.misventas.domain.venta.Carrito
import mx.ambulantpoint.misventas.domain.venta.LineaACorregir
import mx.ambulantpoint.misventas.domain.venta.ProductoEnVenta
import mx.ambulantpoint.misventas.domain.venta.ReglaCorreccion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fase 3: fiado, abonos, corte con fiado y venta rápida sin producto (docs/09-ESPEC-FIADO.md). */
class FiadoTest {
    private val efectivo = FormaPago("ef", "Efectivo", "efectivo", esEfectivo = true, creadoEn = 0, actualizadoEn = 0)
    private val transferencia = FormaPago("tr", "Transferencia", "transferencia", creadoEn = 0, actualizadoEn = 0)
    private val carlota = ProductoEnVenta("c", "Carlota", TipoVenta.UNIDAD, 4_000, 20_000)

    private fun Resultado<*>.campos() = ((this as Resultado.Fallo).falla as FallaCampos).errores
    private fun Resultado<*>.mensaje() = ((this as Resultado.Fallo).falla as FallaMensaje).mensaje
    private fun <T> Resultado<T>.valor() = (this as Resultado.Exito).valor

    // ── Corte de caja (cálculo crítico) ──

    @Test fun `efectivo esperado sin fiado es igual al de antes`() {
        val c = ConceptosCorte(fondoCentavos = 20_000, ventasEfectivoCentavos = 50_000)
        assertEquals(ReglaCorte.efectivoEsperado(20_000, 50_000), ReglaFiado.efectivoEsperado(c))
        assertEquals(70_000L, ReglaFiado.efectivoEsperado(c))
    }

    @Test fun `ejemplo de la especificacion - fiado con pago inicial en efectivo y abono por transferencia`() {
        // Fondo $200, ventas en efectivo $500, transferencia $100 (no cuenta), fiado $120 (no cuenta),
        // pago inicial $50 en efectivo (cuenta), abono $30 por transferencia (no cuenta) → $750.
        val c = ConceptosCorte(fondoCentavos = 20_000, ventasEfectivoCentavos = 50_000, abonosEfectivoCentavos = 5_000)
        assertEquals(75_000L, ReglaFiado.efectivoEsperado(c))
    }

    @Test fun `fiado sin pago inicial no cambia el efectivo esperado`() {
        val c = ConceptosCorte(fondoCentavos = 10_000, ventasEfectivoCentavos = 0, abonosEfectivoCentavos = 0)
        assertEquals(10_000L, ReglaFiado.efectivoEsperado(c))
    }

    @Test fun `abono en efectivo de una venta de otro dia suma al efectivo de hoy`() {
        val c = ConceptosCorte(fondoCentavos = 10_000, ventasEfectivoCentavos = 30_000, abonosEfectivoCentavos = 8_000)
        assertEquals(48_000L, ReglaFiado.efectivoEsperado(c))
    }

    @Test fun `devolucion resta al efectivo esperado`() {
        // Venta fiada de $120 con pago inicial de $50 en efectivo, cancelada el mismo día: se devuelven $50.
        val c = ConceptosCorte(fondoCentavos = 10_000, ventasEfectivoCentavos = 0, abonosEfectivoCentavos = 5_000, devolucionesCentavos = 5_000)
        assertEquals(10_000L, ReglaFiado.efectivoEsperado(c))
    }

    @Test fun `devolucion con abono por transferencia deja el efectivo por debajo del fondo`() {
        // El abono entró por transferencia pero la devolución siempre es en efectivo (respuesta 4b).
        val c = ConceptosCorte(fondoCentavos = 10_000, ventasEfectivoCentavos = 0, abonosEfectivoCentavos = 0, devolucionesCentavos = 3_000)
        assertEquals(7_000L, ReglaFiado.efectivoEsperado(c))
    }

    // ── Saldo y reparto de abonos ──

    private val vieja = SaldoVenta("a", fechaHora = 100, totalCentavos = 12_000, abonadoCentavos = 2_000) // debe $100
    private val nueva = SaldoVenta("b", fechaHora = 200, totalCentavos = 5_000, abonadoCentavos = 0) // debe $50

    @Test fun `saldo es la suma de lo que falta en cada venta`() =
        assertEquals(15_000L, ReglaFiado.saldo(listOf(vieja, nueva)))

    @Test fun `saldo de venta nunca es negativo`() =
        assertEquals(0L, SaldoVenta("x", 0, 1_000, 1_500).saldoCentavos)

    @Test fun `abono parcial va a la venta mas vieja`() =
        assertEquals(listOf(Aplicacion("a", 4_000)), ReglaFiado.repartir(4_000, listOf(nueva, vieja)).valor())

    @Test fun `abono exacto a la venta mas vieja no toca la nueva`() =
        assertEquals(listOf(Aplicacion("a", 10_000)), ReglaFiado.repartir(10_000, listOf(vieja, nueva)).valor())

    @Test fun `abono que pasa la venta vieja sigue con la nueva`() =
        assertEquals(listOf(Aplicacion("a", 10_000), Aplicacion("b", 2_000)), ReglaFiado.repartir(12_000, listOf(nueva, vieja)).valor())

    @Test fun `liquidar todo reparte el saldo completo`() {
        val r = ReglaFiado.repartir(15_000, listOf(vieja, nueva)).valor()
        assertEquals(15_000L, r.sumOf { it.montoCentavos })
        assertEquals(listOf(Aplicacion("a", 10_000), Aplicacion("b", 5_000)), r)
    }

    @Test fun `ventas ya pagadas se saltan`() {
        val pagada = SaldoVenta("p", fechaHora = 50, totalCentavos = 3_000, abonadoCentavos = 3_000)
        assertEquals(listOf(Aplicacion("a", 1_000)), ReglaFiado.repartir(1_000, listOf(pagada, vieja)).valor())
    }

    @Test fun `misma hora se ordena por id para que el reparto sea estable`() {
        val x = SaldoVenta("x", 100, 1_000, 0)
        val y = SaldoVenta("y", 100, 1_000, 0)
        assertEquals(listOf(Aplicacion("x", 1_000), Aplicacion("y", 500)), ReglaFiado.repartir(1_500, listOf(y, x)).valor())
    }

    @Test fun `abono mayor al saldo se rechaza`() =
        assertEquals("El abono es mayor a lo que debe (\$150.00)", ReglaFiado.repartir(15_001, listOf(vieja, nueva)).mensaje())

    @Test fun `abono de cero se rechaza`() =
        assertEquals("El abono debe ser mayor a cero", ReglaFiado.repartir(0, listOf(vieja)).mensaje())

    @Test fun `abono sin saldo pendiente se rechaza`() =
        assertEquals("El cliente no tiene saldo pendiente", ReglaFiado.repartir(1_000, emptyList()).mensaje())

    // ── Pago inicial y aviso de saldo alto ──

    @Test fun `pago inicial de cero es valido`() = assertNull(ReglaFiado.validarPagoInicial(0, 12_000))
    @Test fun `pago inicial parcial es valido`() = assertNull(ReglaFiado.validarPagoInicial(5_000, 12_000))
    @Test fun `pago inicial igual al total no es fiado`() =
        assertEquals("Si paga todo, no es fiado: cobre la venta normal", ReglaFiado.validarPagoInicial(12_000, 12_000))
    @Test fun `pago inicial negativo no es valido`() = assertTrue(ReglaFiado.validarPagoInicial(-1, 12_000) != null)

    @Test fun `saldo despues de fiar resta el pago inicial`() =
        assertEquals(17_000L, ReglaFiado.saldoDespues(saldoActual = 10_000, totalVenta = 12_000, pagoInicial = 5_000))

    @Test fun `aviso solo si el saldo pasa el monto`() {
        assertFalse(ReglaFiado.pasaAviso(14_999, 15_000))
        assertFalse(ReglaFiado.pasaAviso(15_000, 15_000))
        assertTrue(ReglaFiado.pasaAviso(15_001, 15_000))
    }

    // ── Cancelar abono ──

    @Test fun `abono de la caja abierta sin ventas canceladas se puede cancelar`() =
        assertTrue(ReglaFiado.puedeCancelarAbono(esAbono = true, registrado = true, suCajaAbierta = true, algunaVentaCancelada = false))

    @Test fun `abono de una caja cerrada no se cancela`() =
        assertFalse(ReglaFiado.puedeCancelarAbono(true, true, suCajaAbierta = false, algunaVentaCancelada = false))

    @Test fun `abono de una venta cancelada no se cancela (ya se devolvio)`() =
        assertFalse(ReglaFiado.puedeCancelarAbono(true, true, true, algunaVentaCancelada = true))

    @Test fun `devolucion o abono ya cancelado no se cancelan`() {
        assertFalse(ReglaFiado.puedeCancelarAbono(esAbono = false, registrado = true, suCajaAbierta = true, algunaVentaCancelada = false))
        assertFalse(ReglaFiado.puedeCancelarAbono(esAbono = true, registrado = false, suCajaAbierta = true, algunaVentaCancelada = false))
    }

    // ── Cliente ──

    @Test fun `nombre de cliente sin acentos mayusculas ni espacios de mas`() =
        assertEquals(ReglaFiado.normalizarNombre("jose perez"), ReglaFiado.normalizarNombre("  José   PÉREZ "))

    @Test fun `nombre de cliente obligatorio y de 2 a 40 caracteres`() {
        assertEquals("Este campo es obligatorio", ReglaFiado.validarNombre("   "))
        assertEquals("El nombre debe tener de 2 a 40 caracteres", ReglaFiado.validarNombre("J"))
        assertEquals("El nombre debe tener de 2 a 40 caracteres", ReglaFiado.validarNombre("x".repeat(41)))
        assertNull(ReglaFiado.validarNombre("Juan"))
    }

    @Test fun `telefono opcional o de 10 digitos`() {
        assertNull(ReglaFiado.validarTelefono(""))
        assertNull(ReglaFiado.validarTelefono("7341234567"))
        assertEquals("El teléfono debe tener 10 dígitos", ReglaFiado.validarTelefono("12345"))
    }

    // ── Venta rápida (monto libre) ──

    @Test fun `monto libre suma al total y descripcion vacia es Varios`() {
        val c = Carrito().agregar(carlota, 2_000).valor().agregarLibre("1", "  ", 2_500).valor()
        assertEquals(10_500L, c.subtotalCentavos)
        assertEquals("Varios", c.libres.single().descripcion)
        assertTrue(c.tieneLibres)
        assertFalse(c.estaVacio)
    }

    @Test fun `monto libre con descripcion se recorta a 40`() {
        val c = Carrito().agregarLibre("1", "Refresco de la tienda " + "x".repeat(40), 1_800).valor()
        assertEquals(40, c.libres.single().descripcion.length)
    }

    @Test fun `monto libre de cero se rechaza`() =
        assertEquals("El monto debe ser mayor a cero", Carrito().agregarLibre("1", "", 0).mensaje())

    @Test fun `carrito solo con monto libre no esta vacio y quitarlo lo vacia`() {
        val c = Carrito().agregarLibre("1", "", 1_000).valor()
        assertFalse(c.estaVacio)
        assertTrue(c.quitarLibre("1").estaVacio)
    }

    @Test fun `cambiar productos conserva los montos libres`() {
        val c = Carrito().agregarLibre("1", "", 1_000).valor().agregar(carlota).valor().quitar("c")
        assertEquals(1, c.libres.size)
    }

    @Test fun `corregir venta regresa los montos libres al carrito`() {
        val r = ReglaCorreccion.armar(
            listOf(LineaACorregir(null, "Refresco", 1_000, 1_800), LineaACorregir("c", "Carlota", 1_000, 4_000)),
        ) { id -> if (id == "c") carlota else null }
        assertEquals(1_800L + 4_000L, r.carrito.subtotalCentavos)
        assertEquals("Refresco", r.carrito.libres.single().descripcion)
        assertTrue(r.omitidos.isEmpty())
    }

    // ── Cobro con fiado ──

    private class VentasFalsas : PuertoVentas {
        var solicitud: SolicitudCobro? = null
        override suspend fun cobrar(solicitud: SolicitudCobro): Resultado<VentaCobrada> {
            this.solicitud = solicitud
            val v = Venta("v", "k", 0, 0, totalCentavos = 0, formaPagoId = solicitud.formaPagoId, creadoEn = 0, actualizadoEn = 0)
            return Resultado.Exito(VentaCobrada(v, emptyList(), emptyMap()))
        }
        override suspend fun cancelar(ventaId: String) = TODO()
    }

    private object SinHistorial : PuertoVentasRecientes {
        override suspend fun ventasRecientes(productoId: String) = VentasRecientes(0, 0)
    }

    private val tres = Carrito().agregar(carlota, 3_000).valor() // $120

    @Test fun `fiar no pide forma de pago ni monto recibido`() {
        val r = PrepararCobro.calcular(tres, null, null, null, fiar = true).valor()
        assertEquals(12_000L, r.totalCentavos)
        assertNull(r.cambioCentavos)
    }

    @Test fun `fiar con monto libre se rechaza`() {
        val c = tres.agregarLibre("1", "", 1_000).valor()
        assertEquals("Las ventas con monto libre no se pueden fiar", PrepararCobro.calcular(c, null, null, null, fiar = true).mensaje())
    }

    @Test fun `cobrar fiado sin pago inicial manda cliente y ninguna forma de pago`() = runTest {
        val ventas = VentasFalsas()
        val r = CobrarVenta(ventas, EvaluarExistenciaBaja(SinHistorial))(tres, efectivo, fiado = FiadoCapturado("cli", "", null))
        assertEquals("Venta fiada registrada exitosamente", (r as Resultado.Exito).valor.mensaje)
        val s = ventas.solicitud!!
        assertNull(s.formaPagoId)
        assertNull(s.montoRecibidoCentavos)
        assertEquals("cli", s.fiado!!.clienteId)
        assertEquals(0L, s.fiado!!.pagoInicialCentavos)
        assertNull(s.fiado!!.formaPagoInicialId)
    }

    @Test fun `cobrar fiado con pago inicial manda monto y su forma`() = runTest {
        val ventas = VentasFalsas()
        CobrarVenta(ventas, EvaluarExistenciaBaja(SinHistorial))(tres, null, fiado = FiadoCapturado("cli", "50", transferencia))
        assertEquals(5_000L, ventas.solicitud!!.fiado!!.pagoInicialCentavos)
        assertEquals("tr", ventas.solicitud!!.fiado!!.formaPagoInicialId)
    }

    @Test fun `fiar sin cliente pide el cliente`() = runTest {
        val r = CobrarVenta(VentasFalsas(), EvaluarExistenciaBaja(SinHistorial))(tres, null, fiado = FiadoCapturado(null, "", null))
        assertEquals("Elija o escriba el cliente", r.campos()[Campos.CLIENTE])
    }

    @Test fun `pago inicial igual al total se rechaza en el campo`() = runTest {
        val r = CobrarVenta(VentasFalsas(), EvaluarExistenciaBaja(SinHistorial))(tres, null, fiado = FiadoCapturado("cli", "120", efectivo))
        assertEquals("Si paga todo, no es fiado: cobre la venta normal", r.campos()[Campos.PAGO_INICIAL])
    }

    @Test fun `pago inicial sin forma de pago se rechaza`() = runTest {
        val r = CobrarVenta(VentasFalsas(), EvaluarExistenciaBaja(SinHistorial))(tres, null, fiado = FiadoCapturado("cli", "20", null))
        assertEquals("Este campo es obligatorio", r.campos()[Campos.FORMA_PAGO_INICIAL])
    }

    @Test fun `venta normal con monto libre manda el renglon Varios`() = runTest {
        val ventas = VentasFalsas()
        val c = tres.agregarLibre("1", "", 1_000).valor()
        CobrarVenta(ventas, EvaluarExistenciaBaja(SinHistorial))(c, transferencia)
        val s = ventas.solicitud!!
        assertEquals("tr", s.formaPagoId)
        assertNull(s.fiado)
        assertEquals("Varios", s.libres.single().descripcion)
        assertEquals(1_000L, s.libres.single().importeCentavos)
    }

    @Test fun `cancelar venta fiada con abonos avisa la devolucion en efectivo`() {
        val v = Venta("v", "k", 0, 12_000, totalCentavos = 12_000, formaPagoId = null, clienteId = "cli", creadoEn = 0, actualizadoEn = 0)
        val texto = CancelarVenta(VentasFalsas()).confirmacion(v, abonadoCentavos = 5_000)
        assertTrue(texto, texto.endsWith("Se devolverán \$50.00 en efectivo al cliente."))
        assertFalse(CancelarVenta(VentasFalsas()).confirmacion(v).contains("devolverán"))
    }
}
