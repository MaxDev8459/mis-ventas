package mx.ambulantpoint.misventas.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DineroTest {

    @Test
    fun `importe por unidad es cantidad por precio`() {
        // 3 pzas × $15.50 = $46.50
        assertEquals(4650L, Dinero.importe(3000, 1550))
    }

    @Test
    fun `importe por peso usa los gramos de la etiqueta`() {
        // 0.250 kg × $86.00/kg = $21.50
        assertEquals(2150L, Dinero.importe(250, 8600))
    }

    @Test
    fun `importe se redondea a centavos mitad hacia arriba`() {
        // 0.333 kg × $10.00 = $3.33 (3.330)
        assertEquals(333L, Dinero.importe(333, 1000))
        // 0.125 kg × $0.99 = 0.12375 → $0.12
        assertEquals(12L, Dinero.importe(125, 99))
        // 0.005 kg × $1.00 = 0.005 → $0.01
        assertEquals(1L, Dinero.importe(5, 100))
    }

    @Test
    fun `nombre normalizado ignora mayusculas y espacios de los extremos`() {
        assertEquals("papas fritas", normalizarNombre("  Papas FRITAS "))
        assertEquals(normalizarNombre("Bebidas"), normalizarNombre("bebidas "))
    }
}
