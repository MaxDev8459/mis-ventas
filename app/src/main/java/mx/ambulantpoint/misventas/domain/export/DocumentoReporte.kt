package mx.ambulantpoint.misventas.domain.export

import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.regla.NivelExistencia
import mx.ambulantpoint.misventas.domain.regla.Periodo
import mx.ambulantpoint.misventas.domain.regla.TipoPeriodo
import mx.ambulantpoint.misventas.domain.usecase.ReporteInventario
import mx.ambulantpoint.misventas.domain.usecase.ReporteMasVendidos
import mx.ambulantpoint.misventas.domain.usecase.ReportePorcentajes
import mx.ambulantpoint.misventas.domain.usecase.ReporteVentasPeriodo
import mx.ambulantpoint.misventas.domain.usecase.ComparacionPeriodo
import mx.ambulantpoint.misventas.domain.usecase.ReporteHorario
import mx.ambulantpoint.misventas.domain.usecase.ReporteFiado
import mx.ambulantpoint.misventas.domain.usecase.ReporteMermas
import mx.ambulantpoint.misventas.domain.regla.Barra
import mx.ambulantpoint.misventas.domain.regla.Diferencia
import mx.ambulantpoint.misventas.domain.regla.ReglaMermas
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Un valor de celda con su tipo. El PDF lo muestra con formato ("$1,234.50", "45.3 %") y el CSV
 * lo escribe como número simple ("1234.50", "45.3") para que la hoja de cálculo pueda operar con él.
 */
sealed interface Valor {
    data class Texto(val texto: String) : Valor
    data class Entero(val n: Int) : Valor
    data class Dinero(val centavos: Long) : Valor
    /** Solo el número; la unidad va en su propia columna. */
    data class Cantidad(val milesimas: Long, val tipoVenta: TipoVenta) : Valor
    data class Porcentaje(val decimas: Long) : Valor

    /** Texto para el PDF. */
    fun legible(): String = when (this) {
        is Texto -> texto
        is Entero -> n.toString()
        is Dinero -> Formato.moneda(centavos)
        is Cantidad -> Formato.numeroCantidad(milesimas, tipoVenta)
        is Porcentaje -> Formato.porcentaje(decimas)
    }

    /** Texto para el CSV: sin símbolo de pesos ni separador de miles. */
    fun crudo(): String = when (this) {
        is Texto -> texto
        is Entero -> n.toString()
        is Dinero -> {
            val signo = if (centavos < 0) "-" else ""
            val v = kotlin.math.abs(centavos)
            "$signo${v / 100}.${(v % 100).toString().padStart(2, '0')}"
        }
        is Cantidad -> Formato.numeroCantidad(milesimas, tipoVenta).replace(",", "")
        is Porcentaje -> "${decimas / 10}.${kotlin.math.abs(decimas % 10)}"
    }

    /** Los números se alinean a la derecha en el PDF. */
    val esNumero: Boolean get() = this !is Texto
}

data class TablaReporte(val titulo: String?, val encabezados: List<String>, val filas: List<List<Valor>>)

/**
 * RF 6.6 — Lo que se exporta de un reporte, con el periodo y los filtros que el vendedor tiene
 * seleccionados. El nombre del negocio y la fecha de generación los agrega el generador.
 */
data class DocumentoReporte(
    val nombreReporte: String,
    /** Nombre corto para el archivo (sin espacios ni acentos). */
    val nombreArchivo: String,
    val periodo: String?,
    val filtros: String?,
    val resumen: List<Pair<String, Valor>>,
    val tablas: List<TablaReporte>,
    /** "No hay información para exportar". */
    val vacio: Boolean,
)

/** Convierte cada reporte del Módulo 6 en un [DocumentoReporte]. */
object ExportarReportes {
    private val DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** "Esta semana (16/11/2026 – 18/11/2026)", "Hoy (18/11/2026)". */
    fun textoPeriodo(p: Periodo): String {
        val desde = Formato.fecha(p.desde)
        val hasta = Formato.fecha(p.hasta)
        val fechas = if (desde == hasta) desde else "$desde – $hasta"
        return if (p.tipo == TipoPeriodo.RANGO) fechas else "${p.tipo.etiqueta} ($fechas)"
    }

