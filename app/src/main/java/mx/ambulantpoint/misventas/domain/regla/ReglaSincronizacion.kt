package mx.ambulantpoint.misventas.domain.regla

import mx.ambulantpoint.misventas.core.Mensajes

/** Tipo de conexión del teléfono (RF 7.1: Wi-Fi sincroniza solo; datos móviles pregunta). */
enum class TipoRed { NINGUNA, WIFI, MOVIL }

/** RF 7.2 — estados del indicador. */
sealed interface EstadoIndicador {
    val texto: String

    data object Sincronizado : EstadoIndicador { override val texto = Mensajes.SINCRONIZADO }
    data object Sincronizando : EstadoIndicador { override val texto = Mensajes.SINCRONIZANDO }
    data object SinConexion : EstadoIndicador { override val texto = Mensajes.SIN_CONEXION }
    data class Pendientes(val n: Int) : EstadoIndicador { override val texto = Mensajes.registrosPendientes(n) }
}

/** Qué hacer de forma automática al cambiar la conexión o los pendientes (RF 7.1). */
enum class AccionAutomatica { NADA, SINCRONIZAR, PREGUNTAR_DATOS_MOVILES }

/** Respuesta a "Sincronizar ahora" (RF 7.3). */
enum class AccionManual { SIN_INTERNET, YA_SINCRONIZADO, PREGUNTAR_DATOS_MOVILES, SINCRONIZAR }

object ReglaSincronizacion {

    /**
     * RF 7.2. Prioridad: sincronizando → sin pendientes ("Sincronizado", aunque no haya red: todo
     * está respaldado) → sin red ("Sin conexión") → "[n] registros pendientes". El orden es decisión
     * del equipo; el DFR solo lista los estados.
     */
    fun indicador(red: TipoRed, pendientes: Int, sincronizando: Boolean): EstadoIndicador = when {
        sincronizando -> EstadoIndicador.Sincronizando
        pendientes <= 0 -> EstadoIndicador.Sincronizado
        red == TipoRed.NINGUNA -> EstadoIndicador.SinConexion
        else -> EstadoIndicador.Pendientes(pendientes)
    }

    /**
     * RF 7.1. Con Wi-Fi sincroniza sin preguntar. Con datos móviles pregunta, salvo que el vendedor
     * ya haya cancelado: entonces no se vuelve a preguntar hasta que la app se abra de nuevo, se
     * conecte a Wi-Fi o use "Sincronizar ahora" (quien llama reinicia [rechazoDatosMoviles]).
     */
    fun accionAutomatica(
        red: TipoRed,
        pendientes: Int,
        sesionActiva: Boolean,
        rechazoDatosMoviles: Boolean,
    ): AccionAutomatica = when {
        !sesionActiva || pendientes <= 0 -> AccionAutomatica.NADA
        red == TipoRed.WIFI -> AccionAutomatica.SINCRONIZAR
        red == TipoRed.MOVIL && !rechazoDatosMoviles -> AccionAutomatica.PREGUNTAR_DATOS_MOVILES
        else -> AccionAutomatica.NADA
    }

    /** RF 7.3. */
    fun accionManual(red: TipoRed, pendientes: Int): AccionManual = when {
        red == TipoRed.NINGUNA -> AccionManual.SIN_INTERNET
        pendientes <= 0 -> AccionManual.YA_SINCRONIZADO
        red == TipoRed.MOVIL -> AccionManual.PREGUNTAR_DATOS_MOVILES
        else -> AccionManual.SINCRONIZAR
    }
}
