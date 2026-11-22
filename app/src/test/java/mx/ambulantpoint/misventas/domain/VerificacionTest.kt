package mx.ambulantpoint.misventas.domain

import mx.ambulantpoint.misventas.domain.regla.ReglaVerificacion
import mx.ambulantpoint.misventas.domain.regla.ReglaVerificacion.AUSENCIA_MINIMA_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RF 1.2 — volver a verificar la identidad al regresar de segundo plano. */
class VerificacionTest {
    private val salio = 5_000_000L

    @Test fun `regresar despues del margen pide verificar`() =
        assertTrue(ReglaVerificacion.debeVerificar(true, false, salio, salio + AUSENCIA_MINIMA_MS))

    @Test fun `regresar antes del margen no pide verificar`() =
        assertFalse(ReglaVerificacion.debeVerificar(true, false, salio, salio + AUSENCIA_MINIMA_MS - 1))

    @Test fun `sin sesion activa no pide verificar`() =
        assertFalse(ReglaVerificacion.debeVerificar(false, false, salio, salio + 10 * AUSENCIA_MINIMA_MS))

    @Test fun `si ya se esta pidiendo no se vuelve a disparar`() =
        assertFalse(ReglaVerificacion.debeVerificar(true, true, salio, salio + 10 * AUSENCIA_MINIMA_MS))

    @Test fun `sin registro de salida no pide verificar`() =
        assertFalse(ReglaVerificacion.debeVerificar(true, false, 0L, 10 * AUSENCIA_MINIMA_MS))
}
