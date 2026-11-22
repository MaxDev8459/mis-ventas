package mx.ambulantpoint.misventas.data.repository

import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.normalizarNombre
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.entity.Precarga
import mx.ambulantpoint.misventas.domain.regla.ReglaPrecarga

/** Precargas de la carga del día (se respaldan en Firebase; no se borran, se desactivan). */
class PrecargaRepo(private val db: MisVentasDb, private val reloj: Reloj) {
    private val dao = db.precargaDao()

    /** Activas, de la más usada a la menos usada. */
    fun observarActivas(): Flow<List<Precarga>> = dao.observarActivas()

    /**
     * "Guardar también como precarga": si ya hay una con ese nombre (aunque se haya quitado), se
     * actualizan sus cantidades y vuelve a estar activa; si no, se crea.
     */
    suspend fun guardarComo(nombre: String, cantidades: Map<String, Long>): Resultado<Precarga> = db.transaccion {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) fallar(FallaDatos.CantidadInvalida)
        val ahora = reloj.ahora()
        val previa = dao.porNombre(normalizarNombre(limpio))
        val p = previa?.copy(
            nombre = limpio, cantidades = ReglaPrecarga.codificar(cantidades), activa = true,
            actualizadoEn = ahora, pendienteSync = true,
        ) ?: Precarga(
            id = nuevoId(), nombre = limpio, nombreNormalizado = normalizarNombre(limpio),
            cantidades = ReglaPrecarga.codificar(cantidades), creadoEn = ahora, actualizadoEn = ahora,
        )
        dao.guardar(p)
        p
    }

    /** Se usó al capturar la carga: sube en el orden de "más usadas". */
    suspend fun marcarUso(id: String): Resultado<Unit> = db.transaccion {
        val p = dao.porId(id) ?: fallar(FallaDatos.NoEncontrado)
        val ahora = reloj.ahora()
        dao.guardar(p.copy(vecesUsada = p.vecesUsada + 1, ultimoUso = ahora, actualizadoEn = ahora, pendienteSync = true))
    }

    suspend fun renombrar(id: String, nombre: String): Resultado<Unit> = db.transaccion {
        val limpio = nombre.trim()
        if (limpio.isEmpty()) fallar(FallaDatos.CantidadInvalida)
        val p = dao.porId(id) ?: fallar(FallaDatos.NoEncontrado)
        val otra = dao.porNombre(normalizarNombre(limpio))
        if (otra != null && otra.id != id) fallar(FallaDatos.NombreDuplicado)
        dao.guardar(p.copy(nombre = limpio, nombreNormalizado = normalizarNombre(limpio), actualizadoEn = reloj.ahora(), pendienteSync = true))
    }

    /** "Quitar": se desactiva (no se borra). */
    suspend fun quitar(id: String): Resultado<Unit> = db.transaccion {
        val p = dao.porId(id) ?: fallar(FallaDatos.NoEncontrado)
        dao.guardar(p.copy(activa = false, actualizadoEn = reloj.ahora(), pendienteSync = true))
    }
}
