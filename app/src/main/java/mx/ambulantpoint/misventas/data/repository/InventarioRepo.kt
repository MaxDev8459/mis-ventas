package mx.ambulantpoint.misventas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.ambulantpoint.misventas.domain.regla.ReglaExistenciaBaja
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.dao.ProductoVendido
import mx.ambulantpoint.misventas.data.local.dao.ResumenPeriodo
import mx.ambulantpoint.misventas.data.local.dao.TotalCategoria
import mx.ambulantpoint.misventas.data.local.dao.TotalFormaPago
import mx.ambulantpoint.misventas.data.local.dao.TotalPorDia
import mx.ambulantpoint.misventas.data.local.dao.VentasRecientes
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.MovimientoInventario
import mx.ambulantpoint.misventas.data.local.entity.TipoMovimiento
import mx.ambulantpoint.misventas.domain.puertos.PuertoInventario
import mx.ambulantpoint.misventas.domain.puertos.PuertoVentasRecientes

/** RF 4.1 – 4.4. Cada cambio de existencia deja su movimiento en la misma transacción. */
class InventarioRepo(private val db: MisVentasDb, private val reloj: Reloj) : PuertoInventario {
    private val productoDao = db.productoDao()
    private val movimientoDao = db.movimientoDao()

    fun observarMovimientos(productoId: String, desde: Long = 0, hasta: Long = Long.MAX_VALUE): Flow<List<MovimientoInventario>> =
        movimientoDao.observarPorProducto(productoId, desde, hasta)

    /** RF 4.1. Devuelve la nueva existencia. */
    override suspend fun registrarEntrada(productoId: String, cantidadMilesimas: Long): Resultado<Long> = db.transaccion {
        val producto = productoDao.porId(productoId) ?: fallar(FallaDatos.NoEncontrado)
        if (cantidadMilesimas <= 0 || !cantidadValida(producto.tipoVenta, cantidadMilesimas)) {
            fallar(FallaDatos.CantidadInvalida)
        }
        val ahora = reloj.ahora()
        productoDao.sumarExistencia(productoId, cantidadMilesimas, ahora)
        val resultante = producto.existenciaMilesimas + cantidadMilesimas
        movimientoDao.insertar(
            MovimientoInventario(
                id = nuevoId(), productoId = productoId, fechaHora = ahora, tipo = TipoMovimiento.ENTRADA,
                cantidadMilesimas = cantidadMilesimas, existenciaResultanteMilesimas = resultante, creadoEn = ahora,
            ),
        )
        resultante
    }

    /** RF 4.2. Una disminución no puede dejar la existencia debajo de cero. Devuelve la nueva existencia. */
    override suspend fun ajustar(
        productoId: String,
        aumentar: Boolean,
        cantidadMilesimas: Long,
        motivo: MotivoAjuste,
        especificacion: String?,
    ): Resultado<Long> = db.transaccion {
        val producto = productoDao.porId(productoId) ?: fallar(FallaDatos.NoEncontrado)
        if (cantidadMilesimas <= 0 || !cantidadValida(producto.tipoVenta, cantidadMilesimas)) {
            fallar(FallaDatos.CantidadInvalida)
        }
        val ahora = reloj.ahora()
        val resultante = if (aumentar) {
            productoDao.sumarExistencia(productoId, cantidadMilesimas, ahora)
            producto.existenciaMilesimas + cantidadMilesimas
        } else {
            if (productoDao.descontarExistencia(productoId, cantidadMilesimas, ahora) == 0) {
                fallar(FallaDatos.DisminucionMayorAExistencia)
            }
            producto.existenciaMilesimas - cantidadMilesimas
        }
        movimientoDao.insertar(
            MovimientoInventario(
                id = nuevoId(), productoId = productoId, fechaHora = ahora, tipo = TipoMovimiento.AJUSTE,
                cantidadMilesimas = if (aumentar) cantidadMilesimas else -cantidadMilesimas,
                existenciaResultanteMilesimas = resultante,
                motivo = motivo,
                especificacionMotivo = especificacion?.trim()?.takeIf { motivo == MotivoAjuste.OTRO && it.isNotEmpty() },
                creadoEn = ahora,
            ),
        )
        resultante
    }
}

/** Módulo 6 y RF 4.5: lectura de agregados; los cálculos (promedios, porcentajes) son del dominio. */
class ReporteRepo(db: MisVentasDb) : PuertoVentasRecientes {
    private val dao = db.reporteDao()

    suspend fun resumenPeriodo(desde: Long, hasta: Long): ResumenPeriodo = dao.resumenPeriodo(desde, hasta)
    suspend fun totalesPorDia(desde: Long, hasta: Long): List<TotalPorDia> = dao.totalesPorDia(desde, hasta)
    suspend fun totalesPorFormaPago(desde: Long, hasta: Long): List<TotalFormaPago> = dao.totalesPorFormaPago(desde, hasta)
    suspend fun productosVendidos(desde: Long, hasta: Long): List<ProductoVendido> = dao.productosVendidos(desde, hasta)
    suspend fun totalesPorCategoria(desde: Long, hasta: Long): List<TotalCategoria> = dao.totalesPorCategoria(desde, hasta)
    override suspend fun ventasRecientes(productoId: String): VentasRecientes = dao.ventasRecientes(productoId)

    /** productoId → ventas de sus últimos (hasta 7) días con ventas. */
    fun observarVentasRecientesDeTodos(): Flow<Map<String, VentasRecientes>> = dao.observarVentasPorDia().map { filas ->
        filas.groupBy { it.productoId }.mapValues { (_, dias) ->
            val ultimos = dias.take(ReglaExistenciaBaja.DIAS)
            VentasRecientes(ultimos.size, ultimos.sumOf { it.cantidadMilesimas })
        }
    }
}
