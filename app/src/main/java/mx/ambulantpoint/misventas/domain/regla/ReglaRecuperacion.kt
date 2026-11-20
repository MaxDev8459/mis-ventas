package mx.ambulantpoint.misventas.domain.regla

/** RF 7.4: qué hacer con la información del teléfono al terminar de iniciar sesión. */
enum class DecisionRecuperacion {
    /** Reemplazar lo del teléfono por el respaldo en línea de la cuenta. */
    RECUPERAR,
    /** Dejar lo del teléfono como está (no hay nada que bajar o se perderían registros). */
    CONSERVAR_LOCAL,
}

object ReglaRecuperacion {
    /**
     * - Si el teléfono tiene ventas, cajas, productos, categorías o movimientos sin enviar, no se
     *   reemplazan (se perderían): se conservan y la sincronización los sube. Las formas de pago
     *   iniciales que crea la app al instalarse no cuentan (se recuperan las de la cuenta).
     * - Si la cuenta no tiene respaldo en línea y el teléfono ya es de esa cuenta, no hay nada que bajar.
     * - En cualquier otro caso se recupera: teléfono nuevo, datos borrados, otra cuenta, o el
     *   vendedor regresa a un teléfono después de usar otro (lo local puede estar atrasado, RF 7.5).
     */
    fun decidir(
        pendientesNegocio: Int,
        nubeTienePerfil: Boolean,
        correoLocal: String?,
        correoSesion: String,
    ): DecisionRecuperacion = when {
        pendientesNegocio > 0 -> DecisionRecuperacion.CONSERVAR_LOCAL
        !nubeTienePerfil && correoLocal.equals(correoSesion, ignoreCase = true) -> DecisionRecuperacion.CONSERVAR_LOCAL
        else -> DecisionRecuperacion.RECUPERAR
    }
}

/** RF 7.5: qué hace este teléfono al conocer qué dispositivo tiene la sesión activa de la cuenta. */
enum class AccionSesion {
    NADA,
    /** La cuenta no tiene dispositivo registrado: este lo reclama. */
    RECLAMAR,
    /** La sesión se inició en otro teléfono: primero se envían los pendientes de este. */
    ENVIAR_PENDIENTES,
    /** La sesión se inició en otro teléfono y ya no hay pendientes: se cierra. */
    CERRAR_SESION,
}

object ReglaSesionUnica {
    /**
     * @param dispositivoNube el registrado en línea (null = ninguno).
     * @param desdeServidor false si el dato viene de la copia local de Firestore: no se actúa con
     *   datos que pueden estar atrasados.
     */
    fun accion(dispositivoNube: String?, miDispositivo: String, desdeServidor: Boolean, pendientes: Int): AccionSesion = when {
        !desdeServidor -> AccionSesion.NADA
        dispositivoNube == null -> AccionSesion.RECLAMAR
        dispositivoNube == miDispositivo -> AccionSesion.NADA
        pendientes > 0 -> AccionSesion.ENVIAR_PENDIENTES
        else -> AccionSesion.CERRAR_SESION
    }

    /** RF 7.5: mostrar "Su cuenta se cerrará en el otro dispositivo…" al iniciar sesión aquí. */
    fun avisarAlIniciar(dispositivoAnterior: String?, miDispositivo: String): Boolean =
        dispositivoAnterior != null && dispositivoAnterior != miDispositivo
}
