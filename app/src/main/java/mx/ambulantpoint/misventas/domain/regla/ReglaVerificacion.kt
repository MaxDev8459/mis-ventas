package mx.ambulantpoint.misventas.domain.regla

/**
 * Cuánto tiempo puede estar la app en segundo plano antes de volver a pedir huella, PIN o patrón.
 * Lo elige el vendedor en Perfil; se guarda solo en este teléfono. [NUNCA] = solo al abrir la app
 * desde cero (comportamiento de la versión 1.0).
 */
enum class TiempoReverificacion(val etiqueta: String, val margenMs: Long?) {
    NUNCA("Solo al abrir la app", null),
    UN_MINUTO("1 min", 60_000L),
    CINCO_MINUTOS("5 min", 5 * 60_000L),
    QUINCE_MINUTOS("15 min", 15 * 60_000L),
}

/** RF 1.2 (ampliado): volver a verificar la identidad al regresar de segundo plano. */
object ReglaVerificacion {
    /**
     * @param ausenteDesde momento (reloj monotónico, ms) en que la app pasó a segundo plano; 0 si no aplica.
     * @param yaBloqueada true si ya se está pidiendo la verificación (no se vuelve a disparar).
     */
    fun debeVerificar(
        tiempo: TiempoReverificacion,
        sesionActiva: Boolean,
        yaBloqueada: Boolean,
        ausenteDesde: Long,
        ahora: Long,
    ): Boolean {
        val margen = tiempo.margenMs ?: return false
        return sesionActiva && !yaBloqueada && ausenteDesde > 0 && ahora - ausenteDesde >= margen
    }
}
