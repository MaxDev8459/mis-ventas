package mx.ambulantpoint.misventas.domain.venta

import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.nuevoId

/** Un producto de la venta cancelada: qué se vendió y cuánto. */
data class LineaACorregir(
    /** null = monto libre ("Varios"): regresa al carrito con su descripción e importe. */
    val productoId: String?,
    val nombre: String,
    val cantidadMilesimas: Long,
    val importeCentavos: Long = 0,
)

/** Carrito armado para recapturar y los productos que ya no se pueden vender. */
data class Correccion(val carrito: Carrito, val omitidos: List<String>)

/**
 * "Corregir venta" = cancelar y recapturar. Después de cancelar (la existencia ya regresó), los
 * productos de la venta se cargan en un carrito nuevo, con el precio y la existencia de hoy.
 * Un producto inactivo o sin existencia suficiente queda fuera y se avisa al vendedor.
 */
object ReglaCorreccion {
    /** @param productoActual producto tal como está hoy en el catálogo; null si está inactivo o no existe. */
    fun armar(lineas: List<LineaACorregir>, productoActual: (String) -> ProductoEnVenta?): Correccion {
        var carrito = Carrito()
        val omitidos = mutableListOf<String>()
        for (l in lineas) {
            if (l.productoId == null) {
                (carrito.agregarLibre(nuevoId(), l.nombre, l.importeCentavos) as? Resultado.Exito)?.let { carrito = it.valor }
                continue
            }
            val p = productoActual(l.productoId)
            if (p == null) { omitidos += l.nombre; continue }
            when (val r = carrito.agregar(p, l.cantidadMilesimas)) {
                is Resultado.Exito -> carrito = r.valor
                is Resultado.Fallo -> omitidos += l.nombre
            }
        }
        return Correccion(carrito, omitidos)
    }
}
