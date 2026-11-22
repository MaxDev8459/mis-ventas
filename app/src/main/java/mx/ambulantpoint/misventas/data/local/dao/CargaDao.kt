package mx.ambulantpoint.misventas.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.data.local.entity.CargaCaja
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta

/** Lo cargado de un producto, con su nombre y tipo de venta para mostrarlo. */
data class CargaConProducto(
    val productoId: String,
    val nombre: String,
    val tipoVenta: TipoVenta,
    val cantidadMilesimas: Long,
)

/** Cantidad por producto (vendido o mermas). */
data class CantidadPorProducto(val productoId: String, val cantidadMilesimas: Long)

/** Carga del día. */
@Dao
interface CargaDao {
    @Query(
        """
        SELECT k.productoId, p.nombre, p.tipoVenta, k.cantidadMilesimas
        FROM carga_caja k JOIN producto p ON p.id = k.productoId
        WHERE k.cajaId = :cajaId
        ORDER BY p.nombre COLLATE NOCASE
        """,
    )
    fun observarCarga(cajaId: String): Flow<List<CargaConProducto>>

    @Query("SELECT * FROM carga_caja WHERE cajaId = :cajaId")
    suspend fun carga(cajaId: String): List<CargaCaja>

    @Query("SELECT * FROM carga_caja WHERE cajaId = :cajaId AND productoId = :productoId")
    suspend fun renglon(cajaId: String, productoId: String): CargaCaja?

    @Upsert
    suspend fun guardar(filas: List<CargaCaja>)

    /** La carga de la caja anterior más reciente que tuvo carga (para "usar carga anterior"). */
    @Query(
        """
        SELECT * FROM carga_caja WHERE cantidadMilesimas > 0 AND cajaId = (
          SELECT c.id FROM caja c
          WHERE c.id != :cajaActual AND EXISTS (SELECT 1 FROM carga_caja k WHERE k.cajaId = c.id AND k.cantidadMilesimas > 0)
          ORDER BY c.abiertaEn DESC LIMIT 1
        )
        """,
    )
    suspend fun cargaAnterior(cajaActual: String): List<CargaCaja>

    /** Vendido por producto en la caja, sin ventas canceladas. El monto libre ("Varios", sin producto) no cuenta. */
    @Query(
        """
        SELECT d.productoId, SUM(d.cantidadMilesimas) AS cantidadMilesimas
        FROM venta_detalle d JOIN venta v ON v.id = d.ventaId
        WHERE v.cajaId = :cajaId AND v.estado = 'REGISTRADA' AND d.productoId IS NOT NULL
        GROUP BY d.productoId
        """,
    )
    fun observarVendido(cajaId: String): Flow<List<CantidadPorProducto>>

    /** Vendido de un producto en la caja (sin canceladas). */
    @Query(
        """
        SELECT COALESCE(SUM(d.cantidadMilesimas), 0) FROM venta_detalle d JOIN venta v ON v.id = d.ventaId
        WHERE v.cajaId = :cajaId AND v.estado = 'REGISTRADA' AND d.productoId = :productoId
        """,
    )
    suspend fun vendidoDe(cajaId: String, productoId: String): Long

    /** Mermas de un producto desde [desde]. */
    @Query(
        """
        SELECT COALESCE(SUM(-cantidadMilesimas), 0) FROM movimiento_inventario
        WHERE productoId = :productoId AND tipo = 'AJUSTE' AND motivo = 'MERMA' AND cantidadMilesimas < 0 AND fechaHora >= :desde
        """,
    )
    suspend fun mermasDe(productoId: String, desde: Long): Long

    /** Mermas (ajustes que bajan existencia con motivo Merma) registradas entre [desde] y [hasta]. */
    @Query(
        """
        SELECT productoId, SUM(-cantidadMilesimas) AS cantidadMilesimas
        FROM movimiento_inventario
        WHERE tipo = 'AJUSTE' AND motivo = 'MERMA' AND cantidadMilesimas < 0
          AND fechaHora >= :desde AND fechaHora <= :hasta
        GROUP BY productoId
        """,
    )
    fun observarMermas(desde: Long, hasta: Long): Flow<List<CantidadPorProducto>>
}
