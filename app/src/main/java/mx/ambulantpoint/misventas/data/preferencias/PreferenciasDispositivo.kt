package mx.ambulantpoint.misventas.data.preferencias

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.ambulantpoint.misventas.domain.regla.TiempoReverificacion

/** Tema de la app. [SISTEMA] sigue el modo claro/oscuro del teléfono. */
enum class TemaApp(val etiqueta: String) {
    SISTEMA("Como el teléfono"),
    CLARO("Claro"),
    OSCURO("Oscuro"),
}

/**
 * Ajustes de este teléfono (no de la cuenta): no van a Room ni a Firebase, así que no cambian el
 * esquema ni el respaldo. Al recuperar la cuenta en otro teléfono vuelven a sus valores por defecto.
 */
class PreferenciasDispositivo(contexto: Context) {
    private val prefs = contexto.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    private val _tema = MutableStateFlow(leer(CLAVE_TEMA, TemaApp.SISTEMA))
    val tema: StateFlow<TemaApp> = _tema.asStateFlow()

    private val _reverificacion = MutableStateFlow(leer(CLAVE_REVERIFICACION, TiempoReverificacion.NUNCA))
    val reverificacion: StateFlow<TiempoReverificacion> = _reverificacion.asStateFlow()

    fun cambiarTema(t: TemaApp) {
        prefs.edit().putString(CLAVE_TEMA, t.name).apply()
        _tema.value = t
    }

    fun cambiarReverificacion(t: TiempoReverificacion) {
        prefs.edit().putString(CLAVE_REVERIFICACION, t.name).apply()
        _reverificacion.value = t
    }

    private inline fun <reified E : Enum<E>> leer(clave: String, porDefecto: E): E =
        prefs.getString(clave, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: porDefecto

    private companion object {
        const val ARCHIVO = "mis_ventas_preferencias"
        const val CLAVE_TEMA = "tema"
        const val CLAVE_REVERIFICACION = "reverificacion"
    }
}
