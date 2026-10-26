package mx.ambulantpoint.misventas.data.local.entity

/** RF 2.1: tipo de venta del producto. */
enum class TipoVenta { UNIDAD, PESO }

/** RF 3.3: las ventas no se borran, cambian de estado. */
enum class EstadoVenta { REGISTRADA, CANCELADA }

/** RF 5.1 / 5.3. */
enum class EstadoCaja { ABIERTA, CERRADA }

/** RF 4.4: tipos de movimiento de inventario. */
enum class TipoMovimiento { ENTRADA, VENTA, CANCELACION, AJUSTE }

/** RF 4.2: motivos de ajuste (lista cerrada del DFR). */
enum class MotivoAjuste { MERMA, PRODUCTO_CADUCADO, CONSUMO_PROPIO, CORRECCION_CONTEO, OTRO }

/** RF 3.6: tipo de descuento aplicado en una venta. */
enum class TipoDescuento { PORCENTAJE, MONTO_FIJO }
