package mx.ambulantpoint.misventas.data.local.dao

import androidx.room.Embedded
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.local.entity.Venta

/** Resultados de consultas que no son una tabla completa. */

data class ProductoConCategoria(
    @Embedded val producto: Producto,
    val categoriaNombre: String,
)

/** Fiado (v7): una venta fiada no tiene forma de pago y sí cliente. */
data class VentaConFormaPago(
    @Embedded val venta: Venta,
    val formaPagoNombre: String?,
    val clienteNombre: String? = null,
) {
    /** Lo que se muestra en historial: la forma de pago o "Fiado · Cliente". */
    val etiquetaPago: String get() = formaPagoNombre ?: "Fiado · ${clienteNombre.orEmpty()}"
}

/** Reportes: ventas fiadas (sin forma de pago) y renglones sin producto ("Varios"). */
const val ID_FIADO = "fiado"
const val NOMBRE_FIADO = "Fiado"
const val ID_VARIOS = "varios"
const val NOMBRE_VARIOS = "Varios"

/** RF 5.2: totales de las ventas no canceladas de una caja. */
data class ResumenVentas(
    val numeroVentas: Int,
    val totalVendidoCentavos: Long,
    val descuentosCentavos: Long,
)

/** RF 5.2 / 6.2: ventas agrupadas por forma de pago. */
data class TotalFormaPago(
    val formaPagoId: String,
    val nombreFormaPago: String,
    val esEfectivo: Boolean,
    val numeroVentas: Int,
    val totalCentavos: Long,
)

/** RF 6.1. */
data class ResumenPeriodo(
    val numeroVentas: Int,
    val totalVendidoCentavos: Long,
    val descuentosCentavos: Long,
    val numeroCanceladas: Int,
    val importeCanceladasCentavos: Long,
)

/** RF 6.1: desglose por día (`dia` en formato AAAA-MM-DD, hora local). */
data class TotalPorDia(
    val dia: String,
    val numeroVentas: Int,
    val totalCentavos: Long,
)

/** Reportes (Fase 5): ventas agrupadas por hora del día (0–23) o día de la semana (0 = domingo … 6 = sábado). */
data class VentasPorGrupo(
    val grupo: Int,
    val numeroVentas: Int,
    val totalCentavos: Long,
)

/** Reportes (Fase 5): una salida de inventario por ajuste, con el precio guardado (v9) y el actual. */
data class SalidaAjuste(
    val productoId: String,
    val nombre: String,
    val tipoVenta: TipoVenta,
    /** Negativa (es una salida). */
    val cantidadMilesimas: Long,
    val motivo: mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste?,
    val precioGuardadoCentavos: Long?,
    val precioActualCentavos: Long,
)

/** RF 6.3. */
data class ProductoVendido(
    val productoId: String,
    val nombre: String,
    val categoriaNombre: String,
    val tipoVenta: TipoVenta,
    val cantidadMilesimas: Long,
    val importeCentavos: Long,
)

/** RF 6.4. */
data class TotalCategoria(
    val categoriaId: String,
    val categoriaNombre: String,
    val importeCentavos: Long,
)

/**
 * RF 4.5 / 6.5: cantidad vendida en los últimos (hasta 7) días con ventas del producto.
 * Si `dias` < 7 se usa el umbral fijo.
 */
data class VentaDiaProducto(
    val productoId: String,
    val dia: String,
    val cantidadMilesimas: Long,
)

data class VentasRecientes(
    val dias: Int,
    val cantidadMilesimas: Long,
)
