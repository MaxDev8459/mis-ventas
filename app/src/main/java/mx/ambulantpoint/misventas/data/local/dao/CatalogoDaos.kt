package mx.ambulantpoint.misventas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.data.local.entity.Configuracion
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.data.local.entity.Producto

@Dao
interface PerfilDao {
    @Query("SELECT * FROM perfil LIMIT 1")
    fun observar(): Flow<Perfil?>

    @Query("SELECT * FROM perfil LIMIT 1")
    suspend fun obtener(): Perfil?

    @Upsert
    suspend fun guardar(perfil: Perfil)
}

@Dao
interface ConfiguracionDao {
    @Query("SELECT * FROM configuracion WHERE id = '${Configuracion.ID_UNICA}'")
    fun observar(): Flow<Configuracion?>

    @Upsert
    suspend fun guardar(configuracion: Configuracion)
}

@Dao
interface CategoriaDao {
    /** RF 2.7: todas, incluidas las que no tienen productos. */
    @Query("SELECT * FROM categoria ORDER BY nombre COLLATE NOCASE")
    fun observarTodas(): Flow<List<Categoria>>

    @Query("SELECT * FROM categoria WHERE id = :id")
    suspend fun porId(id: String): Categoria?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(categoria: Categoria)

    @Update
    suspend fun actualizar(categoria: Categoria)

    @Query("DELETE FROM categoria WHERE id = :id")
    suspend fun eliminar(id: String): Int

    /** RF 2.9: productos activos o inactivos asignados. */
    @Query("SELECT COUNT(*) FROM producto WHERE categoriaId = :categoriaId")
    suspend fun contarProductos(categoriaId: String): Int
}

@Dao
interface ProductoDao {
    /**
     * RF 2.5: búsqueda por nombre, filtro por categoría y estado (null = sin filtro).
     * Por defecto la pantalla pasa activo = true.
     */
    @Query(
        """
        SELECT p.*, c.nombre AS categoriaNombre
        FROM producto p JOIN categoria c ON c.id = p.categoriaId
        WHERE (:texto = '' OR p.nombre LIKE '%' || :texto || '%')
          AND (:categoriaId IS NULL OR p.categoriaId = :categoriaId)
          AND (:activo IS NULL OR p.activo = :activo)
        ORDER BY p.nombre COLLATE NOCASE
        """,
    )
    fun observar(texto: String = "", categoriaId: String? = null, activo: Boolean? = true): Flow<List<ProductoConCategoria>>

    /** RF 3.1: solo activos con existencia mayor a cero. */
    @Query(
        """
        SELECT p.*, c.nombre AS categoriaNombre
        FROM producto p JOIN categoria c ON c.id = p.categoriaId
        WHERE p.activo = 1 AND p.existenciaMilesimas > 0
        ORDER BY p.nombre COLLATE NOCASE
        """,
    )
    fun observarParaVenta(): Flow<List<ProductoConCategoria>>

    @Query("SELECT * FROM producto WHERE id = :id")
    suspend fun porId(id: String): Producto?

    @Query("SELECT * FROM producto WHERE id = :id")
    fun observarPorId(id: String): Flow<Producto?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(producto: Producto)

    @Update
    suspend fun actualizar(producto: Producto)

    @Query("UPDATE producto SET activo = :activo, actualizadoEn = :ahora, pendienteSync = 1 WHERE id = :id")
    suspend fun cambiarEstado(id: String, activo: Boolean, ahora: Long): Int

    /**
     * Descuenta solo si alcanza la existencia; devuelve 0 si no alcanzó (la transacción
     * que la llama debe abortar). Así nunca queda existencia negativa.
     */
    @Query(
        """
        UPDATE producto
        SET existenciaMilesimas = existenciaMilesimas - :cantidad, actualizadoEn = :ahora, pendienteSync = 1
        WHERE id = :id AND existenciaMilesimas >= :cantidad
        """,
    )
    suspend fun descontarExistencia(id: String, cantidad: Long, ahora: Long): Int

    @Query(
        """
        UPDATE producto
        SET existenciaMilesimas = existenciaMilesimas + :cantidad, actualizadoEn = :ahora, pendienteSync = 1
        WHERE id = :id
        """,
    )
    suspend fun sumarExistencia(id: String, cantidad: Long, ahora: Long): Int
}

@Dao
interface FormaPagoDao {
    @Query("SELECT * FROM forma_pago ORDER BY esEfectivo DESC, nombre COLLATE NOCASE")
    fun observarTodas(): Flow<List<FormaPago>>

    /** RF 3.2: solo las activas aparecen al cobrar. */
    @Query("SELECT * FROM forma_pago WHERE activa = 1 ORDER BY esEfectivo DESC, nombre COLLATE NOCASE")
    fun observarActivas(): Flow<List<FormaPago>>

    @Query("SELECT * FROM forma_pago WHERE id = :id")
    suspend fun porId(id: String): FormaPago?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(formaPago: FormaPago)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarTodas(formas: List<FormaPago>)

    @Update
    suspend fun actualizar(formaPago: FormaPago)

    @Query("SELECT COUNT(*) FROM forma_pago")
    suspend fun contar(): Int
}
