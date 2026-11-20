package mx.ambulantpoint.misventas.domain

import mx.ambulantpoint.misventas.domain.regla.ReglaNotificaciones
import mx.ambulantpoint.misventas.domain.regla.ReglaNotificaciones.DIA_MS
import mx.ambulantpoint.misventas.domain.regla.ReglaNotificaciones.HORA_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RF 5.4 — caja abierta 12 horas. */
class NotificacionCajaTest {
    private val abierta = 1_000_000_000_000L

    @Test fun `recien abierta faltan 12 horas`() =
        assertEquals(12 * HORA_MS, ReglaNotificaciones.esperaCajaAbierta(abierta, abierta))

    @Test fun `a las 5 horas faltan 7`() =
        assertEquals(7 * HORA_MS, ReglaNotificaciones.esperaCajaAbierta(abierta, abierta + 5 * HORA_MS))

    @Test fun `pasadas las 12 horas se avisa de inmediato`() =
        assertEquals(0L, ReglaNotificaciones.esperaCajaAbierta(abierta, abierta + 13 * HORA_MS))
}

/** RF 7.6 — 3 días sin respaldo, repetido cada 24 h. */
class NotificacionSinRespaldoTest {
    private val sync = 1_000_000_000_000L
    private val instalacion = sync - 10 * DIA_MS

    private fun avisar(pendientes: Int = 5, ultima: Long? = sync, aviso: Long? = null, ahora: Long) =
        ReglaNotificaciones.avisarSinRespaldo(pendientes, ultima, instalacion, aviso, ahora)

    @Test fun `antes de 3 dias no avisa`() = assertFalse(avisar(ahora = sync + 3 * DIA_MS - 1))

    @Test fun `a los 3 dias con pendientes avisa`() = assertTrue(avisar(ahora = sync + 3 * DIA_MS))

    @Test fun `sin pendientes no avisa`() = assertFalse(avisar(pendientes = 0, ahora = sync + 5 * DIA_MS))

    @Test fun `no repite antes de 24 horas`() {
        val aviso = sync + 3 * DIA_MS
        assertFalse(avisar(aviso = aviso, ahora = aviso + 23 * HORA_MS))
        assertTrue(avisar(aviso = aviso, ahora = aviso + DIA_MS))
    }

    @Test fun `un aviso anterior a la ultima sincronizacion no cuenta`() {
        // Avisó, después sincronizó y volvieron a pasar 3 días: es un ciclo nuevo y avisa otra vez.
        val avisoViejo = sync - 2 * HORA_MS
        assertTrue(avisar(aviso = avisoViejo, ahora = sync + 3 * DIA_MS))
    }

    @Test fun `si nunca se sincronizo cuenta desde la instalacion`() {
        assertTrue(avisar(ultima = null, ahora = instalacion + 3 * DIA_MS))
        assertFalse(avisar(ultima = null, ahora = instalacion + 2 * DIA_MS))
    }
}
