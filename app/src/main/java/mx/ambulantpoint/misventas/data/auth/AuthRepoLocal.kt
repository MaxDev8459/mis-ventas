package mx.ambulantpoint.misventas.data.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.domain.puertos.DatosRegistro
import mx.ambulantpoint.misventas.domain.puertos.EstadoSesion
import mx.ambulantpoint.misventas.domain.puertos.PuertoAuth
import java.util.Locale

/**
 * Autenticación del MVP 100 % local (decisión 26-oct-2026). La cuenta vive en el teléfono:
 * perfil en Room y credencial (hash PBKDF2) en preferencias privadas de la app.
 * En Sprint 2 se reemplaza por AuthRepoFirebase con la misma interfaz [PuertoAuth].
 */
class AuthRepoLocal(
    contexto: Context,
    private val db: MisVentasDb,
    private val reloj: Reloj,
) : PuertoAuth {

    private val prefs: SharedPreferences =
        contexto.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    private val _estado = MutableStateFlow(leerEstado())
    override val estado: StateFlow<EstadoSesion> = _estado.asStateFlow()

    override suspend fun registrar(datos: DatosRegistro): Resultado<Perfil> = withContext(Dispatchers.IO) {
        val correo = normalizarCorreo(datos.correo)
        val existente = prefs.getString(CLAVE_CORREO, null)
        if (existente != null) {
            return@withContext Resultado.Fallo(
                if (existente == correo) FallaDatos.CorreoYaRegistrado else FallaDatos.YaHayOtraCuenta,
            )
        }
        val ahora = reloj.ahora()
        val perfil = Perfil(
            id = nuevoId(),
            nombres = datos.nombres.trim(),
            apellidoPaterno = datos.apellidoPaterno.trim(),
            apellidoMaterno = datos.apellidoMaterno.trim(),
            telefono = datos.telefono.trim(),
            nombreNegocio = datos.nombreNegocio.trim(),
            correo = correo,
            creadoEn = ahora,
            actualizadoEn = ahora,
        )
        db.perfilDao().guardar(perfil)
        // commit() síncrono: si falla, la cuenta no queda a medias en preferencias.
        val ok = prefs.edit()
            .putString(CLAVE_CORREO, correo)
            .putString(CLAVE_HASH, Hasheador.hashear(datos.contrasena))
            .putBoolean(CLAVE_SESION, true)
            .commit()
        if (!ok) return@withContext Resultado.Fallo(FallaDatos.NoEncontrado)
        _estado.value = EstadoSesion.ACTIVA
        Resultado.Exito(perfil)
    }

    override suspend fun iniciarSesion(correo: String, contrasena: String): Resultado<Unit> = withContext(Dispatchers.IO) {
        val guardado = prefs.getString(CLAVE_CORREO, null)
        val hash = prefs.getString(CLAVE_HASH, null)
        if (guardado == null || hash == null || guardado != normalizarCorreo(correo) || !Hasheador.verificar(contrasena, hash)) {
            return@withContext Resultado.Fallo(FallaDatos.CredencialesIncorrectas)
        }
        prefs.edit().putBoolean(CLAVE_SESION, true).commit()
        _estado.value = EstadoSesion.ACTIVA
        Resultado.Exito(Unit)
    }

    /** RF 1.3: no cierra si hay registros pendientes de sincronizar. */
    override suspend fun cerrarSesion(): Resultado<Unit> = withContext(Dispatchers.IO) {
        if (db.syncDao().observarPendientes().first() > 0) {
            return@withContext Resultado.Fallo(FallaDatos.PendientesDeSincronizar)
        }
        prefs.edit().putBoolean(CLAVE_SESION, false).commit()
        _estado.value = EstadoSesion.SIN_SESION
        Resultado.Exito(Unit)
    }

    private fun leerEstado(): EstadoSesion = when {
        prefs.getString(CLAVE_CORREO, null) == null -> EstadoSesion.SIN_CUENTA
        prefs.getBoolean(CLAVE_SESION, false) -> EstadoSesion.ACTIVA
        else -> EstadoSesion.SIN_SESION
    }

    private fun normalizarCorreo(correo: String) = correo.trim().lowercase(Locale.ROOT)

    private companion object {
        const val ARCHIVO = "mis_ventas_cuenta"
        const val CLAVE_CORREO = "correo"
        const val CLAVE_HASH = "hash_contrasena"
        const val CLAVE_SESION = "sesion_activa"
    }
}
