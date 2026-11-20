package mx.ambulantpoint.misventas.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.data.red.Conectividad
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.domain.puertos.DatosRegistro
import mx.ambulantpoint.misventas.domain.puertos.EstadoSesion
import mx.ambulantpoint.misventas.domain.puertos.PuertoAuth
import java.util.Locale

/**
 * RF 1.1 – 1.4 con Firebase Authentication. La contraseña no se guarda en el teléfono: la resguarda
 * Firebase. La sesión la conserva Firebase aunque se cierre la app o no haya internet (RF 1.2).
 *
 * El perfil se guarda en Room (una cuenta por dispositivo); se sube a Firestore con la
 * sincronización (RF 7.1). En preferencias solo queda el correo, para saber si el teléfono ya tiene
 * cuenta (abrir en "Iniciar sesión" en vez de "Registro") sin consultar la base en el hilo principal.
 */
class AuthRepoFirebase(
    contexto: Context,
    private val db: MisVentasDb,
    private val reloj: Reloj,
    private val red: Conectividad,
    /** RF 7.4: se llama tras iniciar sesión, antes de marcarla activa (deja la recuperación requerida). */
    private val alIniciarSesion: () -> Unit = {},
    private val firebase: FirebaseAuth = FirebaseAuth.getInstance(),
) : PuertoAuth {

    private val prefs: SharedPreferences =
        contexto.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    init {
        // RF 1.1: la contraseña no se almacena en el dispositivo. Se borra el hash que dejó el MVP local.
        if (prefs.contains(CLAVE_HASH_MVP) || prefs.contains(CLAVE_SESION_MVP)) {
            prefs.edit().remove(CLAVE_HASH_MVP).remove(CLAVE_SESION_MVP).apply()
        }
        firebase.setLanguageCode("es")
    }

    private val _estado = MutableStateFlow(leerEstado())
    override val estado: StateFlow<EstadoSesion> = _estado.asStateFlow()

    override suspend fun registrar(datos: DatosRegistro): Resultado<Perfil> = withContext(Dispatchers.IO) {
        if (!red.hayInternet()) return@withContext Resultado.Fallo(FallaDatos.SinInternet)
        val correo = normalizarCorreo(datos.correo)
        try {
            firebase.createUserWithEmailAndPassword(correo, datos.contrasena).await()
        } catch (e: Exception) {
            return@withContext fallaDe(e, porDefecto = FallaDatos.NoEncontrado)
        }
        val ahora = reloj.ahora()
        // Una cuenta por dispositivo: si ya había perfil (MVP local) se conserva su id y sus datos se reemplazan.
        val anterior = db.perfilDao().obtener()
        val perfil = Perfil(
            id = anterior?.id ?: nuevoId(),
            nombres = datos.nombres.trim(),
            apellidoPaterno = datos.apellidoPaterno.trim(),
            apellidoMaterno = datos.apellidoMaterno.trim(),
            telefono = datos.telefono.trim(),
            nombreNegocio = datos.nombreNegocio.trim(),
            correo = correo,
            creadoEn = anterior?.creadoEn ?: ahora,
            actualizadoEn = ahora,
        )
        try {
            db.perfilDao().guardar(perfil)
        } catch (e: Exception) {
            // Sin perfil la cuenta queda a medias: se borra en Firebase para que pueda registrarse de nuevo.
            runCatching { firebase.currentUser?.delete()?.await() }
            firebase.signOut()
            throw e
        }
        prefs.edit().putString(CLAVE_CORREO, correo).apply()
        _estado.value = EstadoSesion.ACTIVA
        Resultado.Exito(perfil)
    }

    override suspend fun iniciarSesion(correo: String, contrasena: String): Resultado<Unit> = withContext(Dispatchers.IO) {
        if (!red.hayInternet()) return@withContext Resultado.Fallo(FallaDatos.SinInternet)
        val normalizado = normalizarCorreo(correo)
        try {
            firebase.signInWithEmailAndPassword(normalizado, contrasena).await()
        } catch (e: Exception) {
            return@withContext fallaDe(e, porDefecto = FallaDatos.CredencialesIncorrectas)
        }
        prefs.edit().putString(CLAVE_CORREO, normalizado).apply()
        alIniciarSesion()
        _estado.value = EstadoSesion.ACTIVA
        Resultado.Exito(Unit)
    }

    /**
     * RF 7.5: la sesión se inició en otro teléfono y aquí ya no quedan pendientes. Cierra sin las
     * validaciones de RF 1.3 (las revisó quien llama).
     */
    fun cerrarSesionPorOtroDispositivo() {
        firebase.signOut()
        _estado.value = EstadoSesion.SIN_SESION
    }

    /** RF 1.3: no cierra si hay registros pendientes de sincronizar. */
    override suspend fun cerrarSesion(): Resultado<Unit> = withContext(Dispatchers.IO) {
        if (db.syncDao().observarPendientes().first() > 0) {
            return@withContext Resultado.Fallo(FallaDatos.PendientesDeSincronizar)
        }
        firebase.signOut()
        _estado.value = EstadoSesion.SIN_SESION
        Resultado.Exito(Unit)
    }

    /** RF 1.4. Si el correo no existe, Firebase no lo dice; el mensaje del DFR es el mismo. */
    override suspend fun recuperarContrasena(correo: String): Resultado<Unit> = withContext(Dispatchers.IO) {
        if (!red.hayInternet()) return@withContext Resultado.Fallo(FallaDatos.SinInternet)
        try {
            firebase.sendPasswordResetEmail(normalizarCorreo(correo)).await()
        } catch (e: FirebaseAuthInvalidUserException) {
            // Correo sin cuenta: se responde igual que si existiera.
        } catch (e: Exception) {
            return@withContext fallaDe(e, porDefecto = FallaDatos.NoEncontrado)
        }
        Resultado.Exito(Unit)
    }

    private fun leerEstado(): EstadoSesion = when {
        firebase.currentUser != null -> EstadoSesion.ACTIVA
        prefs.getString(CLAVE_CORREO, null) != null -> EstadoSesion.SIN_SESION
        else -> EstadoSesion.SIN_CUENTA
    }

    private fun fallaDe(e: Exception, porDefecto: FallaDatos): Resultado.Fallo {
        if (e is CancellationException) throw e
        return Resultado.Fallo(
            when (e) {
                is FirebaseNetworkException -> FallaDatos.SinInternet
                is FirebaseAuthUserCollisionException -> FallaDatos.CorreoYaRegistrado
                is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> FallaDatos.CredencialesIncorrectas
                else -> porDefecto
            },
        )
    }

    private fun normalizarCorreo(correo: String) = correo.trim().lowercase(Locale.ROOT)

    private companion object {
        /** El mismo archivo del MVP local: así un teléfono que ya tenía cuenta abre en "Iniciar sesión". */
        const val ARCHIVO = "mis_ventas_cuenta"
        const val CLAVE_CORREO = "correo"
        const val CLAVE_HASH_MVP = "hash_contrasena"
        const val CLAVE_SESION_MVP = "sesion_activa"
    }
}
