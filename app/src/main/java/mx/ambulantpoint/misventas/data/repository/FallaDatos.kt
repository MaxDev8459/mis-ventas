package mx.ambulantpoint.misventas.data.repository

import mx.ambulantpoint.misventas.core.Falla
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta

/**
 * Motivos por los que la capa de datos rechaza una operación. Aquí no hay textos: el dominio los
 * traduce a los mensajes literales del DFR (core/Mensajes.kt, Fase 2).
 */
sealed interface FallaDatos : Falla {
    /** RF 2.1 / 2.2 / 2.6 / 2.8 / 3.5. */
    data object NombreDuplicado : FallaDatos
    data object NoEncontrado : FallaDatos

    /** RF 2.9. */
    data object CategoriaConProductos : FallaDatos

    /** RF 3.5: "Efectivo" no se desactiva ni se renombra (solo las adicionales). */
    data object EfectivoNoModificable : FallaDatos

    /** RF 2.2: el tipo de venta solo cambia si la existencia es cero. */
    data object TipoVentaNoModificable : FallaDatos

    /** RF 3.1 / 5.1: "Abra caja para comenzar a vender". */
    data object SinCajaAbierta : FallaDatos

    /** RF 5.1: solo una caja abierta a la vez. */
    data object CajaYaAbierta : FallaDatos

    /** RF 3.1: "Agregue al menos un producto a la venta". */
    data object VentaSinProductos : FallaDatos

    /** RF 3.1: "Solo hay [existencia] [pzas/kg] disponibles de [producto]". */
    data class ExistenciaInsuficiente(
        val productoId: String,
        val nombreProducto: String,
        val tipoVenta: TipoVenta,
        val disponibleMilesimas: Long,
    ) : FallaDatos

    /** Producto inactivo o con existencia cero: no se puede vender (RF 3.1). */
    data class ProductoNoDisponible(val productoId: String) : FallaDatos

    /** RF 3.2: forma de pago inexistente o inactiva. */
    data object FormaPagoNoDisponible : FallaDatos

    /** RF 3.2: "El monto recibido es menor al total de la venta". */
    data object MontoRecibidoInsuficiente : FallaDatos

    /** RF 3.6: el descuento no puede dejar el total por debajo de $1.00 (validación fina en dominio). */
    data object DescuentoInvalido : FallaDatos

    /** RF 3.3: venta ya cancelada o de una caja cerrada. */
    data object VentaNoCancelable : FallaDatos

    /** RF 4.2: "La cantidad a disminuir es mayor a la existencia actual". */
    data object DisminucionMayorAExistencia : FallaDatos

    /** RF 5.3: falta el corte o quedó desactualizado por ventas/cancelaciones posteriores. */
    data object CorteNoVigente : FallaDatos

    /** Cantidad o monto fuera de rango (ej. cero o negativo). */
    data object CantidadInvalida : FallaDatos
}

/** Se lanza dentro de una transacción para revertirla completa y se convierte en Resultado.Fallo. */
internal class FallaDatosException(val falla: FallaDatos) : RuntimeException(falla.toString())
