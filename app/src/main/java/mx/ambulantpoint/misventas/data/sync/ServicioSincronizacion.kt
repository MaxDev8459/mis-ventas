package mx.ambulantpoint.misventas.data.sync

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.red.Conectividad
import mx.ambulantpoint.misventas.domain.puertos.EstadoSesion
import mx.ambulantpoint.misventas.domain.puertos.PuertoAuth
import mx.ambulantpoint.misventas.domain.regla.AccionAutomatica
import mx.ambulantpoint.misventas.domain.regla.AccionManual
import mx.ambulantpoint.misventas.domain.regla.EstadoIndicador
import mx.ambulantpoint.misventas.domain.regla.ReglaSincronizacion
import mx.ambulantpoint.misventas.domain.regla.TipoRed

/**
 * RF 7.1 – 7.3. Vive mientras la app está abierta: observa la conexión y los registros pendientes,
 * decide si sincroniza solo (Wi-Fi) o pregunta (datos móviles) y expone el indicador.
 */
@OptIn(FlowPreview::class)
class ServicioSincronizacion(
    contexto: Context,
    private val db: MisVentasDb,
    private val auth: PuertoAuth,
    private val red: Conectividad,
    private val reloj: Reloj,
    private val sincronizador: SincronizadorFirestore,
    /** RF 7.4: mientras haya una recuperación pendiente no se envía nada (se reemplazará lo local). */
    private val bloqueada: StateFlow<Boolean>,
) {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val candado = Mutex()
    private val prefs = contexto.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    private val tipoRed: StateFlow<TipoRed> = red.observar().stateIn(alcance, SharingStarted.Eagerly, red.tipoActual())
    val pendientes: StateFlow<Int> = db.syncDao().observarPendientes().stateIn(alcance, SharingStarted.Eagerly, 0)
    private val sincronizando = MutableStateFlow(false)

    /** RF 7.2: lo que muestra el indicador de la pantalla principal. */
    val indicador: StateFlow<EstadoIndicador> = combine(tipoRed, pendientes, sincronizando) { r, p, s ->
        ReglaSincronizacion.indicador(r, p, s)
    }.stateIn(alcance, SharingStarted.Eagerly, EstadoIndicador.Sincronizado)

    private val _ultima = MutableStateFlow(prefs.getLong(CLAVE_ULTIMA, 0L).takeIf { it > 0 })
    /** RF 7.2: fecha y hora de la última sincronización exitosa (null = nunca). */
    val ultimaSincronizacion: StateFlow<Long?> = _ultima.asStateFlow()

    /** RF 7.1 / 7.3: hay que mostrar el aviso de datos móviles. `manual` = lo pidió "Sincronizar ahora". */
    data class AvisoDatosMoviles(val manual: Boolean)
    private val _aviso = MutableStateFlow<AvisoDatosMoviles?>(null)
    val avisoDatosMoviles: StateFlow<AvisoDatosMoviles?> = _aviso.asStateFlow()

    /** RF 7.1: el vendedor canceló con datos móviles. Se reinicia al abrir la app (proceso nuevo), con Wi-Fi o con "Sincronizar ahora". */
    @Volatile private var rechazoDatosMoviles = false

    /** Mensajes de una sola vez para el vendedor (RF 7.3). */
    private val _mensajes = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensajes.asStateFlow()
    fun mensajeMostrado() { _mensajes.value = null }

    init {
        alcance.launch {
            combine(tipoRed, pendientes, auth.estado, bloqueada) { r, p, s, b -> Triple(r, p, s == EstadoSesion.ACTIVA && !b) }
                // Espera a que termine una ráfaga de cambios (ej. una venta escribe varias tablas).
                .debounce(1_500)
                .collect { (r, p, activa) ->
                    if (r == TipoRed.WIFI) rechazoDatosMoviles = false
                    when (ReglaSincronizacion.accionAutomatica(r, p, activa, rechazoDatosMoviles)) {
                        AccionAutomatica.SINCRONIZAR -> sincronizar(manual = false)
                        AccionAutomatica.PREGUNTAR_DATOS_MOVILES -> if (_aviso.value == null) _aviso.value = AvisoDatosMoviles(manual = false)
                        AccionAutomatica.NADA -> if (r != TipoRed.MOVIL) _aviso.value = null
                    }
                }
        }
    }

    /** RF 7.3 — botón "Sincronizar ahora". */
    fun sincronizarAhora() {
        rechazoDatosMoviles = false
        when (ReglaSincronizacion.accionManual(red.tipoActual(), pendientes.value)) {
            AccionManual.SIN_INTERNET -> _mensajes.value = Mensajes.SYNC_SIN_INTERNET
            AccionManual.YA_SINCRONIZADO -> _mensajes.value = Mensajes.YA_SINCRONIZADO
            AccionManual.PREGUNTAR_DATOS_MOVILES -> _aviso.value = AvisoDatosMoviles(manual = true)
            AccionManual.SINCRONIZAR -> alcance.launch { sincronizar(manual = true) }
        }
    }

    /** Respuesta al aviso de datos móviles. */
    fun responderDatosMoviles(sincronizar: Boolean) {
        val aviso = _aviso.value ?: return
        _aviso.value = null
        if (sincronizar) alcance.launch { sincronizar(aviso.manual) } else rechazoDatosMoviles = true
    }

    /**
     * RF 7.5: la sesión se inició en otro teléfono; antes de cerrarla aquí se envían los pendientes,
     * con Wi-Fi o con datos móviles, para que no se pierdan ventas. Espera a la sincronización en
     * curso, si la hay. Devuelve true si el envío terminó bien.
     */
    /**
     * RF 7.1 con la app cerrada (lo llama [SincronizacionWorker], que solo corre con Wi-Fi). Sin
     * sesión, con la recuperación pendiente o sin pendientes no hace nada. Devuelve false si el
     * envío falló (WorkManager lo reintenta).
     */
    suspend fun sincronizarEnSegundoPlano(): Boolean {
        if (auth.estado.value != EstadoSesion.ACTIVA || bloqueada.value) return true
        if (db.syncDao().observarPendientes().first() == 0) return true
        return candado.withLock { ejecutar(manual = false) }
    }

    suspend fun enviarAntesDeCerrarSesion(): Boolean {
        if (!red.hayInternet()) return false
        return candado.withLock { ejecutar(manual = false) }
    }

    private suspend fun sincronizar(manual: Boolean) {
        if (!candado.tryLock()) return // ya hay una en curso
        try {
            ejecutar(manual)
        } finally {
            candado.unlock()
        }
    }

    /** Llamar con el candado tomado. */
    private suspend fun ejecutar(manual: Boolean): Boolean {
        if (bloqueada.value) return false
        try {
            sincronizando.value = true
            val resultado = runCatching { sincronizador.enviarPendientes() }
            sincronizando.value = false
            if (resultado.isSuccess) {
                val ahora = reloj.ahora()
                prefs.edit().putLong(CLAVE_ULTIMA, ahora).apply()
                _ultima.value = ahora
                if (manual) _mensajes.value = Mensajes.SINCRONIZACION_EXITOSA
                return true
            } else {
                // Lo no confirmado sigue pendiente. Si fue por la red, se avisa; si no, se reintenta más tarde.
                if (manual && !red.hayInternet()) _mensajes.value = Mensajes.SYNC_SIN_INTERNET
                alcance.launch { delay(REINTENTO_MS); reintentar() }
                return false
            }
        } finally {
            sincronizando.value = false
        }
    }

    private suspend fun reintentar() {
        val accion = ReglaSincronizacion.accionAutomatica(
            red.tipoActual(), pendientes.value, auth.estado.value == EstadoSesion.ACTIVA && !bloqueada.value, rechazoDatosMoviles,
        )
        if (accion == AccionAutomatica.SINCRONIZAR) sincronizar(manual = false)
    }

    companion object {
        /** Preferencias compartidas con el aviso de falta de respaldo (RF 7.6). */
        const val ARCHIVO = "mis_ventas_sync"
        const val CLAVE_ULTIMA = "ultima_sincronizacion"
        private const val REINTENTO_MS = 60_000L
    }
}
