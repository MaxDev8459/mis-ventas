package mx.ambulantpoint.misventas.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.data.local.entity.Abono
import mx.ambulantpoint.misventas.data.local.entity.AplicacionAbono
import mx.ambulantpoint.misventas.data.local.entity.Cliente
import mx.ambulantpoint.misventas.data.local.entity.EstadoVenta

/** Cliente con su saldo (ventas fiadas registradas − lo abonado a ellas con abonos no cancelados). */
data class ClienteConSaldo(
    @Embedded val cliente: Cliente,
    val saldoCentavos: Long,
)

/** Una venta fiada con lo que se le ha abonado (abonos no cancelados). */
data class VentaFiada(
    val ventaId: String,
    val cajaId: String,
    val fechaHora: Long,
    val totalCentavos: Long,
    val estado: EstadoVenta,
    val abonadoCentavos: Long,
)

data class AbonoConForma(
    @Embedded val abono: Abono,
    val formaPagoNombre: String,
)

/*
 * Saldo de un cliente: lo mismo en SQL (lista) y en ReglaFiado (detalle, cobro y abonos).
 * Solo cuentan ventas REGISTRADAS y abonos REGISTRADOS; la devolución no cuenta para el saldo
 * (la venta ya está cancelada).
 */
private const val ABONADO_A_VENTA =
    "COALESCE((SELECT SUM(ap.montoCentavos) FROM aplicacion_abono ap JOIN abono a ON a.id = ap.abonoId " +
        "WHERE ap.ventaId = v.id AND a.estado = 'REGISTRADO'), 0)"

@Dao
interface FiadoDao {
    // ── Clientes ──
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarCliente(cliente: Cliente)

    @Update
    suspend fun actualizarCliente(cliente: Cliente)

    @Query("SELECT * FROM cliente WHERE id = :id")
    suspend fun cliente(id: String): Cliente?

    @Query("SELECT * FROM cliente WHERE nombreNormalizado = :nombreNormalizado")
    suspend fun clientePorNombre(nombreNormalizado: String): Cliente?

    @Query("SELECT * FROM cliente WHERE id = :id")
    fun observarCliente(id: String): Flow<Cliente?>

    @Query(
        """
        SELECT c.*, COALESCE((
          SELECT SUM(v.totalCentavos - $ABONADO_A_VENTA) FROM venta v
          WHERE v.clienteId = c.id AND v.estado = 'REGISTRADA'
        ), 0) AS saldoCentavos
        FROM cliente c
        """,
    )
    fun observarClientesConSaldo(): Flow<List<ClienteConSaldo>>

    // ── Ventas fiadas ──
    @Query(
        """
        SELECT v.id AS ventaId, v.cajaId AS cajaId, v.fechaHora AS fechaHora, v.totalCentavos AS totalCentavos,
               v.estado AS estado, $ABONADO_A_VENTA AS abonadoCentavos
        FROM venta v WHERE v.clienteId = :clienteId
        ORDER BY v.fechaHora, v.id
        """,
    )
    suspend fun ventasFiadas(clienteId: String): List<VentaFiada>

    @Query(
        """
        SELECT v.id AS ventaId, v.cajaId AS cajaId, v.fechaHora AS fechaHora, v.totalCentavos AS totalCentavos,
               v.estado AS estado, $ABONADO_A_VENTA AS abonadoCentavos
        FROM venta v WHERE v.clienteId = :clienteId
        ORDER BY v.fechaHora DESC, v.id
        """,
    )
    fun observarVentasFiadas(clienteId: String): Flow<List<VentaFiada>>

    @Query("SELECT $ABONADO_A_VENTA FROM venta v WHERE v.id = :ventaId")
    suspend fun abonadoAVenta(ventaId: String): Long?

    @Query("SELECT $ABONADO_A_VENTA FROM venta v WHERE v.id = :ventaId")
    fun observarAbonadoAVenta(ventaId: String): Flow<Long?>

    // ── Abonos ──
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarAbono(abono: Abono)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarAplicaciones(aplicaciones: List<AplicacionAbono>)

    @Query("SELECT * FROM abono WHERE id = :id")
    suspend fun abono(id: String): Abono?

    @Query("SELECT * FROM aplicacion_abono WHERE abonoId = :abonoId")
    suspend fun aplicacionesDe(abonoId: String): List<AplicacionAbono>

    @Query(
        """
        UPDATE abono SET estado = 'CANCELADO', canceladoEn = :ahora, actualizadoEn = :ahora, pendienteSync = 1
        WHERE id = :id AND estado = 'REGISTRADO'
        """,
    )
    suspend fun marcarAbonoCancelado(id: String, ahora: Long): Int

    @Query(
        """
        SELECT a.*, f.nombre AS formaPagoNombre FROM abono a JOIN forma_pago f ON f.id = a.formaPagoId
        WHERE a.clienteId = :clienteId ORDER BY a.fechaHora DESC
        """,
    )
    fun observarAbonos(clienteId: String): Flow<List<AbonoConForma>>

    // ── Corte de caja ──

    /** Total de las ventas fiadas registradas en la caja. */
    @Query("SELECT COALESCE(SUM(totalCentavos), 0) FROM venta WHERE cajaId = :cajaId AND estado = 'REGISTRADA' AND clienteId IS NOT NULL")
    suspend fun fiadoDeCaja(cajaId: String): Long

    /** Abonos (no cancelados) de la caja por forma de pago; `numeroVentas` = número de abonos. */
    @Query(
        """
        SELECT f.id AS formaPagoId, f.nombre AS nombreFormaPago, f.esEfectivo AS esEfectivo,
               COUNT(a.id) AS numeroVentas, COALESCE(SUM(a.montoCentavos), 0) AS totalCentavos
        FROM abono a JOIN forma_pago f ON f.id = a.formaPagoId
        WHERE a.cajaId = :cajaId AND a.tipo = 'ABONO' AND a.estado = 'REGISTRADO'
        GROUP BY f.id
        ORDER BY f.esEfectivo DESC, f.nombre COLLATE NOCASE
        """,
    )
    suspend fun abonosPorFormaDeCaja(cajaId: String): List<TotalFormaPago>

    @Query(
        """
        SELECT f.id AS formaPagoId, f.nombre AS nombreFormaPago, f.esEfectivo AS esEfectivo,
               COUNT(a.id) AS numeroVentas, COALESCE(SUM(a.montoCentavos), 0) AS totalCentavos
        FROM abono a JOIN forma_pago f ON f.id = a.formaPagoId
        WHERE a.cajaId = :cajaId AND a.tipo = 'ABONO' AND a.estado = 'REGISTRADO'
        GROUP BY f.id
        ORDER BY f.esEfectivo DESC, f.nombre COLLATE NOCASE
        """,
    )
    fun observarAbonosPorFormaDeCaja(cajaId: String): Flow<List<TotalFormaPago>>

    /** Devoluciones (siempre en efectivo) registradas en la caja. */
    @Query("SELECT COALESCE(SUM(montoCentavos), 0) FROM abono WHERE cajaId = :cajaId AND tipo = 'DEVOLUCION' AND estado = 'REGISTRADO'")
    suspend fun devolucionesDeCaja(cajaId: String): Long
}
