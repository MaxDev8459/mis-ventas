package mx.ambulantpoint.misventas.domain

import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.regla.ReglaConteoEfectivo
import mx.ambulantpoint.misventas.domain.regla.ReglaVerificacion
import mx.ambulantpoint.misventas.domain.regla.TiempoReverificacion
import mx.ambulantpoint.misventas.domain.venta.LineaACorregir
import mx.ambulantpoint.misventas.domain.venta.ProductoEnVenta
import mx.ambulantpoint.misventas.domain.venta.ReglaCorreccion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reverificación configurable al regresar de segundo plano. */
class ReverificacionTest {
    private val salio = 5_000_000L
    private val unMin = TiempoReverificacion.UN_MINUTO

    @Test fun `regresar despues del margen pide verificar`() =
        assertTrue(ReglaVerificacion.debeVerificar(unMin, true, false, salio, salio + 60_000))

    @Test fun `regresar antes del margen no pide verificar`() =
        assertFalse(ReglaVerificacion.debeVerificar(unMin, true, false, salio, salio + 59_999))

    @Test fun `cinco minutos respeta su margen`() {
        assertFalse(ReglaVerificacion.debeVerificar(TiempoReverificacion.CINCO_MINUTOS, true, false, salio, salio + 299_999))
        assertTrue(ReglaVerificacion.debeVerificar(TiempoReverificacion.CINCO_MINUTOS, true, false, salio, salio + 300_000))
    }

    @Test fun `con nunca no pide verificar aunque pase mucho tiempo`() =
        assertFalse(ReglaVerificacion.debeVerificar(TiempoReverificacion.NUNCA, true, false, salio, salio + 86_400_000))

    @Test fun `sin sesion activa no pide verificar`() =
        assertFalse(ReglaVerificacion.debeVerificar(unMin, false, false, salio, salio + 600_000))

    @Test fun `si ya se esta pidiendo no se vuelve a disparar`() =
        assertFalse(ReglaVerificacion.debeVerificar(unMin, true, true, salio, salio + 600_000))

    @Test fun `sin registro de salida no pide verificar`() =
        assertFalse(ReglaVerificacion.debeVerificar(unMin, true, false, 0L, 600_000))
}

/** Calculadora de billetes y monedas del corte. */
class ConteoEfectivoTest {
    @Test fun `suma billetes y monedas`() {
        val total = ReglaConteoEfectivo.total(mapOf(50_000L to "2", 2_000L to "3", 500L to "1", 50L to "3"))
        assertEquals(106_650L, total) // 2×500 + 3×20 + 1×5 + 3×0.50 = 1066.50
    }

    @Test fun `vacio cuenta como cero`() = assertEquals(0L, ReglaConteoEfectivo.total(emptyMap()))

    @Test fun `piezas no enteras no son validas`() {
        assertNull(ReglaConteoEfectivo.total(mapOf(10_000L to "1.5")))
        assertNull(ReglaConteoEfectivo.total(mapOf(10_000L to "-2")))
        assertNull(ReglaConteoEfectivo.total(mapOf(10_000L to "abc")))
    }

    @Test fun `espacios se ignoran`() = assertEquals(20_000L, ReglaConteoEfectivo.total(mapOf(10_000L to " 2 ")))

    @Test fun `texto para el campo de efectivo contado`() {
        assertEquals("1066.50", ReglaConteoEfectivo.comoTexto(106_650))
        assertEquals("0.00", ReglaConteoEfectivo.comoTexto(0))
        assertEquals("20.05", ReglaConteoEfectivo.comoTexto(2_005))
    }
}

/** Corregir venta = cancelar y recapturar. */
class CorreccionVentaTest {
    private val refresco = ProductoEnVenta("r", "Refresco", TipoVenta.UNIDAD, 2_500, 10_000)
    private val queso = ProductoEnVenta("q", "Queso", TipoVenta.PESO, 18_000, 2_000)
    private val catalogo = mapOf("r" to refresco, "q" to queso)

    @Test fun `carga los productos con su cantidad y el precio de hoy`() {
        val c = ReglaCorreccion.armar(
            listOf(LineaACorregir("r", "Refresco", 3_000), LineaACorregir("q", "Queso", 250)),
        ) { catalogo[it] }
        assertEquals(emptyList<String>(), c.omitidos)
        assertEquals(3_000L, c.carrito.cantidadDe("r"))
        assertEquals(250L, c.carrito.cantidadDe("q"))
        assertEquals(3 * 2_500L + 4_500L, c.carrito.subtotalCentavos)
    }

    @Test fun `producto inactivo queda fuera y se avisa`() {
        val c = ReglaCorreccion.armar(listOf(LineaACorregir("x", "Chicle", 1_000), LineaACorregir("r", "Refresco", 1_000))) { catalogo[it] }
        assertEquals(listOf("Chicle"), c.omitidos)
        assertEquals(1, c.carrito.renglones.size)
    }

    @Test fun `sin existencia suficiente queda fuera`() {
        val c = ReglaCorreccion.armar(listOf(LineaACorregir("q", "Queso", 3_000))) { catalogo[it] }
        assertEquals(listOf("Queso"), c.omitidos)
        assertTrue(c.carrito.estaVacio)
    }
}
