package mx.ambulantpoint.misventas.data.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.auth.AuthRepoFirebase
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.domain.puertos.EstadoSesion
import mx.ambulantpoint.misventas.domain.regla.AccionSesion
import mx.ambulantpoint.misventas.domain.regla.ReglaSesionUnica

/**
 * RF 7.5 — La cuenta solo está activa en un teléfono.
 *
 * Mientras la app está abierta con sesión activa, escucha `usuarios/{uid}/sesion/dispositivo`.
 * Si otro teléfono inició sesión (lo registra [ServicioRecuperacion]), este envía primero sus
 * registros pendientes (aunque sea con datos móviles) y después cierra la sesión y avisa
 * "Su sesión se inició en otro dispositivo". Sin conexión no pasa nada: Firestore entrega el cambio
 * en cuanto la hay. Solo se actúa con datos confirmados por el servidor, no con la copia local.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlSesionUnica(
    private val db: MisVentasDb,
    private val auth: AuthRepoFirebase,
    private val sincronizacion: ServicioSincronizacion,
    private val recuperacion: ServicioRecuperacion,
    private val firebase: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cierre: Job? = null

    private val _mensaje = MutableStateFlow<String?>(null)
    /** "Su sesión se inició en otro dispositivo" (se muestra sobre la pantalla de inicio de sesión). */
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()
    fun mensajeMostrado() { _mensaje.value = null }

    init {
        alcance.launch {
            // Con una recuperación pendiente este teléfono aún no reclama la sesión: no se escucha.
            combine(auth.estado, recuperacion.requerida) { s, r -> s == EstadoSesion.ACTIVA && !r }
                .flatMapLatest { activa -> firebase.currentUser?.uid?.takeIf { activa }?.let(::observar) ?: emptyFlow() }
                .collect { doc -> atender(doc) }
        }
    }

    private fun observar(uid: String): Flow<DocumentSnapshot> = callbackFlow {
        val registro = recuperacion.documentoSesion(uid).addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
            if (doc != null && error == null) trySend(doc)
        }
        awaitClose { registro.remove() }
    }

    private suspend fun atender(doc: DocumentSnapshot) {
        val accion = ReglaSesionUnica.accion(
            dispositivoNube = doc.getString(ServicioRecuperacion.CAMPO_DISPOSITIVO),
            miDispositivo = recuperacion.miDispositivo,
            desdeServidor = !doc.metadata.isFromCache,
            pendientes = pendientes(),
        )
        when (accion) {
            AccionSesion.NADA -> Unit
            // Cuenta sin dispositivo registrado (ej. recién registrada): este teléfono la reclama.
            AccionSesion.RECLAMAR -> firebase.currentUser?.uid?.let { runCatching { recuperacion.reclamarSesion(it) } }
            AccionSesion.ENVIAR_PENDIENTES, AccionSesion.CERRAR_SESION -> cerrarCuandoSePueda()
        }
    }

    private fun cerrarCuandoSePueda() {
        if (cierre?.isActive == true) return
        cierre = alcance.launch {
            while (auth.estado.value == EstadoSesion.ACTIVA) {
                if (pendientes() > 0 && !sincronizacion.enviarAntesDeCerrarSesion()) {
                    delay(REINTENTO_MS) // sin conexión o falló el envío: se reintenta
                    continue
                }
                // Puede haberse registrado algo mientras se enviaba: se vuelve a revisar.
                if (pendientes() > 0) { delay(1_000); continue }
                auth.cerrarSesionPorOtroDispositivo()
                _mensaje.value = Mensajes.SESION_EN_OTRO_DISPOSITIVO
            }
        }
    }

    private suspend fun pendientes(): Int = db.syncDao().observarPendientes().first()

    private companion object {
        const val REINTENTO_MS = 30_000L
    }
}
