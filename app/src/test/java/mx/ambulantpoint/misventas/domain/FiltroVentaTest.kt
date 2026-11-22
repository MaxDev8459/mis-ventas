package mx.ambulantpoint.misventas.domain

import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.venta.CategoriaChip
import mx.ambulantpoint.misventas.domain.venta.FiltroVenta
import mx.ambulantpoint.misventas.domain.venta.ProductoEnVenta
import mx.ambulantpoint.misventas.domain.venta.ProductoListado
import mx.ambulantpoint.misventas.domain.venta.ReglaFiltroVenta
import org.junit.Assert.assertEquals
import org.junit.Test

/** Encontrar productos en Vender: búsqueda, chips y favoritos primero. */
class FiltroVentaTest {
    private fun p(id: String, nombre: String, cat: String, catNombre: String, fav: Boolean = false) =
        ProductoListado(ProductoEnVenta(id, nombre, TipoVenta.UNIDAD, 1_000, 5_000), cat, catNombre, fav, "")

    // Llega en orden alfabético, como la consulta de Room.
    private val lista = listOf(
        p("a", "Agua 1L", "beb", "Bebidas"),
        p("c", "Café de olla", "beb", "Bebidas", fav = true),
        p("f", "Flan", "pos", "Postres"),
        p("k", "Carlota", "pos", "Postres", fav = true),
    ).sortedBy { it.producto.nombre }

    private fun ids(r: List<ProductoListado>) = r.map { it.producto.id }

    @Test fun `todos muestra favoritos primero y luego el resto en orden alfabetico`() =
        assertEquals(listOf("c", "k", "a", "f"), ids(ReglaFiltroVenta.aplicar(lista, "", FiltroVenta.Todos)))

    @Test fun `favoritos muestra solo los favoritos`() =
        assertEquals(listOf("c", "k"), ids(ReglaFiltroVenta.aplicar(lista, "", FiltroVenta.Favoritos)))

    @Test fun `categoria muestra solo sus productos con favoritos primero`() =
        assertEquals(listOf("k", "f"), ids(ReglaFiltroVenta.aplicar(lista, "", FiltroVenta.DeCategoria("pos"))))

    @Test fun `la busqueda ignora acentos y mayusculas`() {
        assertEquals(listOf("c"), ids(ReglaFiltroVenta.aplicar(lista, "CAFE", FiltroVenta.Todos)))
        assertEquals(listOf("c"), ids(ReglaFiltroVenta.aplicar(lista, " café ", FiltroVenta.Todos)))
    }

    @Test fun `la busqueda se combina con el chip elegido`() =
        assertEquals(emptyList<String>(), ids(ReglaFiltroVenta.aplicar(lista, "flan", FiltroVenta.DeCategoria("beb"))))

    @Test fun `categorias solo las que tienen productos en orden alfabetico`() =
        assertEquals(
            listOf(CategoriaChip("beb", "Bebidas"), CategoriaChip("pos", "Postres")),
            ReglaFiltroVenta.categorias(lista),
        )

    @Test fun `si la categoria elegida se queda sin productos regresa a todos`() {
        assertEquals(FiltroVenta.Todos, ReglaFiltroVenta.vigente(FiltroVenta.DeCategoria("dul"), lista))
        assertEquals(FiltroVenta.DeCategoria("pos"), ReglaFiltroVenta.vigente(FiltroVenta.DeCategoria("pos"), lista))
    }
}
