package mx.ambulantpoint.misventas.domain.regla

/** RF 5.4 y 7.6 — cuándo avisar. Tiempos en epoch ms. */
object ReglaNotificaciones {
    const val HORA_MS = 60L * 60 * 1000
    const val DIA_MS = 24 * HORA_MS

    /** RF 5.4: a las 12 horas de abrir la caja. */
    const val HORAS_CAJA_ABIERTA = 12L

    /** RF 7.6: 3 días sin sincronizar; se repite cada 24 h. */
    const val DIAS_SIN_RESPALDO = 3L

    /** Cuánto falta para avisar de la caja abierta (0 si ya se cumplieron las 12 horas). */
    fun esperaCajaAbierta(abiertaEn: Long, ahora: Long): Long =
        (abiertaEn + HORAS_CAJA_ABIERTA * HORA_MS - ahora).coerceAtLeast(0)

    /**
     * RF 7.6: hay pendientes y pasaron 3 días desde la última sincronización exitosa (o, si nunca se
     * ha sincronizado, desde [referenciaSinSincronizar], p. ej. la instalación). Se repite cada 24 h:
     * no se avisa si el último aviso fue hace menos de un día. Un aviso anterior a la última
     * sincronización exitosa no cuenta (empezó un ciclo nuevo).
     */
    fun avisarSinRespaldo(
        pendientes: Int,
        ultimaSincronizacion: Long?,
        referenciaSinSincronizar: Long,
        ultimoAviso: Long?,
        ahora: Long,
    ): Boolean {
        if (pendientes <= 0) return false
        val desde = ultimaSincronizacion ?: referenciaSinSincronizar
        if (ahora - desde < DIAS_SIN_RESPALDO * DIA_MS) return false
        val avisoVigente = ultimoAviso?.takeIf { ultimaSincronizacion == null || it >= ultimaSincronizacion }
        return avisoVigente == null || ahora - avisoVigente >= DIA_MS
    }
}
