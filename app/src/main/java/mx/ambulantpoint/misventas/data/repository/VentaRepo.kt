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
import mx.ambulantpoint.misventas.data.local.entity.Abono
import mx.ambulantpoint.misventas.data.local.entity.AplicacionAbono
import mx.ambulantpoint.misventas.data.local.entity.TipoAbono
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.domain.regla.ReglaFiado
import mx.ambulantpoint.misventas.domain.regla.ReglaApartado
import mx.ambulantpoint.misventas.data.local.entity.EstadoApartado
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

/** Venta rápida: monto libre sin producto ("Varios" si no lleva descripción). */
data class LineaLibre(val descripcion: String, val importeCentavos: Long)

/** Fiado: cliente y pago inicial opcional (se registra como primer abono de esta venta). */
data class DatosFiado(
    val clienteId: String,
    val pagoInicialCentavos: Long = 0,
    /** Obligatoria si hay pago inicial. */
    val formaPagoInicialId: String? = null,
)

data class SolicitudCobro(
    val lineas: List<LineaVenta>,
    /** null solo si la venta es fiada. */
    val formaPagoId: String?,
    /** Solo en efectivo. */
    val montoRecibidoCentavos: Long? = null,
    val descuento: DescuentoVenta? = null,
    val libres: List<LineaLibre> = emptyList(),
    val fiado: DatosFiado? = null,
    /** Apartados: entrega de este apartado (sus productos y precios fijos; las `lineas` se ignoran). */
    val apartadoId: String? = null,
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
    private val fiadoDao = db.fiadoDao()
    private val apartadoDao = db.apartadoDao()

    /** Lo abonado (abonos no cancelados) a una venta fiada: lo que se devolvería al cancelarla. */
    suspend fun abonadoAVenta(ventaId: String): Long = fiadoDao.abonadoAVenta(ventaId) ?: 0L

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
        val caja = cajaDao.abierta() ?: fallar(FallaDatos.SinCajaAbierta)
        // Entrega de un apartado: sus productos con el precio fijo del día en que se apartó.
        val apartado = solicitud.apartadoId?.let { id ->
            if (solicitud.fiado != null) fallar(FallaDatos.Regla(Mensajes.Apartado.NO_FIAR_APARTADO))
            if (solicitud.libres.isNotEmpty()) fallar(FallaDatos.Regla(Mensajes.Apartado.NO_APARTAR_VARIOS))
            val a = apartadoDao.porId(id) ?: fallar(FallaDatos.NoEncontrado)
            if (a.estado != EstadoApartado.PENDIENTE) fallar(FallaDatos.Regla(Mensajes.Apartado.NO_PENDIENTE))
            a
        }
        val detallesApartado = apartado?.let { apartadoDao.detalles(it.id) }
        if (solicitud.lineas.isEmpty() && solicitud.libres.isEmpty() && detallesApartado.isNullOrEmpty()) {
            fallar(FallaDatos.VentaSinProductos)
        }
        val fiado = solicitud.fiado
        val forma = if (fiado != null) {
            // Respuesta 6 del cliente: una venta con monto libre no se fía.
            if (solicitud.libres.isNotEmpty()) fallar(FallaDatos.Regla(Mensajes.Fiado.NO_FIAR_VARIOS))
            fiadoDao.cliente(fiado.clienteId) ?: fallar(FallaDatos.Regla(Mensajes.Fiado.ELIJA_CLIENTE))
            null
        } else {
            val f = solicitud.formaPagoId?.let { db.formaPagoDao().porId(it) }
            if (f == null || !f.activa) fallar(FallaDatos.FormaPagoNoDisponible)
            f
        }

        val ahora = reloj.ahora()
        val ventaId = nuevoId()

        // Si el mismo producto viene en varias líneas, se suma para validar contra la existencia.
        val cantidades = detallesApartado?.groupBy { it.productoId }?.mapValues { (_, l) -> l.sumOf { it.cantidadMilesimas } }
            ?: solicitud.lineas.groupBy { it.productoId }.mapValues { (_, lineas) -> lineas.sumOf { it.cantidadMilesimas } }
        val precioFijo = detallesApartado?.associate { it.productoId to it.precioUnitarioCentavos }.orEmpty()

        val detalles = cantidades.map { (productoId, cantidad) ->
            val producto = productoDao.porId(productoId) ?: fallar(FallaDatos.NoEncontrado)
            // Un apartado se entrega aunque el producto se haya desactivado después de apartarlo.
            if (!producto.activo && apartado == null) fallar(FallaDatos.ProductoNoDisponible(productoId))
            if (cantidad <= 0 || !cantidadValida(producto.tipoVenta, cantidad)) fallar(FallaDatos.CantidadInvalida)
            // Apartados: solo se vende lo disponible (lo de otros apartados pendientes no).
            val disponible = ReglaApartado.disponible(
                producto.existenciaMilesimas, apartadoDao.apartadoDe(productoId, excepto = apartado?.id.orEmpty()),
            )
            if (cantidad > disponible) {
                fallar(FallaDatos.ExistenciaInsuficiente(productoId, producto.nombre, producto.tipoVenta, disponible))
            }
            val precio = precioFijo[productoId] ?: producto.precioCentavos
            VentaDetalle(
                id = nuevoId(),
                ventaId = ventaId,
                productoId = productoId,
                nombreProducto = producto.nombre,
                tipoVenta = producto.tipoVenta,
                cantidadMilesimas = cantidad,
                precioUnitarioCentavos = precio,
                importeCentavos = Dinero.importe(cantidad, precio),
                creadoEn = ahora,
            ) to producto.existenciaMilesimas
        }

        // Venta rápida: renglones sin producto (no mueven inventario).
        val libres = solicitud.libres.map { l ->
            if (l.importeCentavos <= 0) fallar(FallaDatos.CantidadInvalida)
            VentaDetalle(
                id = nuevoId(), ventaId = ventaId, productoId = null, nombreProducto = ReglaFiado.descripcionLibre(l.descripcion),
                tipoVenta = TipoVenta.UNIDAD, cantidadMilesimas = Dinero.MILESIMAS_POR_UNIDAD,
                precioUnitarioCentavos = l.importeCentavos, importeCentavos = l.importeCentavos, creadoEn = ahora,
            )
        }

        val subtotal = detalles.sumOf { it.first.importeCentavos } + libres.sumOf { it.importeCentavos }
        val descuento = solicitud.descuento?.centavos ?: 0L
        val total = subtotal - descuento
        // Defensa de datos; los mensajes y límites finos (1 %–99 %, < subtotal) los valida el dominio.
        if (descuento < 0 || (descuento > 0 && total < CENTAVOS_TOTAL_MINIMO_CON_DESCUENTO)) {
            fallar(FallaDatos.DescuentoInvalido)
        }

        val recibido: Long?
        val cambio: Long?
        if (forma != null && forma.esEfectivo) {
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
            formaPagoId = forma?.id,
            montoRecibidoCentavos = recibido,
            cambioCentavos = cambio,
            clienteId = fiado?.clienteId,
            creadoEn = ahora,
            actualizadoEn = ahora,
        )
        ventaDao.insertar(venta)
        ventaDao.insertarDetalles(detalles.map { it.first } + libres)

        // Pago inicial del fiado: primer abono, aplicado completo a esta venta y en el corte de hoy.
        if (fiado != null && fiado.pagoInicialCentavos != 0L) {
            ReglaFiado.validarPagoInicial(fiado.pagoInicialCentavos, total)?.let { fallar(FallaDatos.Regla(it)) }
            val formaInicial = fiado.formaPagoInicialId?.let { db.formaPagoDao().porId(it) }
            if (formaInicial == null || !formaInicial.activa) fallar(FallaDatos.FormaPagoNoDisponible)
            val abonoId = nuevoId()
            fiadoDao.insertarAbono(
                Abono(
                    id = abonoId, clienteId = fiado.clienteId, cajaId = caja.id, fechaHora = ahora, tipo = TipoAbono.ABONO,
                    montoCentavos = fiado.pagoInicialCentavos, formaPagoId = formaInicial.id, creadoEn = ahora, actualizadoEn = ahora,
                ),
            )
            fiadoDao.insertarAplicaciones(
                listOf(AplicacionAbono(nuevoId(), abonoId, ventaId, fiado.pagoInicialCentavos, ahora)),
            )
        }

        val existencias = mutableMapOf<String, Long>()
        for ((detalle, existenciaAnterior) in detalles) {
            val productoId = detalle.productoId!!
            if (productoDao.descontarExistencia(productoId, detalle.cantidadMilesimas, ahora) == 0) {
                fallar(
                    FallaDatos.ExistenciaInsuficiente(
                        productoId, detalle.nombreProducto, detalle.tipoVenta, existenciaAnterior,
                    ),
                )
            }
            val resultante = existenciaAnterior - detalle.cantidadMilesimas
            existencias[productoId] = resultante
            movimientoDao.insertar(
                MovimientoInventario(
                    id = nuevoId(), productoId = productoId, fechaHora = ahora,
                    tipo = TipoMovimiento.VENTA, cantidadMilesimas = -detalle.cantidadMilesimas,
                    existenciaResultanteMilesimas = resultante, ventaId = ventaId, creadoEn = ahora,
                ),
            )
        }
        if (apartado != null && apartadoDao.terminar(apartado.id, EstadoApartado.ENTREGADO.name, ahora, ventaId) == 0) {
            fallar(FallaDatos.Regla(Mensajes.Apartado.NO_PENDIENTE))
        }
        cajaDao.invalidarCorte(caja.id, ahora)

        VentaCobrada(venta, detalles.map { it.first } + libres, existencias)
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

        // Lo abonado a una venta fiada se devuelve en efectivo en esta caja (respuestas 4 y 4b).
        if (venta.clienteId != null) {
            val abonado = fiadoDao.abonadoAVenta(ventaId) ?: 0L
            if (abonado > 0) {
                val efectivo = db.formaPagoDao().efectivo() ?: fallar(FallaDatos.FormaPagoNoDisponible)
                fiadoDao.insertarAbono(
                    Abono(
                        id = nuevoId(), clienteId = venta.clienteId, cajaId = caja.id, fechaHora = ahora,
                        tipo = TipoAbono.DEVOLUCION, montoCentavos = abonado, formaPagoId = efectivo.id, ventaId = ventaId,
                        creadoEn = ahora, actualizadoEn = ahora,
                    ),
                )
            }
        }

        for (detalle in ventaDao.detalles(ventaId)) {
            // Monto libre ("Varios"): no movió inventario, no regresa nada.
            val productoId = detalle.productoId ?: continue
            productoDao.sumarExistencia(productoId, detalle.cantidadMilesimas, ahora)
            val existencia = productoDao.porId(productoId)?.existenciaMilesimas
                ?: fallar(FallaDatos.NoEncontrado)
            movimientoDao.insertar(
                MovimientoInventario(
                    id = nuevoId(), productoId = productoId, fechaHora = ahora,
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
