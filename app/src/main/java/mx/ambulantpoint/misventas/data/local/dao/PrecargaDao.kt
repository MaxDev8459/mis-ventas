package mx.ambulantpoint.misventas.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.data.local.entity.Precarga

/** Precargas de la carga del día. */
@Dao
interface PrecargaDao {
    /** Activas, de la más usada a la menos usada. */
    @Query("SELECT * FROM precarga WHERE activa = 1 ORDER BY vecesUsada DESC, ultimoUso DESC, nombre COLLATE NOCASE")
    fun observarActivas(): Flow<List<Precarga>>

    @Query("SELECT * FROM precarga WHERE id = :id")
    suspend fun porId(id: String): Precarga?

    /** Por nombre normalizado (incluye inactivas: al guardar con ese nombre se reactiva). */
    @Query("SELECT * FROM precarga WHERE nombreNormalizado = :nombreNormalizado")
    suspend fun porNombre(nombreNormalizado: String): Precarga?

    @Upsert
    suspend fun guardar(p: Precarga)
}
