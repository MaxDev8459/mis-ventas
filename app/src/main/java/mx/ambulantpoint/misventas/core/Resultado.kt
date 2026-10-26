package mx.ambulantpoint.misventas.core

/** Cualquier motivo por el que una operación no se completó (datos, validación, etc.). */
interface Falla

/** Resultado de una operación que puede fallar por una regla del negocio. */
sealed interface Resultado<out T> {
    data class Exito<out T>(val valor: T) : Resultado<T>
    data class Fallo(val falla: Falla) : Resultado<Nothing>
}

inline fun <T, R> Resultado<T>.mapear(transformar: (T) -> R): Resultado<R> = when (this) {
    is Resultado.Exito -> Resultado.Exito(transformar(valor))
    is Resultado.Fallo -> this
}
