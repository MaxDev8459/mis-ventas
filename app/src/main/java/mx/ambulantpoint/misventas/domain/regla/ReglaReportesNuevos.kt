package mx.ambulantpoint.misventas.domain.regla

import mx.ambulantpoint.misventas.core.Dinero
import mx.ambulantpoint.misventas.data.local.dao.ProductoVendido
import mx.ambulantpoint.misventas.data.local.dao.SalidaAjuste
import mx.ambulantpoint.misventas.data.local.dao.VentasPorGrupo
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import java.util.Calendar
import java.util.TimeZone

/*
 * Fase 5 (`docs/11-ESPEC-REPORTES.md`): reglas puras de los reportes nuevos, con prueba unitaria.
 */

/** Un dato comparado con el periodo anterior. `porcentajeDecimas` null = el periodo anterior fue 0. */
data class Diferencia(val actual: Long, val anterior: Long) {
    val diferencia: Long get() = actual - anterior
    val porcentajeDecimas: Long? get() = ReglaComparacion.porcentaje(actual, anterior)
}

/** Cuánto cambió lo vendido de un producto entre los dos periodos. */
data class CambioProducto(val productoId: String, val nombre: String, val actualCentavos: Long, val anteriorCentavos: Long) {
    val diferenciaCentavos: Long get() = actualCentavos - anteriorCentavos
}

object ReglaComparacion {
    const val PRODUCTOS = 5

    /**
     * Periodo anterior = **el mismo tramo** (respuesta del cliente): si el periodo llega hasta hoy, se compara solo
     * hasta esta misma hora. Hoy → ayer; semana → semana pasada; mes → mes pasado (si es más corto, hasta su último
     * día); rango → los mismos días inmediatamente antes.
     */
    fun periodoAnterior(p: Periodo, ahora: Long, zona: TimeZone = TimeZone.getDefault()): Periodo {
        val corte = minOf(p.hasta, ahora)
        fun mover(ms: Long, campo: Int, cuanto: Int) = Calendar.getInstance(zona).apply { timeInMillis = ms; add(campo, -cuanto) }.timeInMillis
        return when (p.tipo) {
            TipoPeriodo.HOY -> Periodo(p.tipo, mover(p.desde, Calendar.DAY_OF_YEAR, 1), mover(corte, Calendar.DAY_OF_YEAR, 1))
            TipoPeriodo.SEMANA -> Periodo(p.tipo, mover(p.desde, Calendar.DAY_OF_YEAR, 7), mover(corte, Calendar.DAY_OF_YEAR, 7))
            // Calendar ajusta el día si el mes pasado es más corto (31 de marzo → 28/29 de febrero).
            TipoPeriodo.MES -> Periodo(p.tipo, mover(p.desde, Calendar.MONTH, 1), mover(corte, Calendar.MONTH, 1))
            TipoPeriodo.RANGO -> {
                val dias = diasEntre(p.desde, p.hasta, zona)
                Periodo(p.tipo, mover(p.desde, Calendar.DAY_OF_YEAR, dias), mover(corte, Calendar.DAY_OF_YEAR, dias))
            }
        }
    }

    /** Días de calendario de [desde] a [hasta], ambos incluidos. */
    private fun diasEntre(desde: Long, hasta: Long, zona: TimeZone): Int {
        val c = Calendar.getInstance(zona).apply { timeInMillis = desde }
        var n = 0
        while (c.timeInMillis <= hasta) { n++; c.add(Calendar.DAY_OF_YEAR, 1) }
        return maxOf(1, n)
    }

    /** Cambio en décimas de punto (125 = +12.5 %), mitad alejándose de cero; null si antes fue 0. */
    fun porcentaje(actual: Long, anterior: Long): Long? {
        if (anterior == 0L) return null
        val num = (actual - anterior) * 1000
        val signo = if ((num < 0) != (anterior < 0)) -1 else 1
        return (kotlin.math.abs(num) * 2 + kotlin.math.abs(anterior)) / (kotlin.math.abs(anterior) * 2) * signo
    }

    /** Los que más subieron y más bajaron por importe (hasta [n] de cada uno). Empates por nombre. */
    fun cambiosProductos(
        actual: List<ProductoVendido>,
        anterior: List<ProductoVendido>,
        n: Int = PRODUCTOS,
    ): Pair<List<CambioProducto>, List<CambioProducto>> {
        val a = actual.associateBy { it.productoId }
        val b = anterior.associateBy { it.productoId }
        val cambios = (a.keys + b.keys).map { id ->
            CambioProducto(id, (a[id] ?: b[id])!!.nombre, a[id]?.importeCentavos ?: 0, b[id]?.importeCentavos ?: 0)
        }
        val subieron = cambios.filter { it.diferenciaCentavos > 0 }
            .sortedWith(compareByDescending<CambioProducto> { it.diferenciaCentavos }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.nombre })
            .take(n)
        val bajaron = cambios.filter { it.diferenciaCentavos < 0 }
            .sortedWith(compareBy<CambioProducto> { it.diferenciaCentavos }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.nombre })
            .take(n)
        return subieron to bajaron
    }
}

