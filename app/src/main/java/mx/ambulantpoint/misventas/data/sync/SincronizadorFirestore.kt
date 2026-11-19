package mx.ambulantpoint.misventas.data.sync

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.dao.SyncDao

/**
 * RF 7.1 — Envía a Firestore los registros pendientes.
 *
 * - **Idempotente:** cada registro va a `usuarios/{uid}/{coleccion}/{id local}` con `set`. Si el envío
 *   se interrumpe y se repite, se sobrescribe el mismo documento: no hay duplicados.
 * - **Sin perder cambios:** un registro se marca como sincronizado solo si su versión
 *   (`actualizadoEn`, o `creadoEn` en los que no cambian) es la misma que se envió. Si el vendedor
 *   lo modificó mientras se subía, sigue pendiente y se envía en la vuelta siguiente.
 * - Si algo falla, lo que no se confirmó sigue pendiente y se reintenta después (lanza la excepción).
 */
class SincronizadorFirestore(
    private val db: MisVentasDb,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val dao: SyncDao = db.syncDao()

    /** Un registro listo para enviar y la condición para marcarlo después. */
    private class Envio(val idDocumento: String, val datos: Map<String, Any?>, val donde: String, val argumentos: Array<Any>)

    /** Una tabla de Room que se respalda en una colección. */
    private class Tabla(val tabla: String, val coleccion: String, val leer: suspend (Int) -> List<Envio>)

    private fun porId(id: String, version: Long) = arrayOf<Any>(id, version)
    private val condicionId = "id = ? AND %s = ?"

    private val tablas = listOf(
        Tabla("perfil", Colecciones.PERFIL) { n ->
            dao.perfiles(n).map { Envio(it.id, it.aDocumento(), condicionId.format("actualizadoEn"), porId(it.id, it.actualizadoEn)) }
        },
        Tabla("configuracion", Colecciones.CONFIGURACION) { n ->
            dao.configuraciones(n).map { Envio(it.id, it.aDocumento(), condicionId.format("actualizadoEn"), porId(it.id, it.actualizadoEn)) }
        },
        Tabla("categoria", Colecciones.CATEGORIA) { n ->
            dao.categorias(n).map { Envio(it.id, it.aDocumento(), condicionId.format("actualizadoEn"), porId(it.id, it.actualizadoEn)) }
        },
        Tabla("forma_pago", Colecciones.FORMA_PAGO) { n ->
            dao.formasPago(n).map { Envio(it.id, it.aDocumento(), condicionId.format("actualizadoEn"), porId(it.id, it.actualizadoEn)) }
        },
        Tabla("producto", Colecciones.PRODUCTO) { n ->
            dao.productos(n).map { Envio(it.id, it.aDocumento(), condicionId.format("actualizadoEn"), porId(it.id, it.actualizadoEn)) }
        },
        Tabla("caja", Colecciones.CAJA) { n ->
            dao.cajas(n).map { Envio(it.id, it.aDocumento(), condicionId.format("actualizadoEn"), porId(it.id, it.actualizadoEn)) }
        },
        Tabla("corte_forma_pago", Colecciones.CORTE_FORMA_PAGO) { n ->
            dao.cortesFormaPago(n).map {
                Envio(it.idDocumento(), it.aDocumento(), "cajaId = ? AND formaPagoId = ? AND creadoEn = ?", arrayOf(it.cajaId, it.formaPagoId, it.creadoEn))
            }
        },
        Tabla("venta", Colecciones.VENTA) { n ->
            dao.ventas(n).map { Envio(it.id, it.aDocumento(), condicionId.format("actualizadoEn"), porId(it.id, it.actualizadoEn)) }
        },
        Tabla("venta_detalle", Colecciones.VENTA_DETALLE) { n ->
            dao.ventaDetalles(n).map { Envio(it.id, it.aDocumento(), condicionId.format("creadoEn"), porId(it.id, it.creadoEn)) }
        },
        Tabla("movimiento_inventario", Colecciones.MOVIMIENTO) { n ->
            dao.movimientos(n).map { Envio(it.id, it.aDocumento(), condicionId.format("creadoEn"), porId(it.id, it.creadoEn)) }
        },
    )

    /** Envía todo lo pendiente. Devuelve cuántos registros se confirmaron. Requiere sesión iniciada. */
    suspend fun enviarPendientes(): Int {
        val uid = auth.currentUser?.uid ?: error("Sin sesión de Firebase")
        val raiz = firestore.collection("usuarios").document(uid)
        var enviados = 0

        for (t in tablas) {
            val coleccion = raiz.collection(t.coleccion)
            // Tope de vueltas por si un registro cambia sin parar mientras se sube.
            var vueltas = 0
            while (vueltas++ < MAX_LOTES) {
                val lote = t.leer(LOTE)
                if (lote.isEmpty()) break
                enviar(coleccion, lote)
                enviados += marcarSincronizados(t.tabla, lote)
            }
        }

        // RF 2.9: categorías eliminadas que ya estaban en la nube.
        var vueltas = 0
        while (vueltas++ < MAX_LOTES) {
            val lote = dao.eliminaciones(LOTE)
            if (lote.isEmpty()) break
            val batch = firestore.batch()
            lote.forEach { batch.delete(raiz.collection(it.coleccion).document(it.docId)) }
            withTimeout(ESPERA_MS) { batch.commit().await() }
            lote.forEach { dao.quitarEliminacion(it.coleccion, it.docId) }
            enviados += lote.size
        }
        return enviados
    }

    private suspend fun enviar(coleccion: CollectionReference, lote: List<Envio>) {
        val batch = firestore.batch()
        lote.forEach { batch.set(coleccion.document(it.idDocumento), it.datos) }
        // Sin confirmación del servidor no se marca nada: si vence el tiempo, todo sigue pendiente.
        withTimeout(ESPERA_MS) { batch.commit().await() }
    }

    /** Marca como sincronizados los que no cambiaron mientras se enviaban. */
    private suspend fun marcarSincronizados(tabla: String, lote: List<Envio>): Int {
        var marcados = 0
        // withTransaction avisa a Room del cambio, así el indicador de pendientes se actualiza.
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            lote.forEach { e ->
                val cambiados = sql.compileStatement("UPDATE $tabla SET pendienteSync = 0 WHERE ${e.donde}").use { st ->
                    e.argumentos.forEachIndexed { i, a ->
                        when (a) {
                            is String -> st.bindString(i + 1, a)
                            is Long -> st.bindLong(i + 1, a)
                            else -> st.bindString(i + 1, a.toString())
                        }
                    }
                    st.executeUpdateDelete()
                }
                marcados += cambiados
            }
        }
        return marcados
    }

    private companion object {
        /** Firestore admite hasta 500 escrituras por lote. */
        const val LOTE = 400
        const val MAX_LOTES = 1_000
        const val ESPERA_MS = 60_000L
    }
}
