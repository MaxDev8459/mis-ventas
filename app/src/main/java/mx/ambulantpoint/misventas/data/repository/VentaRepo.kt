package mx.ambulantpoint.misventas.data.repository

import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.core.Dinero
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.dao.VentaConFormaPago
import mx.ambulantpoint.misventas.data.local.entity.EstadoCaja
import mx.ambulantpoint.misventas.data.local.entity.EstadoVenta
import mx.ambulantpoint.misventas.data.local.entity.MovimientoInventario
import mx.ambulantpoint.misventas.data.local.entity.TipoDescuento
import mx.ambulantpoint.misventas.data.local.entity.TipoMovimiento
import mx.ambulantpoint.misventas.data.local.entity.Venta
import mx.ambulantpoint.misventas.data.local.entity.VentaDetalle
import mx.ambulantpoint.misventas.domain.puertos.PuertoVentas

/** Un producto y su cantidad en la venta (milésimas: 2 pzas = 2000; 0.250 kg = 250). */
data class LineaVenta(val productoId: String, val cantidadMilesimas: Long)

/** Descuento ya calculado por el dominio (RF 3.6). */
data class DescuentoVenta(
    val tipo: TipoDescuento,
    /** Porcentaje en centésimas (1250 = 12.50 %) o monto fijo en centavos. */
    val valor: Long,
    val centavos: Long,
)

data class SolicitudCobro(
    val lineas: List<LineaVenta>,
    val formaPagoId: String,
    /** Solo en efectivo. */
    val montoRecibidoCentavos: Long? = null,
    val descuento: DescuentoVenta? = null,
)

data class VentaCobrada(
    val venta: Venta,
    val detalles: List<VentaDetalle>,
    /** productoId → existencia después de la venta, para evaluar existencia baja (RF 4.5). */
    val existencias: Map<String, Long>,
)

/** RF 3.1 – 3.4. */
class VentaRepo(private val db: MisVentasDb, private val reloj: Reloj) : PuertoVentas {
    private val ventaDao = db.ventaDao()
    private val productoDao = db.productoDao()
    private val movimientoDao = db.movimientoDao()
    private val cajaDao = db.cajaDao()

    fun observarHistorial(
        desde: Long,
        hasta: Long,
        estado: EstadoVenta? = null,
        formaPagoId: String? = null,
    ): Flow<List<VentaConFormaPago>> = ventaDao.observarHistorial(desde, hasta, estado, formaPagoId)

    fun observarDetalles(ventaId: String): Flow<List<VentaDetalle>> = ventaDao.observarDetalles(ventaId)

    suspend fun porId(ventaId: String): Venta? = ventaDao.porId(ventaId)