/** Una barra del reporte por hora o día. */
data class Barra(val etiqueta: String, val numeroVentas: Int, val totalCentavos: Long, val porcentajeDecimas: Long, val mejor: Boolean)

object ReglaHorario {
    /** Lunes primero (costumbre en México); en SQLite 0 = domingo. */
    private val DIAS = listOf(1 to "Lunes", 2 to "Martes", 3 to "Miércoles", 4 to "Jueves", 5 to "Viernes", 6 to "Sábado", 0 to "Domingo")

    fun etiquetaHora(h: Int) = "%02d:00–%02d:00".format(h, (h + 1) % 24)

    /** De la primera a la última hora con ventas (las de en medio sin ventas van en cero). */
    fun porHora(filas: List<VentasPorGrupo>): List<Barra> {
        if (filas.isEmpty()) return emptyList()
        val m = filas.associateBy { it.grupo }
        val horas = (filas.minOf { it.grupo }..filas.maxOf { it.grupo }).map { h -> h to (m[h]) }
        return barras(horas.map { (h, f) -> Triple(etiquetaHora(h), f?.numeroVentas ?: 0, f?.totalCentavos ?: 0L) })
    }

    /** Lunes a domingo (todos, aunque no tengan ventas). */
    fun porDiaSemana(filas: List<VentasPorGrupo>): List<Barra> {
        if (filas.isEmpty()) return emptyList()
        val m = filas.associateBy { it.grupo }
        return barras(DIAS.map { (n, nombre) -> Triple(nombre, m[n]?.numeroVentas ?: 0, m[n]?.totalCentavos ?: 0L) })
    }

    /** La mejor = mayor importe; en empate, la primera. */
    private fun barras(datos: List<Triple<String, Int, Long>>): List<Barra> {
        val total = datos.sumOf { it.third }
        val mejor = datos.indexOfFirst { it.third == datos.maxOf { d -> d.third } }.takeIf { total > 0 } ?: -1
        return datos.mapIndexed { i, (etiqueta, n, importe) ->
            Barra(etiqueta, n, importe, ReglaPorcentaje.decimas(importe, total), i == mejor)
        }
    }
}

/** Una pérdida por motivo o por producto. */
data class FilaMotivo(val motivo: MotivoAjuste, val registros: Int, val valorCentavos: Long, val porcentajeDecimas: Long)
data class FilaPerdidaProducto(
    val productoId: String,
    val nombre: String,
    val tipoVenta: TipoVenta,
    val cantidadMilesimas: Long,
    val valorCentavos: Long,
    /** Alguna de sus mermas se valoró con el precio actual (registrada antes de la 1.6.0). */
    val conPrecioActual: Boolean,
)

object ReglaMermas {
    /** Respuesta 7: estas salidas son pérdida; la corrección de conteo no. */
    val MOTIVOS = listOf(MotivoAjuste.MERMA, MotivoAjuste.PRODUCTO_CADUCADO, MotivoAjuste.CONSUMO_PROPIO, MotivoAjuste.OTRO)

    fun cuenta(s: SalidaAjuste): Boolean = s.cantidadMilesimas < 0 && s.motivo in MOTIVOS

    /** Respuesta 6: precio del día de la merma; si no se guardó (antes de la 1.6.0), el actual. */
    fun valor(s: SalidaAjuste): Long = Dinero.importe(-s.cantidadMilesimas, s.precioGuardadoCentavos ?: s.precioActualCentavos)

    fun porMotivo(salidas: List<SalidaAjuste>): List<FilaMotivo> {
        val validas = salidas.filter(::cuenta)
        val total = validas.sumOf(::valor)
        return MOTIVOS.mapNotNull { m ->
            val de = validas.filter { it.motivo == m }
            if (de.isEmpty()) null else de.sumOf(::valor).let { v -> FilaMotivo(m, de.size, v, ReglaPorcentaje.decimas(v, total)) }
        }.sortedByDescending { it.valorCentavos }
    }

    fun porProducto(salidas: List<SalidaAjuste>): List<FilaPerdidaProducto> =
        salidas.filter(::cuenta).groupBy { it.productoId }.map { (id, l) ->
            FilaPerdidaProducto(
                id, l.first().nombre, l.first().tipoVenta, l.sumOf { -it.cantidadMilesimas }, l.sumOf(::valor),
                l.any { it.precioGuardadoCentavos == null },
            )
        }.sortedWith(compareByDescending<FilaPerdidaProducto> { it.valorCentavos }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.nombre })

    fun total(salidas: List<SalidaAjuste>): Long = salidas.filter(::cuenta).sumOf(::valor)

    fun etiqueta(m: MotivoAjuste): String = when (m) {
        MotivoAjuste.MERMA -> "Merma"
        MotivoAjuste.PRODUCTO_CADUCADO -> "Producto caducado"
        MotivoAjuste.CONSUMO_PROPIO -> "Consumo propio"
        MotivoAjuste.CORRECCION_CONTEO -> "Corrección de conteo"
        MotivoAjuste.OTRO -> "Otro"
    }
}