    /** Fase 5: "ayer", "la semana pasada"… para el periodo con el que se compara. */
    fun nombreAnterior(tipo: TipoPeriodo): String = when (tipo) {
        TipoPeriodo.HOY -> "ayer"
        TipoPeriodo.SEMANA -> "la semana pasada"
        TipoPeriodo.MES -> "el mes pasado"
        TipoPeriodo.RANGO -> "los mismos días antes"
    }

    /** "16/11/2026 00:00 – 18/11/2026 15:30". */
    fun tramo(p: Periodo): String = "${Formato.fechaHora(p.desde)} – ${Formato.fechaHora(p.hasta)}"

    /** RF 6.1 + Fase 5 (comparación con el mismo tramo del periodo anterior). */
    fun ventas(r: ReporteVentasPeriodo, titulo: String, comparacion: ComparacionPeriodo? = null): DocumentoReporte = DocumentoReporte(
        nombreReporte = titulo,
        nombreArchivo = "ventas_por_periodo",
        periodo = textoPeriodo(r.periodo),
        filtros = null,
        resumen = listOf(
            "Total vendido" to Valor.Dinero(r.totalVendidoCentavos),
            "Número de ventas" to Valor.Entero(r.numeroVentas),
            "Ticket promedio" to Valor.Dinero(r.ticketPromedioCentavos),
            "Descuentos aplicados" to Valor.Dinero(r.descuentosCentavos),
            "Ventas canceladas" to Valor.Entero(r.numeroCanceladas),
            "Importe de ventas canceladas" to Valor.Dinero(r.importeCanceladasCentavos),
        ),
        tablas = listOf(
            TablaReporte(
                "Desglose por día", listOf("Día", "Ventas", "Total"),
                r.porDia.map { listOf(Valor.Texto(dia(it.dia)), Valor.Entero(it.numeroVentas), Valor.Dinero(it.totalCentavos)) },
            ),
        ) + comparacion?.let { c ->
            fun pct(d: Diferencia): Valor = d.porcentajeDecimas?.let { Valor.Porcentaje(it) } ?: Valor.Texto("sin ventas antes")
            listOf(
                TablaReporte(
                    "Comparado con ${nombreAnterior(r.periodo.tipo)} (${tramo(c.anterior)})",
                    listOf("Dato", "Actual", "Anterior", "Diferencia", "%"),
                    listOf(
                        listOf(Valor.Texto("Total vendido"), Valor.Dinero(c.total.actual), Valor.Dinero(c.total.anterior), Valor.Dinero(c.total.diferencia), pct(c.total)),
                        listOf(Valor.Texto("Número de ventas"), Valor.Entero(c.numeroVentas.actual.toInt()), Valor.Entero(c.numeroVentas.anterior.toInt()), Valor.Entero(c.numeroVentas.diferencia.toInt()), pct(c.numeroVentas)),
                        listOf(Valor.Texto("Ticket promedio"), Valor.Dinero(c.ticketPromedio.actual), Valor.Dinero(c.ticketPromedio.anterior), Valor.Dinero(c.ticketPromedio.diferencia), pct(c.ticketPromedio)),
                    ),
                ),
                TablaReporte(
                    "Productos que más subieron", listOf("Producto", "Actual", "Anterior", "Diferencia"),
                    c.subieron.map { listOf(Valor.Texto(it.nombre), Valor.Dinero(it.actualCentavos), Valor.Dinero(it.anteriorCentavos), Valor.Dinero(it.diferenciaCentavos)) },
                ),
                TablaReporte(
                    "Productos que más bajaron", listOf("Producto", "Actual", "Anterior", "Diferencia"),
                    c.bajaron.map { listOf(Valor.Texto(it.nombre), Valor.Dinero(it.actualCentavos), Valor.Dinero(it.anteriorCentavos), Valor.Dinero(it.diferenciaCentavos)) },
                ),
            )
        }.orEmpty(),
        vacio = r.sinVentas,
    )

