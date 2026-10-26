package mx.ambulantpoint.misventas.domain

import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.entity.TipoDescuento
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.regla.FallaMensaje
import mx.ambulantpoint.misventas.domain.regla.ReglaCobro
import mx.ambulantpoint.misventas.domain.regla.ReglaCorte
import mx.ambulantpoint.misventas.domain.regla.ReglaDescuento
import mx.ambulantpoint.misventas.domain.regla.ReglaExistenciaBaja
import mx.ambulantpoint.misventas.domain.regla.TipoDiferencia
import mx.ambulantpoint.misventas.domain.venta.Carrito
import mx.ambulantpoint.misventas.domain.venta.ProductoEnVenta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun <T> Resultado<T>.ok(): T = (this as Resultado.Exito).valor
internal fun Resultado<*>.textoFalla(): String = ((this as Resultado.Fallo).falla as FallaMensaje).mensaje

class ReglaDescuentoTest {
    @Test fun `porcentaje es subtotal por porcentaje entre 100 redondeado`() {
        assertEquals(1000L, ReglaDescuento.calcular(10_000, TipoDescuento.PORCENTAJE, 1000).ok())
        // $3.33 × 12.5 % = 0.41625 → $0.42
        assertEquals(42L, ReglaDescuento.calcular(333, TipoDescuento.PORCENTAJE, 1250).ok())
    }

    @Test fun `porcentaje menor a 1 o mayor o igual a 100 no es valido`() {
        assertEquals(Mensajes.DESCUENTO_NO_VALIDO, ReglaDescuento.calcular(10_000, TipoDescuento.PORCENTAJE, 99).textoFalla())
        assertEquals(Mensajes.DESCUENTO_NO_VALIDO, ReglaDescuento.calcular(10_000, TipoDescuento.PORCENTAJE, 10_000).textoFalla())
        assertEquals(100L, ReglaDescuento.calcular(10_000, TipoDescuento.PORCENTAJE, 100).ok())
    }

    @Test fun `monto fijo mayor a cero y menor al subtotal`() {
        assertEquals(1500L, ReglaDescuento.calcular(10_000, TipoDescuento.MONTO_FIJO, 1500).ok())
        assertEquals(Mensajes.DESCUENTO_NO_VALIDO, ReglaDescuento.calcular(10_000, TipoDescuento.MONTO_FIJO, 0).textoFalla())
        assertEquals(Mensajes.DESCUENTO_NO_VALIDO, ReglaDescuento.calcular(10_000, TipoDescuento.MONTO_FIJO, 10_000).textoFalla())
    }

    @Test fun `el total con descuento debe ser al menos un peso`() {
        assertEquals(Mensajes.DESCUENTO_TOTAL_MINIMO, ReglaDescuento.calcular(150, TipoDescuento.PORCENTAJE, 5000).textoFalla())
        assertEquals(Mensajes.DESCUENTO_TOTAL_MINIMO, ReglaDescuento.calcular(500, TipoDescuento.MONTO_FIJO, 450).textoFalla())
        assertEquals(400L, ReglaDescuento.calcular(500, TipoDescuento.MONTO_FIJO, 400).ok())
    }
}

class ReglaCobroTest {
    @Test fun `cambio es recibido menos total`() {
        assertEquals(3850L, ReglaCobro.cambio(6150, 10_000).ok())
        assertEquals(0L, ReglaCobro.cambio(6150, 6150).ok())
        assertEquals(Mensajes.MONTO_RECIBIDO_MENOR, ReglaCobro.cambio(6150, 6100).textoFalla())
    }
}

class ReglaExistenciaBajaTest {
    @Test fun `con menos de 7 dias de ventas usa umbral fijo`() {
        assertTrue(ReglaExistenciaBaja.esBaja(5_000, TipoVenta.UNIDAD, 3, 9_000))
        assertFalse(ReglaExistenciaBaja.esBaja(6_000, TipoVenta.UNIDAD, 6, 99_000))
        assertTrue(ReglaExistenciaBaja.esBaja(1_000, TipoVenta.PESO, 0, 0))
        assertFalse(ReglaExistenciaBaja.esBaja(1_001, TipoVenta.PESO, 0, 0))
    }

    @Test fun `con 7 dias es baja si alcanza para dos dias o menos`() {
        // Vendió 14 pzas en 7 días: promedio 2/día; umbral 4 pzas
        assertTrue(ReglaExistenciaBaja.esBaja(4_000, TipoVenta.UNIDAD, 7, 14_000))
        assertFalse(ReglaExistenciaBaja.esBaja(5_000, TipoVenta.UNIDAD, 7, 14_000))
        // Promedio 1.5 kg/7 días → umbral 0.428… kg
        assertTrue(ReglaExistenciaBaja.esBaja(428, TipoVenta.PESO, 7, 1_500))
        assertFalse(ReglaExistenciaBaja.esBaja(429, TipoVenta.PESO, 7, 1_500))
    }

    @Test fun `agotado siempre es bajo`() {
        assertTrue(ReglaExistenciaBaja.esBaja(0, TipoVenta.UNIDAD, 7, 0))
    }
}

class ReglaCorteTest {
    @Test fun `esperado es fondo mas efectivo y diferencia contado menos esperado`() {
        val esperado = ReglaCorte.efectivoEsperado(10_000, 6_000)
        assertEquals(16_000L, esperado)
        assertEquals(-1_000L, ReglaCorte.diferencia(15_000, esperado))
        assertEquals(TipoDiferencia.FALTANTE, ReglaCorte.tipoDiferencia(-1_000))
        assertEquals(TipoDiferencia.SOBRANTE, ReglaCorte.tipoDiferencia(1))
        assertEquals(TipoDiferencia.SIN_DIFERENCIA, ReglaCorte.tipoDiferencia(0))
        assertEquals("Faltante", TipoDiferencia.FALTANTE.etiqueta)
    }
}

class CarritoTest {
    private val refresco = ProductoEnVenta("r", "Refresco", TipoVenta.UNIDAD, 2000, 2_000)
    private val cacahuate = ProductoEnVenta("c", "Cacahuate", TipoVenta.PESO, 8600, 750)

    @Test fun `un toque agrega una pieza y el total se actualiza`() {
        val c = Carrito().agregar(refresco).ok().agregar(refresco).ok()
        assertEquals(2000L, c.cantidadDe("r"))
        assertEquals(4000L, c.subtotalCentavos)
    }

    @Test fun `peso de la etiqueta por precio por kg`() {
        val c = Carrito().agregar(refresco).ok().fijar(cacahuate, 250).ok()
        assertEquals(2000L + 2150L, c.subtotalCentavos)
    }

    @Test fun `no permite mas que la existencia con el mensaje del DFR`() {
        val c = Carrito().agregar(refresco).ok().agregar(refresco).ok()
        assertEquals("Solo hay 2 pzas disponibles de Refresco", c.agregar(refresco).textoFalla())
        assertEquals("Solo hay 0.750 kg disponibles de Cacahuate", Carrito().fijar(cacahuate, 800).textoFalla())
    }

    @Test fun `cantidad cero quita el producto`() {
        val c = Carrito().agregar(refresco).ok().fijar(refresco, 0).ok()
        assertTrue(c.estaVacio)
        assertEquals(0L, c.subtotalCentavos)
    }
}
