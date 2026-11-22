package mx.ambulantpoint.misventas.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.data.local.entity.Apartado
import mx.ambulantpoint.misventas.data.local.entity.ApartadoDetalle

/** Un apartado con sus productos. */
data class ApartadoConDetalles(
    @Embedded val apartado: Apartado,
    @Relation(parentColumn = "id", entityColumn = "apartadoId")
    val detalles: List<ApartadoDetalle>,
)

/** Apartados (v8, `docs/10-ESPEC-APARTADOS.md`). */
@Dao
interface ApartadoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(apartado: Apartado)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarDetalles(detalles: List<ApartadoDetalle>)

    @Update
    suspend fun actualizar(apartado: Apartado)

    @Query("SELECT * FROM apartado WHERE id = :id")
    suspend fun porId(id: String): Apartado?

    @Query("SELECT * FROM apartado_detalle WHERE apartadoId = :apartadoId ORDER BY nombreProducto COLLATE NOCASE")
    suspend fun detalles(apartadoId: String): List<ApartadoDetalle>

    @Transaction
    @Query("SELECT * FROM apartado WHERE id = :id")
    fun observar(id: String): Flow<ApartadoConDetalles?>

    /** Pendientes por fecha y hora de entrega. */
    @Transaction
    @Query("SELECT * FROM apartado WHERE estado = 'PENDIENTE' ORDER BY entregaEn, nombre COLLATE NOCASE")
    fun observarPendientes(): Flow<List<ApartadoConDetalles>>

    @Transaction
    @Query("SELECT * FROM apartado WHERE estado = 'PENDIENTE' ORDER BY entregaEn")
    suspend fun pendientes(): List<ApartadoConDetalles>

    /** Entregados, cancelados y liberados, del más reciente al más antiguo. */
    @Transaction
    @Query("SELECT * FROM apartado WHERE estado <> 'PENDIENTE' ORDER BY terminadoEn DESC LIMIT 200")
    fun observarHistorial(): Flow<List<ApartadoConDetalles>>

    /** Lo apartado (pendiente) por producto: no se puede vender. */
    @Query(
        """
        SELECT d.productoId AS productoId, SUM(d.cantidadMilesimas) AS cantidadMilesimas
        FROM apartado_detalle d JOIN apartado a ON a.id = d.apartadoId
        WHERE a.estado = 'PENDIENTE'
        GROUP BY d.productoId
        """,
    )
    fun observarApartadoPorProducto(): Flow<List<CantidadPorProducto>>

    /** Lo apartado (pendiente) de un producto, sin contar el apartado [excepto] (el que se entrega). */
    @Query(
        """
        SELECT COALESCE(SUM(d.cantidadMilesimas), 0)
        FROM apartado_detalle d JOIN apartado a ON a.id = d.apartadoId
        WHERE a.estado = 'PENDIENTE' AND d.productoId = :productoId AND a.id <> :excepto
        """,
    )
    suspend fun apartadoDe(productoId: String, excepto: String = ""): Long

    /** Pendientes con entrega antes de [hasta] (para liberarlos al cerrar la caja). */
    @Query("SELECT * FROM apartado WHERE estado = 'PENDIENTE' AND entregaEn < :hasta ORDER BY entregaEn")
    suspend fun pendientesAntesDe(hasta: Long): List<Apartado>

    /** Cambia de estado solo si sigue pendiente; devuelve 0 si ya no lo estaba. */
    @Query(
        """
        UPDATE apartado SET estado = :estado, ventaId = :ventaId, terminadoEn = :ahora, actualizadoEn = :ahora, pendienteSync = 1
        WHERE id = :id AND estado = 'PENDIENTE'
        """,
    )
    suspend fun terminar(id: String, estado: String, ahora: Long, ventaId: String? = null): Int
}
