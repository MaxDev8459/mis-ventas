package mx.ambulantpoint.misventas.domain

import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.regla.ProductoParaCarga
import mx.ambulantpoint.misventas.domain.regla.ReglaPrecarga
import org.junit.Assert.assertEquals
import org.junit.Test

/** Precargas y control + / − de la carga del día. */
class PrecargaTest {
    private val papas = ProductoParaCarga("p", "Papas", TipoVenta.UNIDAD, 26_000)
    private val chicles = ProductoParaCarga("c", "Chicles", TipoVenta.UNIDAD, 4_000)
    private val cacahuate = ProductoParaCarga("k", "Cacahuate", TipoVenta.PESO, 1_600)
    private val productos = listOf(papas, chicles, cacahuate)

    @Test fun `codificar y decodificar conserva las cantidades mayores a cero`() {
        val texto = ReglaPrecarga.codificar(mapOf("p" to 10_000L, "k" to 500L, "c" to 0L))
        assertEquals("k=500;p=10000", texto)
        assertEquals(mapOf("k" to 500L, "p" to 10_000L), ReglaPrecarga.decodificar(texto))
    }

    @Test fun `decodificar ignora pedazos raros`() =
        assertEquals(mapOf("p" to 5_000L), ReglaPrecarga.decodificar("p=5000;;x;y=abc;z=-3"))

    @Test fun `aplicar respeta lo disponible y omite lo que ya no esta`() {
        val r = ReglaPrecarga.aplicar(mapOf("p" to 10_000L, "c" to 10_000L, "viejo" to 5_000L), productos) { if (it == "viejo") "Gomitas" else null }
        assertEquals(10_000L, r.cantidades["p"])
        assertEquals(4_000L, r.cantidades["c"]) // solo hay 4
        assertEquals(0L, r.cantidades["k"])     // no venía en la precarga
        assertEquals(listOf("Gomitas"), r.omitidos)
    }

    @Test fun `n de cada producto usa piezas y la unidad para kilo`() {
        val r = ReglaPrecarga.nDeCada(10, 100, productos)
        assertEquals(10_000L, r["p"])
        assertEquals(4_000L, r["c"])  // tope: existencia
        assertEquals(1_000L, r["k"])  // 10 × 100 g = 1 kg
    }

    @Test fun `n de cada con 250 g y tope por existencia`() =
        assertEquals(1_600L, ReglaPrecarga.nDeCada(10, 250, productos)["k"]) // 2.5 kg pedido, hay 1.6

    @Test fun `mas y menos por pieza`() {
        assertEquals(1_000L, ReglaPrecarga.mas(0, TipoVenta.UNIDAD, 100, 26_000))
        assertEquals(26_000L, ReglaPrecarga.mas(26_000, TipoVenta.UNIDAD, 100, 26_000))
        assertEquals(0L, ReglaPrecarga.menos(0, TipoVenta.UNIDAD, 100))
        assertEquals(2_000L, ReglaPrecarga.menos(3_000, TipoVenta.UNIDAD, 100))
    }

    @Test fun `mas y menos por kilo con la unidad configurada`() {
        assertEquals(100L, ReglaPrecarga.mas(0, TipoVenta.PESO, 100, 1_600))
        assertEquals(1_600L, ReglaPrecarga.mas(1_550, TipoVenta.PESO, 100, 1_600))
        assertEquals(0L, ReglaPrecarga.menos(50, TipoVenta.PESO, 100))
        assertEquals(250L, ReglaPrecarga.mas(0, TipoVenta.PESO, 250, 1_600))
    }
}
