package mx.ambulantpoint.misventas.core

import java.util.UUID

/** Fuente de la hora actual (epoch ms). Se reemplaza en pruebas para controlar fechas. */
fun interface Reloj {
    fun ahora(): Long
}

object RelojSistema : Reloj {
    override fun ahora(): Long = System.currentTimeMillis()
}

/** Identificador único de cada registro (UUID), estable al sincronizar. */
fun nuevoId(): String = UUID.randomUUID().toString()
