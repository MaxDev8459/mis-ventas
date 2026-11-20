package mx.ambulantpoint.misventas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.data.local.entity.Configuracion
import mx.ambulantpoint.misventas.data.local.entity.EliminacionPendiente
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.CorteFormaPago
import mx.ambulantpoint.misventas.data.local.entity.EstadoVenta
import mx.ambulantpoint.misventas.data.local.entity.MovimientoInventario
import mx.ambulantpoint.misventas.data.local.entity.Venta
import mx.ambulantpoint.misventas.data.local.entity.VentaDetalle

@Dao
interface CajaDao {
    @Query("SELECT * FROM caja WHERE estado = 'ABIERTA' LIMIT 1")
    fun observarAbierta(): Flow<Caja?>

    @Query("SELECT * FROM caja WHERE estado = 'ABIERTA' LIMIT 1")
    suspend fun abierta(): Caja?

    @Query("SELECT * FROM caja WHERE id = :id")
    suspend fun porId(id: String): Caja?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(caja: Caja)

    @Update
    suspend fun actualizar(caja: Caja)

    /** RF 5.3: una venta o cancelación posterior al corte obliga a recalcularlo. */
    @Query("UPDATE caja SET corteVigente = 0, actualizadoEn = :ahora, pendienteSync = 1 WHERE id = :cajaId")
    suspend fun invalidarCorte(cajaId: String, ahora: Long)

    /** RF 5.5: cortes (cajas cerradas) del más reciente al más antiguo. */
    @Query(
        """
        SELECT * FROM caja
        WHERE estado = 'CERRADA' AND cerradaEn BETWEEN :desde AND :hasta
        ORDER BY cerradaEn DESC
        """,
    )
    fun observarCerradas(desde: Long = 0, hasta: Long = Long.MAX_VALUE): Flow<List<Caja>>

    /** RF 5.2: totales de ventas no canceladas de la caja. */
    @Query(
        """
        SELECT COUNT(*) AS numeroVentas,
               COALESCE(SUM(totalCentavos), 0) AS totalVendidoCentavos,
               COALESCE(SUM(descuentoCentavos), 0) AS descuentosCentavos
        FROM venta WHERE cajaId = :cajaId AND estado = 'REGISTRADA'
        """,
    )
    suspend fun resumenVentas(cajaId: String): ResumenVentas

    /** RF 5.2: total por cada forma de pago (no canceladas). */
    @Query(
        """
        SELECT f.id AS formaPagoId, f.nombre AS nombreFormaPago, f.esEfectivo AS esEfectivo,
               COUNT(v.id) AS numeroVentas, COALESCE(SUM(v.totalCentavos), 0) AS totalCentavos
        FROM venta v JOIN forma_pago f ON f.id = v.formaPagoId
        WHERE v.cajaId = :cajaId AND v.estado = 'REGISTRADA'
        GROUP BY f.id
        ORDER BY f.esEfectivo DESC, f.nombre COLLATE NOCASE
        """,
    )
    suspend fun totalesPorFormaPago(cajaId: String): List<TotalFormaPago>

    @Query("DELETE FROM corte_forma_pago WHERE cajaId = :cajaId")
    suspend fun borrarCorteFormaPago(cajaId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarCorteFormaPago(filas: List<CorteFormaPago>)

    @Query("SELECT * FROM corte_forma_pago WHERE cajaId = :cajaId ORDER BY nombreFormaPago COLLATE NOCASE")
    fun observarCorteFormaPago(cajaId: String): Flow<List<CorteFormaPago>>
}

@Dao
interface VentaDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(venta: Venta)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarDetalles(detalles: List<VentaDetalle>)

    @Query("SELECT * FROM venta WHERE id = :id")
    suspend fun porId(id: String): Venta?

    @Query("SELECT * FROM venta_detalle WHERE ventaId = :ventaId ORDER BY nombreProducto COLLATE NOCASE")
    suspend fun detalles(ventaId: String): List<VentaDetalle>

    @Query("SELECT * FROM venta_detalle WHERE ventaId = :ventaId ORDER BY nombreProducto COLLATE NOCASE")
    fun observarDetalles(ventaId: String): Flow<List<VentaDetalle>>

    @Query(
        """
        UPDATE venta SET estado = 'CANCELADA', canceladaEn = :ahora, actualizadoEn = :ahora, pendienteSync = 1
        WHERE id = :id AND estado = 'REGISTRADA'
        """,
    )
    suspend fun marcarCancelada(id: String, ahora: Long): Int

