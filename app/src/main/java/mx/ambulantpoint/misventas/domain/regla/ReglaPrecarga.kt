package mx.ambulantpoint.misventas.domain.regla

import mx.ambulantpoint.misventas.core.Dinero
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta

/** Resultado de aplicar una precarga: cantidades para la pantalla y productos que no se pudieron cargar. */
data class PrecargaAplicada(val cantidades: Map<String, Long>, val omitidos: List<String>)

/**
 * Precargas de la carga del día (desarrollo independiente, v6): cargas con nombre, "N de cada
 * producto" y el control + / − (por pieza de 1 en 1; por kilo según la unidad configurada).
 */
object ReglaPrecarga {
    /** Unidades para kilo que se pueden elegir, en milésimas de kg (gramos). */
    val PASOS_KILO = listOf(50L, 100L, 250L, 500L)

    /** {id → milésimas} → "id=milésimas;id=milésimas". Solo cantidades mayores a cero. */
    fun codificar(cantidades: Map<String, Long>): String =
        cantidades.filterValues { it > 0 }.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }

    /** Inverso de [codificar]; ignora pedazos que no se entiendan. */
    fun decodificar(texto: String): Map<String, Long> =
        texto.split(";").mapNotNull { parte ->
            val i = parte.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val cantidad = parte.substring(i + 1).toLongOrNull() ?: return@mapNotNull null
            if (cantidad <= 0) null else parte.substring(0, i) to cantidad
        }.toMap()

    /**
     * Aplica una precarga a los productos que hoy se pueden cargar: sin pasar de lo disponible.
     * Lo que ya no está (inactivo o sin existencia) se omite. [nombreDe] da el nombre para avisar.
     */
    fun aplicar(precarga: Map<String, Long>, productos: List<ProductoParaCarga>, nombreDe: (String) -> String?): PrecargaAplicada {
        val porId = productos.associateBy { it.id }
        val cantidades = productos.associate { it.id to 0L }.toMutableMap()
        val omitidos = mutableListOf<String>()
        for ((id, cantidad) in precarga) {
            val p = porId[id]
            if (p == null || p.existenciaMilesimas <= 0) {
                nombreDe(id)?.let { omitidos += it }
                continue
            }
            cantidades[id] = minOf(cantidad, p.existenciaMilesimas)
        }
        return PrecargaAplicada(cantidades, omitidos.sorted())
    }

    /** "N de cada producto": N piezas o N × unidad para kilo, sin pasar de lo disponible. */
    fun nDeCada(n: Long, pasoKilo: Long, productos: List<ProductoParaCarga>): Map<String, Long> =
        productos.associate { p ->
            val pedido = if (p.tipoVenta == TipoVenta.UNIDAD) n * Dinero.MILESIMAS_POR_UNIDAD else n * pasoKilo
            p.id to minOf(maxOf(0, pedido), p.existenciaMilesimas)
        }

    /** Paso del + / −: 1 pieza, o la unidad para kilo. */
    fun paso(tipoVenta: TipoVenta, pasoKilo: Long): Long =
        if (tipoVenta == TipoVenta.UNIDAD) Dinero.MILESIMAS_POR_UNIDAD else pasoKilo

    /** + : sube un paso sin pasar del máximo. */
    fun mas(actual: Long, tipoVenta: TipoVenta, pasoKilo: Long, maximo: Long): Long =
        minOf(actual + paso(tipoVenta, pasoKilo), maximo).coerceAtLeast(0)

    /** − : baja un paso sin bajar de cero. */
    fun menos(actual: Long, tipoVenta: TipoVenta, pasoKilo: Long): Long =
        maxOf(0, actual - paso(tipoVenta, pasoKilo))
}
