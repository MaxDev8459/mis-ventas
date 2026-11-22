package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.LectorNumeros
import mx.ambulantpoint.misventas.core.Lectura
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.local.entity.TipoDescuento
import mx.ambulantpoint.misventas.data.local.entity.Venta
import mx.ambulantpoint.misventas.data.repository.CorteCaja
import mx.ambulantpoint.misventas.data.repository.DescuentoVenta
import mx.ambulantpoint.misventas.data.repository.LineaVenta
import mx.ambulantpoint.misventas.data.repository.SolicitudCobro
import mx.ambulantpoint.misventas.data.repository.DatosFiado
import mx.ambulantpoint.misventas.data.repository.LineaLibre
import mx.ambulantpoint.misventas.domain.regla.ReglaFiado
import mx.ambulantpoint.misventas.data.repository.VentaCobrada
import mx.ambulantpoint.misventas.domain.puertos.PuertoCaja
import mx.ambulantpoint.misventas.domain.puertos.PuertoVentas
import mx.ambulantpoint.misventas.domain.regla.FallaMensaje
import mx.ambulantpoint.misventas.domain.regla.ReglaCobro
import mx.ambulantpoint.misventas.domain.regla.ReglaCorte
import mx.ambulantpoint.misventas.domain.regla.ReglaDescuento
import mx.ambulantpoint.misventas.domain.regla.TipoDiferencia
import mx.ambulantpoint.misventas.domain.venta.Carrito

/** RF 5.1. */
class AbrirCaja(private val caja: PuertoCaja) {
    suspend operator fun invoke(fondo: String): Resultado<Hecho<Caja>> {
        val e = Errores()
        val centavos = e.leer(Campos.FONDO, LectorNumeros.monto(fondo, permitirCero = true))
        if (e.hay) return e.falla()
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { caja.abrir(centavos!!) }) {
            Hecho(it, Mensajes.CAJA_ABIERTA)
        }
    }
}

/**
 * Fiado tal como se captura al cobrar: cliente elegido, pago inicial opcional ("" = $0) y su forma de
 * pago (obligatoria solo si hay pago inicial).
 */
data class FiadoCapturado(val clienteId: String?, val pagoInicial: String, val formaInicial: FormaPago?)

/** Descuento tal como lo captura el vendedor (RF 3.6): "10" = 10 %, o "$15.00". */
data class DescuentoCapturado(val tipo: TipoDescuento, val valor: String)

/** Lo que la pantalla de cobro muestra antes de confirmar (RF 3.2 / 3.6). */
data class ResumenCobro(
    val subtotalCentavos: Long,
    val descuento: DescuentoVenta?,
    val totalCentavos: Long,
    /** Solo en efectivo y con monto capturado válido. */
    val cambioCentavos: Long?,
) {
    val descuentoCentavos: Long get() = descuento?.centavos ?: 0
}

/**
 * RF 3.2: calcula subtotal, descuento, total y cambio, y valida antes de guardar.
 * La pantalla lo llama en cada cambio para mostrar el cambio a entregar antes de confirmar.
 */
object PrepararCobro {
    fun calcular(
        carrito: Carrito,
        forma: FormaPago?,
        montoRecibido: String?,
        descuento: DescuentoCapturado?,
        fiar: Boolean = false,
    ): Resultado<ResumenCobro> {
        if (carrito.estaVacio) return mensaje(Mensajes.AGREGUE_UN_PRODUCTO)
        if (fiar && carrito.tieneLibres) return mensaje(Mensajes.Fiado.NO_FIAR_VARIOS)
        val subtotal = carrito.subtotalCentavos

        val desc = descuento?.let { d ->
            val valor = leerDescuento(d) ?: return campo(Campos.DESCUENTO, Mensajes.DESCUENTO_NO_VALIDO)
            when (val r = ReglaDescuento.calcular(subtotal, d.tipo, valor)) {
                is Resultado.Exito -> DescuentoVenta(d.tipo, valor, r.valor)
                is Resultado.Fallo -> return campo(
                    Campos.DESCUENTO,
                    (r.falla as? FallaMensaje)?.mensaje ?: Mensajes.DESCUENTO_NO_VALIDO,
                )
            }
        }
        val total = subtotal - (desc?.centavos ?: 0)

        // Fiado: sin forma de pago ni cambio (el pago inicial se valida en CobrarVenta).
        if (fiar) return Resultado.Exito(ResumenCobro(subtotal, desc, total, null))
        if (forma == null) return campo(Campos.FORMA_PAGO, Mensajes.Campo.OBLIGATORIO)
        val cambio = if (forma.esEfectivo) {
            val recibido = when (val l = LectorNumeros.monto(montoRecibido, permitirCero = true)) {
                is Lectura.Valor -> l.valor
                is Lectura.Error -> return campo(Campos.MONTO_RECIBIDO, l.mensaje)
            }
            when (val c = ReglaCobro.cambio(total, recibido)) {
                is Resultado.Exito -> c.valor
                is Resultado.Fallo -> return campo(Campos.MONTO_RECIBIDO, Mensajes.MONTO_RECIBIDO_MENOR)
            }
        } else {
            null
        }
        return Resultado.Exito(ResumenCobro(subtotal, desc, total, cambio))
    }

