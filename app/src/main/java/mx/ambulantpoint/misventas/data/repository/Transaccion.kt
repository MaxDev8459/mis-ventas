package mx.ambulantpoint.misventas.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.MisVentasDb

/**
 * Ejecuta [bloque] como una sola transacción: si algo falla, no se guarda nada (RNF Confiabilidad).
 * Las fallas de negocio se lanzan con [fallar] y salen como [Resultado.Fallo].
 */
internal suspend fun <T> MisVentasDb.transaccion(bloque: suspend () -> T): Resultado<T> =
    try {
        Resultado.Exito(withTransaction { bloque() })
    } catch (e: FallaDatosException) {
        Resultado.Fallo(e.falla)
    } catch (e: SQLiteConstraintException) {
        // Los índices únicos de nombre son la última defensa contra duplicados.
        if (e.message.orEmpty().contains("UNIQUE", ignoreCase = true)) {
            Resultado.Fallo(FallaDatos.NombreDuplicado)
        } else {
            throw e
        }
    }

internal fun fallar(falla: FallaDatos): Nothing = throw FallaDatosException(falla)
