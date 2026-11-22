package mx.ambulantpoint.misventas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.entity.CargaCaja
import mx.ambulantpoint.misventas.data.local.entity.EstadoCaja
import mx.ambulantpoint.misventas.domain.regla.RenglonCarga

/**
 * Carga del día. No toca la existencia del inventario: solo guarda cuánto lleva el vendedor de cada
 * producto en la caja abierta.
 */
class CargaRepo(private val db: MisVentasDb, private val reloj: Reloj) {
    private val dao = db.cargaDao()

    /**
     * Resumen de la carga de una caja: cargado, vendido y mermas por producto. [hasta] = cierre de la
     * caja (o sin límite si sigue abierta). Solo productos con carga mayor a cero.
     */
    fun observarResumen(cajaId: String, desde: Long, hasta: Long = Long.MAX_VALUE): Flow<List<RenglonCarga>> =
        combine(dao.observarCarga(cajaId), dao.observarVendido(cajaId), dao.observarMermas(desde, hasta)) { carga, vendido, mermas ->
            val v = vendido.associate { it.productoId to it.cantidadMilesimas }
            val m = mermas.associate { it.productoId to it.cantidadMilesimas }
            carga.filter { it.cantidadMilesimas > 0 }.map {
                RenglonCarga(it.productoId, it.nombre, it.tipoVenta, it.cantidadMilesimas, v[it.productoId] ?: 0, m[it.productoId] ?: 0)
            }
        }

    /** Lo cargado hoy por producto (incluye ceros), para llenar la pantalla de captura. */
    suspend fun cargaActual(cajaId: String): Map<String, Long> = dao.carga(cajaId).associate { it.productoId to it.cantidadMilesimas }

    /** Límite de carga por producto: existencia de hoy + vendido y mermas de la caja (lo que había al abrir). */
    suspend fun disponibles(cajaId: String, abiertaEn: Long, existencias: Map<String, Long>): Map<String, Long> =
        existencias.mapValues { (id, existencia) -> existencia + dao.vendidoDe(cajaId, id) + dao.mermasDe(id, abiertaEn) }

    /** "Usar carga anterior": la de la caja más reciente que tuvo carga. */
    suspend fun cargaAnterior(cajaActual: String): Map<String, Long> =
        dao.cargaAnterior(cajaActual).associate { it.productoId to it.cantidadMilesimas }

    /**
     * Guarda la carga de la caja abierta (reemplaza la cantidad de cada producto capturado). No deja
     * cargar más que la existencia.
     */
    suspend fun guardar(cajaId: String, cantidades: Map<String, Long>): Resultado<Unit> = db.transaccion {
        val caja = db.cajaDao().abierta()
        if (caja == null || caja.id != cajaId || caja.estado != EstadoCaja.ABIERTA) fallar(FallaDatos.SinCajaAbierta)
        val ahora = reloj.ahora()
        val actuales = dao.carga(cajaId).associateBy { it.productoId }
        val filas = cantidades.mapNotNull { (productoId, cantidad) ->
            val previa = actuales[productoId]
            // Cero en un producto que nunca se cargó: no hace falta guardar nada.
            if (previa == null && cantidad == 0L) return@mapNotNull null
            if (previa != null && previa.cantidadMilesimas == cantidad) return@mapNotNull null
            validarExistencia(caja.id, caja.abiertaEn, productoId, cantidad)
            CargaCaja(cajaId, productoId, cantidad, previa?.creadoEn ?: ahora, ahora)
        }
        if (filas.isNotEmpty()) dao.guardar(filas)
    }

    /** "Agregar a la carga" desde Vender: suma a lo cargado del producto. */
    suspend fun agregar(cajaId: String, productoId: String, cantidadMilesimas: Long): Resultado<Long> = db.transaccion {
        if (cantidadMilesimas <= 0) fallar(FallaDatos.CantidadInvalida)
        val caja = db.cajaDao().abierta()
        if (caja == null || caja.id != cajaId) fallar(FallaDatos.SinCajaAbierta)
        val ahora = reloj.ahora()
        val previa = dao.renglon(cajaId, productoId)
        val nueva = (previa?.cantidadMilesimas ?: 0) + cantidadMilesimas
        validarExistencia(caja.id, caja.abiertaEn, productoId, nueva)
        dao.guardar(listOf(CargaCaja(cajaId, productoId, nueva, previa?.creadoEn ?: ahora, ahora)))
        nueva
    }

    /**
     * Lo cargado no puede pasar de lo que había al abrir: existencia de hoy + lo vendido y las mermas
     * de esta caja (al vender, la existencia ya bajó).
     */
    private suspend fun validarExistencia(cajaId: String, abiertaEn: Long, productoId: String, cargadoMilesimas: Long) {
        val p = db.productoDao().porId(productoId) ?: fallar(FallaDatos.NoEncontrado)
        val disponible = p.existenciaMilesimas + dao.vendidoDe(cajaId, productoId) + dao.mermasDe(productoId, abiertaEn)
        if (cargadoMilesimas > disponible) {
            fallar(FallaDatos.CargaExcedeExistencia("Solo hay ${Formato.cantidad(disponible, p.tipoVenta)} de ${p.nombre}"))
        }
    }
}
