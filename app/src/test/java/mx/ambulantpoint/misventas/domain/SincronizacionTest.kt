package mx.ambulantpoint.misventas.domain

import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.domain.regla.AccionAutomatica
import mx.ambulantpoint.misventas.domain.regla.AccionManual
import mx.ambulantpoint.misventas.domain.regla.EstadoIndicador
import mx.ambulantpoint.misventas.domain.regla.ReglaSincronizacion
import mx.ambulantpoint.misventas.domain.regla.TipoRed
import org.junit.Assert.assertEquals
import org.junit.Test

/** RF 7.2 — estados del indicador. */
class IndicadorSincronizacionTest {
    @Test fun `sincronizando tiene prioridad`() =
        assertEquals(EstadoIndicador.Sincronizando, ReglaSincronizacion.indicador(TipoRed.WIFI, 5, sincronizando = true))

    @Test fun `sin pendientes es sincronizado aunque no haya red`() {
        assertEquals(EstadoIndicador.Sincronizado, ReglaSincronizacion.indicador(TipoRed.WIFI, 0, false))
        assertEquals(EstadoIndicador.Sincronizado, ReglaSincronizacion.indicador(TipoRed.NINGUNA, 0, false))
    }

    @Test fun `con pendientes y sin red es sin conexion`() =
        assertEquals(EstadoIndicador.SinConexion, ReglaSincronizacion.indicador(TipoRed.NINGUNA, 3, false))

    @Test fun `con pendientes y red muestra cuantos`() {
        val e = ReglaSincronizacion.indicador(TipoRed.MOVIL, 12, false)
        assertEquals(EstadoIndicador.Pendientes(12), e)
        assertEquals("12 registros pendientes", e.texto)
    }

    @Test fun `textos del DFR`() {
        assertEquals(Mensajes.SINCRONIZADO, EstadoIndicador.Sincronizado.texto)
        assertEquals(Mensajes.SINCRONIZANDO, EstadoIndicador.Sincronizando.texto)
        assertEquals(Mensajes.SIN_CONEXION, EstadoIndicador.SinConexion.texto)
    }
}

/** RF 7.1 — sincronización automática. */
class SincronizacionAutomaticaTest {
    private fun accion(red: TipoRed, pendientes: Int = 4, sesion: Boolean = true, rechazo: Boolean = false) =
        ReglaSincronizacion.accionAutomatica(red, pendientes, sesion, rechazo)

    @Test fun `con wifi sincroniza sin preguntar`() = assertEquals(AccionAutomatica.SINCRONIZAR, accion(TipoRed.WIFI))

    @Test fun `con wifi sincroniza aunque antes haya cancelado con datos`() =
        assertEquals(AccionAutomatica.SINCRONIZAR, accion(TipoRed.WIFI, rechazo = true))

    @Test fun `con datos moviles pregunta`() = assertEquals(AccionAutomatica.PREGUNTAR_DATOS_MOVILES, accion(TipoRed.MOVIL))

    @Test fun `si cancelo con datos moviles no vuelve a preguntar`() =
        assertEquals(AccionAutomatica.NADA, accion(TipoRed.MOVIL, rechazo = true))

    @Test fun `sin red, sin pendientes o sin sesion no hace nada`() {
        assertEquals(AccionAutomatica.NADA, accion(TipoRed.NINGUNA))
        assertEquals(AccionAutomatica.NADA, accion(TipoRed.WIFI, pendientes = 0))
        assertEquals(AccionAutomatica.NADA, accion(TipoRed.WIFI, sesion = false))
    }
}

/** RF 7.3 — "Sincronizar ahora". */
class SincronizacionManualTest {
    @Test fun `sin red avisa`() = assertEquals(AccionManual.SIN_INTERNET, ReglaSincronizacion.accionManual(TipoRed.NINGUNA, 3))
    @Test fun `sin red avisa aunque no haya pendientes`() =
        assertEquals(AccionManual.SIN_INTERNET, ReglaSincronizacion.accionManual(TipoRed.NINGUNA, 0))
    @Test fun `sin pendientes ya esta sincronizada`() =
        assertEquals(AccionManual.YA_SINCRONIZADO, ReglaSincronizacion.accionManual(TipoRed.WIFI, 0))
    @Test fun `con datos moviles pregunta`() =
        assertEquals(AccionManual.PREGUNTAR_DATOS_MOVILES, ReglaSincronizacion.accionManual(TipoRed.MOVIL, 3))
    @Test fun `con wifi sincroniza`() = assertEquals(AccionManual.SINCRONIZAR, ReglaSincronizacion.accionManual(TipoRed.WIFI, 3))
}