    /** RF 3.4: de la más reciente a la más antigua, con filtros opcionales. */
    @Query(
        """
        SELECT v.*, f.nombre AS formaPagoNombre
        FROM venta v JOIN forma_pago f ON f.id = v.formaPagoId
        WHERE v.fechaHora BETWEEN :desde AND :hasta
          AND (:estado IS NULL OR v.estado = :estado)
          AND (:formaPagoId IS NULL OR v.formaPagoId = :formaPagoId)
        ORDER BY v.fechaHora DESC
        """,
    )
    fun observarHistorial(
        desde: Long,
        hasta: Long,
        estado: EstadoVenta? = null,
        formaPagoId: String? = null,
    ): Flow<List<VentaConFormaPago>>
}

@Dao
interface MovimientoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(movimiento: MovimientoInventario)

    /** RF 4.4: historial de un producto. */
    @Query(
        """
        SELECT * FROM movimiento_inventario
        WHERE productoId = :productoId AND fechaHora BETWEEN :desde AND :hasta
        ORDER BY fechaHora DESC
        """,
    )
    fun observarPorProducto(
        productoId: String,
        desde: Long = 0,
        hasta: Long = Long.MAX_VALUE,
    ): Flow<List<MovimientoInventario>>
}

/** Consultas del Módulo 6 y de existencia baja. Solo ventas no canceladas salvo donde se indica. */
@Dao
interface ReporteDao {
    /** RF 6.1 (incluye número e importe de canceladas). */
    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN estado = 'REGISTRADA' THEN 1 ELSE 0 END), 0) AS numeroVentas,
          COALESCE(SUM(CASE WHEN estado = 'REGISTRADA' THEN totalCentavos ELSE 0 END), 0) AS totalVendidoCentavos,
          COALESCE(SUM(CASE WHEN estado = 'REGISTRADA' THEN descuentoCentavos ELSE 0 END), 0) AS descuentosCentavos,
          COALESCE(SUM(CASE WHEN estado = 'CANCELADA' THEN 1 ELSE 0 END), 0) AS numeroCanceladas,
          COALESCE(SUM(CASE WHEN estado = 'CANCELADA' THEN totalCentavos ELSE 0 END), 0) AS importeCanceladasCentavos
        FROM venta WHERE fechaHora BETWEEN :desde AND :hasta
        """,
    )
    suspend fun resumenPeriodo(desde: Long, hasta: Long): ResumenPeriodo

    /** RF 6.1: desglose por día. */
    @Query(
        """
        SELECT date(fechaHora / 1000, 'unixepoch', 'localtime') AS dia,
               COUNT(*) AS numeroVentas, SUM(totalCentavos) AS totalCentavos
        FROM venta
        WHERE estado = 'REGISTRADA' AND fechaHora BETWEEN :desde AND :hasta
        GROUP BY dia ORDER BY dia
        """,
    )
    suspend fun totalesPorDia(desde: Long, hasta: Long): List<TotalPorDia>

    /** RF 6.2: incluye formas de pago desactivadas con ventas en el periodo. */
    @Query(
        """
        SELECT f.id AS formaPagoId, f.nombre AS nombreFormaPago, f.esEfectivo AS esEfectivo,
               COUNT(v.id) AS numeroVentas, SUM(v.totalCentavos) AS totalCentavos
        FROM venta v JOIN forma_pago f ON f.id = v.formaPagoId
        WHERE v.estado = 'REGISTRADA' AND v.fechaHora BETWEEN :desde AND :hasta
        GROUP BY f.id ORDER BY totalCentavos DESC
        """,
    )
    suspend fun totalesPorFormaPago(desde: Long, hasta: Long): List<TotalFormaPago>

    /** RF 6.3: incluye productos desactivados. El orden final lo decide el dominio. */
    @Query(
        """
        SELECT p.id AS productoId, p.nombre AS nombre, c.nombre AS categoriaNombre, p.tipoVenta AS tipoVenta,
               SUM(d.cantidadMilesimas) AS cantidadMilesimas, SUM(d.importeCentavos) AS importeCentavos
        FROM venta_detalle d
          JOIN venta v ON v.id = d.ventaId
          JOIN producto p ON p.id = d.productoId
          JOIN categoria c ON c.id = p.categoriaId
        WHERE v.estado = 'REGISTRADA' AND v.fechaHora BETWEEN :desde AND :hasta
        GROUP BY p.id
        ORDER BY importeCentavos DESC
        """,
    )
    suspend fun productosVendidos(desde: Long, hasta: Long): List<ProductoVendido>

    /** RF 6.4: importe por categoría, de mayor a menor. */
    @Query(
        """
        SELECT c.id AS categoriaId, c.nombre AS categoriaNombre, SUM(d.importeCentavos) AS importeCentavos
        FROM venta_detalle d
          JOIN venta v ON v.id = d.ventaId
          JOIN producto p ON p.id = d.productoId
          JOIN categoria c ON c.id = p.categoriaId
        WHERE v.estado = 'REGISTRADA' AND v.fechaHora BETWEEN :desde AND :hasta
        GROUP BY c.id ORDER BY importeCentavos DESC
        """,
    )
    suspend fun totalesPorCategoria(desde: Long, hasta: Long): List<TotalCategoria>

    /**
     * RF 4.5 / 6.5: suma de lo vendido en los últimos 7 días (calendario, hora local) en que el
     * producto tuvo ventas no canceladas, y cuántos de esos días hay (máximo 7).
     */
    @Query(
        """
        SELECT COUNT(*) AS dias, COALESCE(SUM(cantidad), 0) AS cantidadMilesimas FROM (
          SELECT date(v.fechaHora / 1000, 'unixepoch', 'localtime') AS dia, SUM(d.cantidadMilesimas) AS cantidad
          FROM venta_detalle d JOIN venta v ON v.id = d.ventaId
          WHERE d.productoId = :productoId AND v.estado = 'REGISTRADA'
          GROUP BY dia ORDER BY dia DESC LIMIT 7
        )
        """,
    )
    suspend fun ventasRecientes(productoId: String): VentasRecientes

    /**
     * RF 4.3 / 4.5 para toda la lista de existencias de una vez: lo vendido por producto y día
     * (no canceladas), del día más reciente al más antiguo. El dominio toma los primeros 7 de cada uno.
     */
    @Query(
        """
        SELECT d.productoId AS productoId, date(v.fechaHora / 1000, 'unixepoch', 'localtime') AS dia,
               SUM(d.cantidadMilesimas) AS cantidadMilesimas
        FROM venta_detalle d JOIN venta v ON v.id = d.ventaId
        WHERE v.estado = 'REGISTRADA'
        GROUP BY d.productoId, dia
        ORDER BY d.productoId, dia DESC
        """,
    )
    fun observarVentasPorDia(): Flow<List<VentaDiaProducto>>
}

/** RF 7.2 (Sprint 2): registros pendientes de sincronizar en todas las tablas. */
@Dao
interface SyncDao {
    /** RF 7.2: registros pendientes de sincronizar (incluye eliminaciones por enviar). */
    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM perfil WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM configuracion WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM categoria WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM producto WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM forma_pago WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM caja WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM corte_forma_pago WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM venta WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM venta_detalle WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM movimiento_inventario WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM eliminacion_pendiente)
        """,
    )
    fun observarPendientes(): Flow<Int>

    // ── RF 7.1: lectura de pendientes por tabla, en lotes ──
    @Query("SELECT * FROM perfil WHERE pendienteSync = 1 LIMIT :n") suspend fun perfiles(n: Int): List<Perfil>
    @Query("SELECT * FROM configuracion WHERE pendienteSync = 1 LIMIT :n") suspend fun configuraciones(n: Int): List<Configuracion>
    @Query("SELECT * FROM categoria WHERE pendienteSync = 1 LIMIT :n") suspend fun categorias(n: Int): List<Categoria>
    @Query("SELECT * FROM producto WHERE pendienteSync = 1 LIMIT :n") suspend fun productos(n: Int): List<Producto>
    @Query("SELECT * FROM forma_pago WHERE pendienteSync = 1 LIMIT :n") suspend fun formasPago(n: Int): List<FormaPago>
    @Query("SELECT * FROM caja WHERE pendienteSync = 1 LIMIT :n") suspend fun cajas(n: Int): List<Caja>
    @Query("SELECT * FROM corte_forma_pago WHERE pendienteSync = 1 LIMIT :n") suspend fun cortesFormaPago(n: Int): List<CorteFormaPago>
    @Query("SELECT * FROM venta WHERE pendienteSync = 1 LIMIT :n") suspend fun ventas(n: Int): List<Venta>
    @Query("SELECT * FROM venta_detalle WHERE pendienteSync = 1 LIMIT :n") suspend fun ventaDetalles(n: Int): List<VentaDetalle>
    @Query("SELECT * FROM movimiento_inventario WHERE pendienteSync = 1 LIMIT :n") suspend fun movimientos(n: Int): List<MovimientoInventario>

    // ── RF 7.1 + 2.9: eliminaciones ──
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registrarEliminacion(e: EliminacionPendiente)

    @Query("SELECT * FROM eliminacion_pendiente LIMIT :n")
    suspend fun eliminaciones(n: Int): List<EliminacionPendiente>

    @Query("DELETE FROM eliminacion_pendiente WHERE coleccion = :coleccion AND docId = :docId")
    suspend fun quitarEliminacion(coleccion: String, docId: String)

    // ── RF 7.1: reenviar los productos una vez para respaldar sus imágenes ──
    @Query("UPDATE producto SET pendienteSync = 1")
    suspend fun marcarProductosPendientes()

    // ── RF 1.1 + 7.1: al registrar otra cuenta en un teléfono con datos, se respaldan en la cuenta nueva ──
    @Query("UPDATE configuracion SET pendienteSync = 1") suspend fun pendienteConfiguracion()
    @Query("UPDATE categoria SET pendienteSync = 1") suspend fun pendienteCategorias()
    @Query("UPDATE forma_pago SET pendienteSync = 1") suspend fun pendienteFormasPago()
    @Query("UPDATE caja SET pendienteSync = 1") suspend fun pendienteCajas()
    @Query("UPDATE corte_forma_pago SET pendienteSync = 1") suspend fun pendienteCortesFormaPago()
    @Query("UPDATE venta SET pendienteSync = 1") suspend fun pendienteVentas()
    @Query("UPDATE venta_detalle SET pendienteSync = 1") suspend fun pendienteVentaDetalles()
    @Query("UPDATE movimiento_inventario SET pendienteSync = 1") suspend fun pendienteMovimientos()

    // ── RF 7.4: recuperar en un dispositivo nuevo ──

    /** Registros del negocio sin enviar (sin perfil, configuración ni formas de pago iniciales). */
    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM categoria WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM producto WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM caja WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM corte_forma_pago WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM venta WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM venta_detalle WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM movimiento_inventario WHERE pendienteSync = 1) +
          (SELECT COUNT(*) FROM eliminacion_pendiente)
        """,
    )
    suspend fun contarPendientesNegocio(): Int

    @Query("DELETE FROM movimiento_inventario") suspend fun borrarMovimientos()
    @Query("DELETE FROM venta_detalle") suspend fun borrarVentaDetalles()
    @Query("DELETE FROM venta") suspend fun borrarVentas()
    @Query("DELETE FROM corte_forma_pago") suspend fun borrarCortesFormaPago()
    @Query("DELETE FROM caja") suspend fun borrarCajas()
    @Query("DELETE FROM producto") suspend fun borrarProductos()
    @Query("DELETE FROM forma_pago") suspend fun borrarFormasPago()
    @Query("DELETE FROM categoria") suspend fun borrarCategorias()
    @Query("DELETE FROM configuracion") suspend fun borrarConfiguracion()
    @Query("DELETE FROM perfil") suspend fun borrarPerfil()
    @Query("DELETE FROM eliminacion_pendiente") suspend fun borrarEliminaciones()

    @Insert suspend fun insertarPerfiles(l: List<Perfil>)
    @Insert suspend fun insertarConfiguraciones(l: List<Configuracion>)
    @Insert suspend fun insertarCategorias(l: List<Categoria>)
    @Insert suspend fun insertarFormasPago(l: List<FormaPago>)
    @Insert suspend fun insertarProductos(l: List<Producto>)
    @Insert suspend fun insertarCajas(l: List<Caja>)
    @Insert suspend fun insertarCortesFormaPago(l: List<CorteFormaPago>)
    @Insert suspend fun insertarVentas(l: List<Venta>)
    @Insert suspend fun insertarVentaDetalles(l: List<VentaDetalle>)
    @Insert suspend fun insertarMovimientos(l: List<MovimientoInventario>)
}