    /** Porcentaje → centésimas (12.5 → 1250); monto fijo → centavos. */
    private fun leerDescuento(d: DescuentoCapturado): Long? = when (val l = LectorNumeros.monto(d.valor)) {
        is Lectura.Valor -> l.valor
        is Lectura.Error -> null
    }
}

/** RF 3.2: confirma el cobro y guarda la venta (todo o nada). */
class CobrarVenta(
    private val ventas: PuertoVentas,
    private val existenciaBaja: EvaluarExistenciaBaja,
) {
    suspend operator fun invoke(
        carrito: Carrito,
        forma: FormaPago?,
        montoRecibido: String? = null,
        descuento: DescuentoCapturado? = null,
        fiado: FiadoCapturado? = null,
        /** Apartados: se entrega este apartado (sus precios fijos van en el carrito). */
        apartadoId: String? = null,
    ): Resultado<Hecho<VentaCobrada>> {
        val resumen = when (val r = PrepararCobro.calcular(carrito, forma, montoRecibido, descuento, fiar = fiado != null)) {
            is Resultado.Exito -> r.valor
            is Resultado.Fallo -> return r
        }
        val datosFiado = fiado?.let { f ->
            val clienteId = f.clienteId ?: return campo(Campos.CLIENTE, Mensajes.Fiado.ELIJA_CLIENTE)
            val pago = when (val l = LectorNumeros.monto(f.pagoInicial.ifBlank { "0" }, permitirCero = true)) {
                is Lectura.Valor -> l.valor
                is Lectura.Error -> return campo(Campos.PAGO_INICIAL, l.mensaje)
            }
            ReglaFiado.validarPagoInicial(pago, resumen.totalCentavos)?.let { return campo(Campos.PAGO_INICIAL, it) }
            if (pago > 0 && f.formaInicial == null) return campo(Campos.FORMA_PAGO_INICIAL, Mensajes.Campo.OBLIGATORIO)
            DatosFiado(clienteId, pago, if (pago > 0) f.formaInicial?.id else null)
        }
        val solicitud = SolicitudCobro(
            lineas = carrito.renglones.map { LineaVenta(it.producto.id, it.cantidadMilesimas) },
            formaPagoId = if (datosFiado != null) null else forma!!.id,
            montoRecibidoCentavos = if (datosFiado == null && forma!!.esEfectivo) resumen.totalCentavos + (resumen.cambioCentavos ?: 0) else null,
            descuento = resumen.descuento,
            libres = carrito.libres.map { LineaLibre(it.descripcion, it.importeCentavos) },
            fiado = datosFiado,
            apartadoId = apartadoId,
        )
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { ventas.cobrar(solicitud) }) { cobrada ->
            val avisos = carrito.renglones.mapNotNull { r ->
                val existencia = cobrada.existencias[r.producto.id] ?: return@mapNotNull null
                existenciaBaja.aviso(ProductoRef(r.producto.id, r.producto.nombre, r.producto.tipoVenta), existencia)
            }
            val mensajeOk = when {
                apartadoId != null -> Mensajes.Apartado.APARTADO_ENTREGADO
                datosFiado != null -> Mensajes.Fiado.VENTA_FIADA
                else -> Mensajes.VENTA_REGISTRADA
            }
            Hecho(cobrada, mensajeOk, avisos)
        }
    }
}

/** RF 3.3. */
class CancelarVenta(private val ventas: PuertoVentas) {
    /** Fiado: si la venta tiene abonos, avisa que se devuelven en efectivo. */
    fun confirmacion(venta: Venta, abonadoCentavos: Long = 0): String =
        Mensajes.confirmarCancelarVenta(Formato.moneda(venta.totalCentavos)) +
            (if (abonadoCentavos > 0) Mensajes.Fiado.devolucionAlCancelar(Formato.moneda(abonadoCentavos)) else "")

    suspend operator fun invoke(ventaId: String): Resultado<Hecho<Venta>> =
        ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { ventas.cancelar(ventaId) }) {
            Hecho(it, Mensajes.VENTA_CANCELADA)
        }
}

/** Corte listo para mostrar (RF 5.2). */
data class CorteCalculado(val corte: CorteCaja, val tipoDiferencia: TipoDiferencia)

/** RF 5.2: no cierra la caja. */
class CalcularCorte(private val caja: PuertoCaja) {
    suspend operator fun invoke(efectivoContado: String): Resultado<Hecho<CorteCalculado>> {
        val e = Errores()
        val contado = e.leer(Campos.EFECTIVO_CONTADO, LectorNumeros.monto(efectivoContado, permitirCero = true))
        if (e.hay) return e.falla()
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { caja.calcularCorte(contado!!) }) {
            Hecho(CorteCalculado(it, ReglaCorte.tipoDiferencia(it.diferenciaCentavos)))
        }
    }
}

/** RF 5.3. La confirmación es [Mensajes.CONFIRMAR_CERRAR_CAJA]. */
class CerrarCaja(private val caja: PuertoCaja) {
    suspend operator fun invoke(): Resultado<Hecho<Caja>> =
        ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { caja.cerrar() }) {
            Hecho(it, Mensajes.CAJA_CERRADA)
        }
}