    /**
     * RF 3.2: guarda venta + detalle + movimientos y descuenta existencias, todo o nada.
     * El importe usa el precio vigente del producto en este momento.
     */
    override suspend fun cobrar(solicitud: SolicitudCobro): Resultado<VentaCobrada> = db.transaccion {
        if (solicitud.lineas.isEmpty()) fallar(FallaDatos.VentaSinProductos)
        val caja = cajaDao.abierta() ?: fallar(FallaDatos.SinCajaAbierta)
        val forma = db.formaPagoDao().porId(solicitud.formaPagoId)
        if (forma == null || !forma.activa) fallar(FallaDatos.FormaPagoNoDisponible)

        val ahora = reloj.ahora()
        val ventaId = nuevoId()

        // Si el mismo producto viene en varias líneas, se suma para validar contra la existencia.
        val cantidades = solicitud.lineas.groupBy { it.productoId }
            .mapValues { (_, lineas) -> lineas.sumOf { it.cantidadMilesimas } }

        val detalles = cantidades.map { (productoId, cantidad) ->
            val producto = productoDao.porId(productoId) ?: fallar(FallaDatos.NoEncontrado)
            if (!producto.activo) fallar(FallaDatos.ProductoNoDisponible(productoId))
            if (cantidad <= 0 || !cantidadValida(producto.tipoVenta, cantidad)) fallar(FallaDatos.CantidadInvalida)
            if (cantidad > producto.existenciaMilesimas) {
                fallar(
                    FallaDatos.ExistenciaInsuficiente(
                        productoId, producto.nombre, producto.tipoVenta, producto.existenciaMilesimas,
                    ),
                )
            }
            VentaDetalle(
                id = nuevoId(),
                ventaId = ventaId,
                productoId = productoId,
                nombreProducto = producto.nombre,
                tipoVenta = producto.tipoVenta,
                cantidadMilesimas = cantidad,
                precioUnitarioCentavos = producto.precioCentavos,
                importeCentavos = Dinero.importe(cantidad, producto.precioCentavos),
                creadoEn = ahora,
            ) to producto.existenciaMilesimas
        }

        val subtotal = detalles.sumOf { it.first.importeCentavos }
        val descuento = solicitud.descuento?.centavos ?: 0L
        val total = subtotal - descuento
        // Defensa de datos; los mensajes y límites finos (1 %–99 %, < subtotal) los valida el dominio.
        if (descuento < 0 || (descuento > 0 && total < CENTAVOS_TOTAL_MINIMO_CON_DESCUENTO)) {
            fallar(FallaDatos.DescuentoInvalido)
        }

        val recibido: Long?
        val cambio: Long?
        if (forma.esEfectivo) {
            recibido = solicitud.montoRecibidoCentavos ?: fallar(FallaDatos.MontoRecibidoInsuficiente)
            if (recibido < total) fallar(FallaDatos.MontoRecibidoInsuficiente)
            cambio = recibido - total
        } else {
            recibido = null
            cambio = null
        }

        val venta = Venta(
            id = ventaId,
            cajaId = caja.id,
            fechaHora = ahora,
            subtotalCentavos = subtotal,
            tipoDescuento = solicitud.descuento?.tipo,
            valorDescuento = solicitud.descuento?.valor,
            descuentoCentavos = descuento,
            totalCentavos = total,
            formaPagoId = forma.id,
            montoRecibidoCentavos = recibido,
            cambioCentavos = cambio,
            creadoEn = ahora,
            actualizadoEn = ahora,
        )
        ventaDao.insertar(venta)
        ventaDao.insertarDetalles(detalles.map { it.first })

        val existencias = mutableMapOf<String, Long>()
        for ((detalle, existenciaAnterior) in detalles) {
            if (productoDao.descontarExistencia(detalle.productoId, detalle.cantidadMilesimas, ahora) == 0) {
                fallar(
                    FallaDatos.ExistenciaInsuficiente(
                        detalle.productoId, detalle.nombreProducto, detalle.tipoVenta, existenciaAnterior,
                    ),
                )
            }
            val resultante = existenciaAnterior - detalle.cantidadMilesimas
            existencias[detalle.productoId] = resultante
            movimientoDao.insertar(
                MovimientoInventario(
                    id = nuevoId(), productoId = detalle.productoId, fechaHora = ahora,
                    tipo = TipoMovimiento.VENTA, cantidadMilesimas = -detalle.cantidadMilesimas,
                    existenciaResultanteMilesimas = resultante, ventaId = ventaId, creadoEn = ahora,
                ),
            )
        }
        cajaDao.invalidarCorte(caja.id, ahora)

        VentaCobrada(venta, detalles.map { it.first }, existencias)
    }

    /**
     * RF 3.3: solo ventas registradas de la caja abierta. Cambia el estado a "Cancelada" y regresa
     * las cantidades a la existencia con un movimiento CANCELACION por producto.
     */
    override suspend fun cancelar(ventaId: String): Resultado<Venta> = db.transaccion {
        val venta = ventaDao.porId(ventaId) ?: fallar(FallaDatos.NoEncontrado)
        val caja = cajaDao.porId(venta.cajaId) ?: fallar(FallaDatos.NoEncontrado)
        if (venta.estado != EstadoVenta.REGISTRADA || caja.estado != EstadoCaja.ABIERTA) {
            fallar(FallaDatos.VentaNoCancelable)
        }
        val ahora = reloj.ahora()
        if (ventaDao.marcarCancelada(ventaId, ahora) == 0) fallar(FallaDatos.VentaNoCancelable)

        for (detalle in ventaDao.detalles(ventaId)) {
            productoDao.sumarExistencia(detalle.productoId, detalle.cantidadMilesimas, ahora)
            val existencia = productoDao.porId(detalle.productoId)?.existenciaMilesimas
                ?: fallar(FallaDatos.NoEncontrado)
            movimientoDao.insertar(
                MovimientoInventario(
                    id = nuevoId(), productoId = detalle.productoId, fechaHora = ahora,
                    tipo = TipoMovimiento.CANCELACION, cantidadMilesimas = detalle.cantidadMilesimas,
                    existenciaResultanteMilesimas = existencia, ventaId = ventaId, creadoEn = ahora,
                ),
            )
        }
        cajaDao.invalidarCorte(caja.id, ahora)
        ventaDao.porId(ventaId) ?: fallar(FallaDatos.NoEncontrado)
    }

    /** ¿Se puede cancelar? Para mostrar u ocultar la opción en el detalle (RF 3.3). */
    suspend fun esCancelable(ventaId: String): Boolean {
        val venta = ventaDao.porId(ventaId) ?: return false
        val caja = cajaDao.porId(venta.cajaId) ?: return false
        return venta.estado == EstadoVenta.REGISTRADA && caja.estado == EstadoCaja.ABIERTA
    }

    private companion object {
        /** RF 3.6: con descuento, el total a cobrar debe ser al menos $1.00. */
        const val CENTAVOS_TOTAL_MINIMO_CON_DESCUENTO = 100L
    }
}