    /** Fase 5: ventas por hora del día y por día de la semana. */
    fun horario(r: ReporteHorario, titulo: String): DocumentoReporte {
        fun tabla(t: String, col: String, b: List<Barra>) = TablaReporte(
            t, listOf(col, "Ventas", "Importe", "%"),
            b.map { listOf(Valor.Texto(it.etiqueta + if (it.mejor) " (mejor)" else ""), Valor.Entero(it.numeroVentas), Valor.Dinero(it.totalCentavos), Valor.Porcentaje(it.porcentajeDecimas)) },
        )
        return DocumentoReporte(
            nombreReporte = titulo, nombreArchivo = "ventas_por_hora_y_dia", periodo = textoPeriodo(r.periodo), filtros = null,
            resumen = listOfNotNull(
                r.porHora.firstOrNull { it.mejor }?.let { "Mejor hora" to Valor.Texto(it.etiqueta) },
                r.porDia.firstOrNull { it.mejor }?.let { "Mejor día" to Valor.Texto(it.etiqueta) },
            ),
            tablas = listOf(tabla("Por hora del día", "Hora", r.porHora), tabla("Por día de la semana", "Día", r.porDia)),
            vacio = r.sinVentas,
        )
    }

    /** Fase 5: fiado y abonos del periodo y lo que se debe hoy. */
    fun fiado(r: ReporteFiado, titulo: String): DocumentoReporte = DocumentoReporte(
        nombreReporte = titulo, nombreArchivo = "fiado_y_abonos", periodo = textoPeriodo(r.periodo), filtros = null,
        resumen = listOf(
            "Ventas fiadas" to Valor.Entero(r.ventasFiadas),
            "Total fiado" to Valor.Dinero(r.fiadoCentavos),
            "Abonos recibidos" to Valor.Dinero(r.abonosCentavos),
            "Devoluciones en efectivo" to Valor.Dinero(r.devolucionesCentavos),
            "Total por cobrar (hoy)" to Valor.Dinero(r.porCobrarCentavos),
        ),
        tablas = listOf(
            TablaReporte(
                "Abonos por forma de pago", listOf("Forma de pago", "Abonos", "Importe", "%"),
                r.abonosPorForma.map { listOf(Valor.Texto(it.nombre), Valor.Entero(it.numeroVentas ?: 0), Valor.Dinero(it.importeCentavos), Valor.Porcentaje(it.porcentajeDecimas)) },
            ),
            TablaReporte(
                "Clientes con saldo (hoy)", listOf("Cliente", "Teléfono", "Saldo"),
                r.clientes.map { listOf(Valor.Texto(it.cliente.nombre), Valor.Texto(it.cliente.telefono.orEmpty()), Valor.Dinero(it.saldoCentavos)) },
            ),
        ),
        vacio = r.sinDatos,
    )

    /** Fase 5: mermas y pérdidas a precio de venta. */
    fun mermas(r: ReporteMermas, titulo: String): DocumentoReporte = DocumentoReporte(
        nombreReporte = titulo, nombreArchivo = "mermas_y_perdidas", periodo = textoPeriodo(r.periodo),
        filtros = if (r.algunaConPrecioActual) NOTA_PRECIO_ACTUAL else null,
        resumen = listOf("Total perdido" to Valor.Dinero(r.totalCentavos)),
        tablas = listOf(
            TablaReporte(
                "Por motivo", listOf("Motivo", "Registros", "Valor", "%"),
                r.porMotivo.map { listOf(Valor.Texto(ReglaMermas.etiqueta(it.motivo)), Valor.Entero(it.registros), Valor.Dinero(it.valorCentavos), Valor.Porcentaje(it.porcentajeDecimas)) },
            ),
            TablaReporte(
                "Por producto", listOf("Producto", "Cantidad", "Unidad", "Valor"),
                r.porProducto.map {
                    listOf(
                        Valor.Texto(it.nombre + if (it.conPrecioActual) " *" else ""), Valor.Cantidad(it.cantidadMilesimas, it.tipoVenta),
                        Valor.Texto(Formato.unidad(it.tipoVenta)), Valor.Dinero(it.valorCentavos),
                    )
                },
            ),
        ),
        vacio = r.sinMermas,
    )

    const val NOTA_PRECIO_ACTUAL = "* Valorado con el precio actual (merma registrada antes de la versión 1.6.0)"

