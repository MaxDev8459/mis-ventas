package mx.ambulantpoint.misventas.domain.venta

import java.text.Normalizer

/** Chip elegido en la barra de Vender. */
sealed interface FiltroVenta {
    data object Todos : FiltroVenta
    data object Favoritos : FiltroVenta
    data class DeCategoria(val categoriaId: String) : FiltroVenta
}

/** Producto en la lista de Vender, con lo necesario para filtrar. */
data class ProductoListado(
    val producto: ProductoEnVenta,
    val categoriaId: String,
    val categoriaNombre: String,
    val favorito: Boolean,
    val imagenRuta: String,
)

data class CategoriaChip(val id: String, val nombre: String)

/**
 * Encontrar productos en Vender: búsqueda por nombre (sin importar acentos ni mayúsculas), chips de
 * "Todos", "Favoritos" y categorías, y los favoritos primero. La lista llega en orden alfabético.
 */
object ReglaFiltroVenta {

    /** Solo las categorías que tienen productos para vender, en orden alfabético. */
    fun categorias(lista: List<ProductoListado>): List<CategoriaChip> =
        lista.map { CategoriaChip(it.categoriaId, it.categoriaNombre) }
            .distinctBy { it.id }
            .sortedBy { normalizar(it.nombre) }

    /** Si la categoría elegida ya no tiene productos para vender, se regresa a "Todos". */
    fun vigente(filtro: FiltroVenta, lista: List<ProductoListado>): FiltroVenta =
        if (filtro is FiltroVenta.DeCategoria && lista.none { it.categoriaId == filtro.categoriaId }) FiltroVenta.Todos else filtro

    fun aplicar(lista: List<ProductoListado>, texto: String, filtro: FiltroVenta): List<ProductoListado> {
        val buscado = normalizar(texto)
        return lista
            .filter { buscado.isEmpty() || normalizar(it.producto.nombre).contains(buscado) }
            .filter {
                when (filtro) {
                    FiltroVenta.Todos -> true
                    FiltroVenta.Favoritos -> it.favorito
                    is FiltroVenta.DeCategoria -> it.categoriaId == filtro.categoriaId
                }
            }
            // sortedBy es estable: dentro de cada grupo se conserva el orden alfabético.
            .sortedBy { !it.favorito }
    }

    /** "Café " → "cafe": minúsculas, sin acentos y sin espacios en los extremos. */
    fun normalizar(t: String): String =
        Normalizer.normalize(t.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
