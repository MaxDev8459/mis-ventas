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

data class VentaConFormaPago(
    @Embedded val venta: Venta,
    val formaPagoNombre: String,
)

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
data class VentasRecientes(
    val dias: Int,
    val cantidadMilesimas: Long,
)
