package mx.ambulantpoint.misventas.domain.regla

/**
 * RF 1.2 — al volver a abrir la aplicación con la sesión activa se verifica la identidad.
 *
 * Además del arranque en frío, se considera "volver a abrir" regresar de segundo plano después de
 * [AUSENCIA_MINIMA_MS]. El margen evita pedir la huella cada vez que el vendedor toma una foto o
 * comparte un reporte (la cámara y el menú de compartir mandan la app a segundo plano unos segundos).
 * El tiempo es decisión del equipo: el DFR no lo fija.
 */
object ReglaVerificacion {
    const val AUSENCIA_MINIMA_MS = 60_000L

    /**
     * @param ausenteDesde momento (reloj monotónico, ms) en que la app pasó a segundo plano; 0 si no aplica.
     * @param yaBloqueada true si ya se está pidiendo la verificación (no se vuelve a disparar).
     */
    fun debeVerificar(sesionActiva: Boolean, yaBloqueada: Boolean, ausenteDesde: Long, ahora: Long): Boolean =
        sesionActiva && !yaBloqueada && ausenteDesde > 0 && ahora - ausenteDesde >= AUSENCIA_MINIMA_MS
}
