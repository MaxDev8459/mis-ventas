package mx.ambulantpoint.misventas.domain

import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.regla.MotivoAvisoCarga
import mx.ambulantpoint.misventas.domain.regla.ProductoParaCarga
import mx.ambulantpoint.misventas.domain.regla.ReglaCarga
import mx.ambulantpoint.misventas.domain.regla.RenglonCarga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Carga del día: captura, "te quedan", sobrante y aviso al vender. */
class CargaTest {
    private val carlota = ProductoParaCarga("c", "Carlota", TipoVenta.UNIDAD, 20_000)
    private val queso = ProductoParaCarga("q", "Queso", TipoVenta.PESO, 2_000)

    private fun renglon(cargado: Long, vendido: Long = 0, mermas: Long = 0) =
        RenglonCarga("c", "Carlota", TipoVenta.UNIDAD, cargado, vendido, mermas)

    @Test fun `quedan es cargado menos vendido menos mermas`() {
        val r = renglon(10_000, vendido = 6_000, mermas = 1_000)
        assertEquals(3_000L, r.quedanMilesimas)
        assertEquals(3_000L, r.sobranteMilesimas)
    }

    @Test fun `si se vendio de mas el sobrante es cero`() {
        val r = renglon(5_000, vendido = 7_000)
        assertEquals(-2_000L, r.quedanMilesimas)
        assertEquals(0L, r.sobranteMilesimas)
    }

    @Test fun `captura valida con vacio como cero`() {
        val (cant, err) = ReglaCarga.leerCaptura(mapOf("c" to "12", "q" to ""), listOf(carlota, queso))
        assertTrue(err.isEmpty())
        assertEquals(mapOf("c" to 12_000L, "q" to 0L), cant)
    }

    @Test fun `captura por peso en kg`() {
        val (cant, err) = ReglaCarga.leerCaptura(mapOf("q" to "1.5"), listOf(queso))
        assertTrue(err.isEmpty())
        assertEquals(1_500L, cant["q"])
    }

    @Test fun `no se puede cargar mas que la existencia`() {
        val (_, err) = ReglaCarga.leerCaptura(mapOf("c" to "25"), listOf(carlota))
        assertEquals("Solo hay 20 pzas de Carlota", err["c"])
    }

    @Test fun `piezas con decimales no son validas`() {
        val (_, err) = ReglaCarga.leerCaptura(mapOf("c" to "1.5"), listOf(carlota))
        assertTrue(err.containsKey("c"))
    }

    @Test fun `sin carga en la caja nunca avisa`() =
        assertNull(ReglaCarga.aviso(hayCarga = false, renglon = null, enVentaMilesimas = 0, agregarMilesimas = 1_000))

    @Test fun `producto no cargado avisa`() =
        assertEquals(MotivoAvisoCarga.NO_CARGADO, ReglaCarga.aviso(true, null, 0, 1_000))

    @Test fun `producto cargado en cero cuenta como no cargado`() =
        assertEquals(MotivoAvisoCarga.NO_CARGADO, ReglaCarga.aviso(true, renglon(0), 0, 1_000))

    @Test fun `alcanza lo que queda contando lo que ya esta en la venta`() {
        val r = renglon(3_000, vendido = 1_000)
        assertNull(ReglaCarga.aviso(true, r, enVentaMilesimas = 1_000, agregarMilesimas = 1_000))
        assertEquals(MotivoAvisoCarga.SE_ACABO, ReglaCarga.aviso(true, r, enVentaMilesimas = 2_000, agregarMilesimas = 1_000))
    }

    @Test fun `quedan para la tarjeta descuenta la venta en curso`() {
        assertEquals(1_000L, ReglaCarga.quedanConVenta(renglon(3_000, vendido = 1_000), 1_000))
        assertEquals(0L, ReglaCarga.quedanConVenta(renglon(1_000), 3_000))
    }

    @Test fun `texto para prellenar sin separador de miles`() {
        assertEquals("1200", ReglaCarga.textoCaptura(1_200_000, TipoVenta.UNIDAD))
        assertEquals("1.500", ReglaCarga.textoCaptura(1_500, TipoVenta.PESO))
        assertEquals("", ReglaCarga.textoCaptura(0, TipoVenta.UNIDAD))
    }
}
