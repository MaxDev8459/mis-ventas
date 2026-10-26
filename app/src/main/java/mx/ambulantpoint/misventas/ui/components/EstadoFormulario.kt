package mx.ambulantpoint.misventas.ui.components

import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.domain.regla.FallaCampos
import mx.ambulantpoint.misventas.domain.regla.FallaMensaje

/**
 * Reparte el resultado de un caso de uso: alertas por campo a la pantalla y mensajes generales al
 * notificador. Devuelve el valor si fue éxito.
 */
fun <T> Resultado<T>.atender(
    notificador: Notificador,
    alErroresDeCampo: (Map<String, String>) -> Unit,
): T? = when (this) {
    is Resultado.Exito -> { alErroresDeCampo(emptyMap()); valor }
    is Resultado.Fallo -> {
        when (val f = falla) {
            is FallaCampos -> alErroresDeCampo(f.errores)
            is FallaMensaje -> { alErroresDeCampo(emptyMap()); notificador.mostrar(f.mensaje) }
            else -> notificador.mostrar(f.toString())
        }
        null
    }
}
