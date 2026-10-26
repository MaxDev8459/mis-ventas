package mx.ambulantpoint.misventas.domain.venta

import mx.ambulantpoint.misventas.core.Dinero
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.regla.fallo

/** Lo que la pantalla de venta necesita saber de un producto. */
data class ProductoEnVenta(
    val id: String,
    val nombre: String,
    val tipoVenta: TipoVenta,
    val precioCentavos: Long,
    val existenciaMilesimas: Long,
)

data class RenglonCarrito(val producto: ProductoEnVenta, val cantidadMilesimas: Long) {
    val importeCentavos: Long get() = Dinero.importe(cantidadMilesimas, producto.precioCentavos)
}

/**
 * RF 3.1: venta en captura. Inmutable: cada operación devuelve un carrito nuevo, así el
 * ViewModel solo reemplaza el estado y el total se recalcula al instante.
 */
data class Carrito(val renglones: List<RenglonCarrito> = emptyList()) {

    val subtotalCentavos: Long get() = renglones.sumOf { it.importeCentavos }
    val estaVacio: Boolean get() = renglones.isEmpty()

    fun cantidadDe(productoId: String): Long = renglones.firstOrNull { it.producto.id == productoId }?.cantidadMilesimas ?: 0

    /**
     * Agrega o suma. Para un toque rápido en un producto por unidad, la cantidad por defecto es 1 pza.
     * No permite superar la existencia: "Solo hay [existencia] [pzas/kg] disponibles de [producto]".
     */
    fun agregar(producto: ProductoEnVenta, cantidadMilesimas: Long = Dinero.MILESIMAS_POR_UNIDAD): Resultado<Carrito> =
        fijar(producto, cantidadDe(producto.id) + cantidadMilesimas)

    /** Cambia la cantidad de un producto (0 lo quita). */
    fun fijar(producto: ProductoEnVenta, cantidadMilesimas: Long): Resultado<Carrito> {
        if (cantidadMilesimas <= 0) return Resultado.Exito(quitar(producto.id))
        if (producto.tipoVenta == TipoVenta.UNIDAD && cantidadMilesimas % Dinero.MILESIMAS_POR_UNIDAD != 0L) {
            return fallo(Mensajes.Campo.CANTIDAD_ENTERA)
        }
        if (cantidadMilesimas > producto.existenciaMilesimas) {
            return fallo(
                Mensajes.soloHayDisponibles(Formato.cantidad(producto.existenciaMilesimas, producto.tipoVenta), producto.nombre),
            )
        }
        val existe = renglones.any { it.producto.id == producto.id }
        val nuevos = if (existe) {
            renglones.map { if (it.producto.id == producto.id) RenglonCarrito(producto, cantidadMilesimas) else it }
        } else {
            renglones + RenglonCarrito(producto, cantidadMilesimas)
        }
        return Resultado.Exito(Carrito(nuevos))
    }

    fun quitar(productoId: String): Carrito = Carrito(renglones.filterNot { it.producto.id == productoId })
}