    /** RF 6.2 (por forma de pago) y RF 6.4 (por categoría). */
    fun porcentajes(r: ReportePorcentajes, titulo: String, porCategoria: Boolean): DocumentoReporte = DocumentoReporte(
        nombreReporte = titulo,
        nombreArchivo = if (porCategoria) "ventas_por_categoria" else "ventas_por_forma_de_pago",
        periodo = textoPeriodo(r.periodo),
        filtros = null,
        resumen = listOf("Total vendido" to Valor.Dinero(r.totalCentavos)),
        tablas = listOf(
            if (porCategoria) {
                TablaReporte(
                    null, listOf("Categoría", "Importe", "Porcentaje"),
                    r.filas.map { listOf(Valor.Texto(it.nombre), Valor.Dinero(it.importeCentavos), Valor.Porcentaje(it.porcentajeDecimas)) },
                )
            } else {
                TablaReporte(
                    null, listOf("Forma de pago", "Ventas", "Importe", "Porcentaje"),
                    r.filas.map {
                        listOf(Valor.Texto(it.nombre), Valor.Entero(it.numeroVentas ?: 0), Valor.Dinero(it.importeCentavos), Valor.Porcentaje(it.porcentajeDecimas))
                    },
                )
            },
        ),
        vacio = r.sinVentas,
    )

    /** RF 6.3. */
    fun masVendidos(r: ReporteMasVendidos, titulo: String): DocumentoReporte = DocumentoReporte(
        nombreReporte = titulo,
        nombreArchivo = "productos_mas_vendidos",
        periodo = textoPeriodo(r.periodo),
        filtros = "Ordenado por: ${r.orden.etiqueta.lowercase()}",
        resumen = emptyList(),
        tablas = listOf(
            TablaReporte(
                null, listOf("Producto", "Categoría", "Cantidad", "Unidad", "Importe"),
                r.filas.map {
                    listOf(
                        Valor.Texto(it.nombre), Valor.Texto(it.categoriaNombre),
                        Valor.Cantidad(it.cantidadMilesimas, it.tipoVenta), Valor.Texto(Formato.unidad(it.tipoVenta)),
                        Valor.Dinero(it.importeCentavos),
                    )
                },
            ),
        ),
        vacio = r.sinVentas,
    )

    /** RF 6.5. No depende de un periodo; el filtro es la categoría (o todas). */
    fun inventario(r: ReporteInventario, titulo: String, categoria: String?): DocumentoReporte = DocumentoReporte(
        nombreReporte = titulo,
        nombreArchivo = "inventario",
        periodo = null,
        filtros = "Categoría: ${categoria ?: "Todas"}",
        resumen = listOf(
            "Productos activos" to Valor.Entero(r.activos),
            "Con existencia baja" to Valor.Entero(r.conExistenciaBaja),
            "Agotados" to Valor.Entero(r.agotados),
        ),
        tablas = listOf(
            TablaReporte(
                null, listOf("Producto", "Categoría", "Existencia", "Unidad", "Estado"),
                r.filas.map {
                    val p = it.producto.producto
                    listOf(
                        Valor.Texto(p.nombre), Valor.Texto(it.producto.categoriaNombre),
                        Valor.Cantidad(p.existenciaMilesimas, p.tipoVenta), Valor.Texto(Formato.unidad(p.tipoVenta)),
                        Valor.Texto(estado(it.nivel)),
                    )
                },
            ),
        ),
        vacio = r.filas.isEmpty(),
    )

    private fun estado(n: NivelExistencia) = when (n) {
        NivelExistencia.AGOTADO -> "Agotado"
        NivelExistencia.BAJA -> "Existencia baja"
        NivelExistencia.NORMAL -> ""
    }

    /** "2026-11-16" → "16/11/2026". */
    private fun dia(iso: String) = runCatching { LocalDate.parse(iso).format(DIA) }.getOrDefault(iso)

}

/** RF 6.6 — CSV: secciones con su fila de encabezados, separadas por una línea en blanco. */
object GeneradorCsv {
    /** Marca UTF-8 para que Excel muestre bien los acentos. */
    const val BOM = "﻿"

    fun generar(d: DocumentoReporte): String = buildString {
        append(BOM)
        if (d.resumen.isNotEmpty()) {
            linea(listOf("Concepto", "Valor"))
            d.resumen.forEach { (k, v) -> linea(listOf(k, v.crudo())) }
        }
        d.tablas.filter { it.filas.isNotEmpty() }.forEach { t ->
            if (isNotEmpty() && length > BOM.length) append("\r\n")
            linea(t.encabezados)
            t.filas.forEach { f -> linea(f.map { it.crudo() }) }
        }
    }

    private fun StringBuilder.linea(celdas: List<String>) {
        append(celdas.joinToString(",") { escapar(it) })
        append("\r\n")
    }

    /** RFC 4180: comillas si hay coma, comillas o salto de línea; las comillas se duplican. */
    fun escapar(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
