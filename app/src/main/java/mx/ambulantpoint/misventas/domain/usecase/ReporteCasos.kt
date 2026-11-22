package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.data.local.dao.ProductoConCategoria
import mx.ambulantpoint.misventas.data.local.dao.ProductoVendido
import mx.ambulantpoint.misventas.data.local.dao.TotalPorDia
import mx.ambulantpoint.misventas.data.local.dao.VentasRecientes
import mx.ambulantpoint.misventas.domain.puertos.PuertoReportes
import mx.ambulantpoint.misventas.domain.regla.NivelExistencia
import mx.ambulantpoint.misventas.domain.regla.OrdenMasVendidos
import mx.ambulantpoint.misventas.domain.regla.Periodo
import mx.ambulantpoint.misventas.domain.regla.ReglaExistenciaBaja
import mx.ambulantpoint.misventas.domain.regla.ReglaMasVendidos
import mx.ambulantpoint.misventas.domain.regla.ReglaPorcentaje
import mx.ambulantpoint.misventas.domain.regla.ReglaTicketPromedio

/** RF 6.1: lo que muestra el reporte de ventas por periodo. */
data class ReporteVentasPeriodo(
    val periodo: Periodo,
    val totalVendidoCentavos: Long,
    val numeroVentas: Int,
    val ticketPromedioCentavos: Long,
    val descuentosCentavos: Long,
    val numeroCanceladas: Int,
    val importeCanceladasCentavos: Long,
    /** Solo días con ventas no canceladas, del más antiguo al más reciente. */
    val porDia: List<TotalPorDia>,
) {
    /** "No hay ventas registradas en este periodo": ni registradas ni canceladas. */
    val sinVentas: Boolean get() = numeroVentas == 0 && numeroCanceladas == 0
}

/**
 * RF 6.1 — Reporte de ventas por periodo. Solo cuenta ventas no canceladas, salvo el número e
 * importe de canceladas, que el DFR pide aparte.
 */
class GenerarReporteVentas(private val reportes: PuertoReportes) {
    suspend operator fun invoke(periodo: Periodo): ReporteVentasPeriodo {
        val r = reportes.resumenPeriodo(periodo.desde, periodo.hasta)
        val dias = if (r.numeroVentas > 0) reportes.totalesPorDia(periodo.desde, periodo.hasta) else emptyList()
        return ReporteVentasPeriodo(
            periodo = periodo,
            totalVendidoCentavos = r.totalVendidoCentavos,
            numeroVentas = r.numeroVentas,
            ticketPromedioCentavos = ReglaTicketPromedio.calcular(r.totalVendidoCentavos, r.numeroVentas),
            descuentosCentavos = r.descuentosCentavos,
            numeroCanceladas = r.numeroCanceladas,
            importeCanceladasCentavos = r.importeCanceladasCentavos,
            porDia = dias,
        )
    }
}

/** Una fila con su porcentaje respecto al total (RF 6.2, 6.4). */
data class FilaPorcentaje(val nombre: String, val numeroVentas: Int?, val importeCentavos: Long, val porcentajeDecimas: Long)

/** RF 6.2 / 6.4: filas de mayor a menor importe; vacío ⇒ "No hay ventas registradas en este periodo". */
data class ReportePorcentajes(val periodo: Periodo, val totalCentavos: Long, val filas: List<FilaPorcentaje>) {
    val sinVentas: Boolean get() = filas.isEmpty()
}

/** RF 6.2 — Ventas por forma de pago (incluye desactivadas con ventas en el periodo). */
class GenerarReporteFormasPago(private val reportes: PuertoReportes) {
    suspend operator fun invoke(periodo: Periodo): ReportePorcentajes {
        val filas = reportes.totalesPorFormaPago(periodo.desde, periodo.hasta)
        val total = filas.sumOf { it.totalCentavos }
        return ReportePorcentajes(
            periodo, total,
            filas.sortedByDescending { it.totalCentavos }.map {
                FilaPorcentaje(it.nombreFormaPago, it.numeroVentas, it.totalCentavos, ReglaPorcentaje.decimas(it.totalCentavos, total))
            },
        )
    }
}

/**
 * RF 6.4 — Ventas por categoría. El porcentaje se calcula sobre la suma de los importes de las
 * líneas vendidas (antes del descuento por venta), para que las categorías sumen 100 %.
 */
class GenerarReporteCategorias(private val reportes: PuertoReportes) {
    suspend operator fun invoke(periodo: Periodo): ReportePorcentajes {
        val filas = reportes.totalesPorCategoria(periodo.desde, periodo.hasta)
        val total = filas.sumOf { it.importeCentavos }
        return ReportePorcentajes(
            periodo, total,
            filas.sortedByDescending { it.importeCentavos }.map {
                FilaPorcentaje(it.categoriaNombre, null, it.importeCentavos, ReglaPorcentaje.decimas(it.importeCentavos, total))
            },
        )
    }
}

/** RF 6.3: productos con ventas en el periodo, ya ordenados. */
data class ReporteMasVendidos(val periodo: Periodo, val orden: OrdenMasVendidos, val filas: List<ProductoVendido>) {
    val sinVentas: Boolean get() = filas.isEmpty()
}

/** RF 6.3 — Productos más vendidos (incluye desactivados con ventas en el periodo). */
class GenerarReporteMasVendidos(private val reportes: PuertoReportes) {
    suspend operator fun invoke(periodo: Periodo, orden: OrdenMasVendidos): ReporteMasVendidos {
        val filas = reportes.productosVendidos(periodo.desde, periodo.hasta)
        return ReporteMasVendidos(
            periodo, orden,
            ReglaMasVendidos.ordenar(filas, orden, { it.tipoVenta }, { it.cantidadMilesimas }, { it.importeCentavos }, { it.nombre }),
        )
    }
}

/** RF 6.5: un producto activo con su nivel de existencia. */
data class FilaInventario(val producto: ProductoConCategoria, val nivel: NivelExistencia)

/** RF 6.5 — Reporte de inventario: al inicio, número de activos, con existencia baja y agotados. */
data class ReporteInventario(val filas: List<FilaInventario>) {
    val activos: Int get() = filas.size
    /** Con existencia baja, sin contar los agotados (se cuentan aparte). */
    val conExistenciaBaja: Int get() = filas.count { it.nivel == NivelExistencia.BAJA }
    val agotados: Int get() = filas.count { it.nivel == NivelExistencia.AGOTADO }

    companion object {
        /**
         * @param productos productos activos (opcionalmente filtrados por categoría).
         * @param recientes productoId → ventas de sus últimos (hasta 7) días con ventas.
         */
        fun armar(productos: List<ProductoConCategoria>, recientes: Map<String, VentasRecientes>): ReporteInventario =
            ReporteInventario(
                productos.filter { it.producto.activo }.map { p ->
                    val r = recientes[p.producto.id]
                    FilaInventario(p, ReglaExistenciaBaja.nivel(p.producto.existenciaMilesimas, p.producto.tipoVenta, r?.dias ?: 0, r?.cantidadMilesimas ?: 0))
                },
            )
    }
}

// ───────────────────────── Fase 5 (docs/11-ESPEC-REPORTES.md) ─────────────────────────

/** Ventas por periodo comparado con el mismo tramo del periodo anterior. */
data class ComparacionPeriodo(
    val anterior: mx.ambulantpoint.misventas.domain.regla.Periodo,
    val total: mx.ambulantpoint.misventas.domain.regla.Diferencia,
    val numeroVentas: mx.ambulantpoint.misventas.domain.regla.Diferencia,
    val ticketPromedio: mx.ambulantpoint.misventas.domain.regla.Diferencia,
    val subieron: List<mx.ambulantpoint.misventas.domain.regla.CambioProducto>,
    val bajaron: List<mx.ambulantpoint.misventas.domain.regla.CambioProducto>,
)

class GenerarComparacion(private val reportes: PuertoReportes, private val ahora: () -> Long) {
    suspend operator fun invoke(periodo: Periodo, zona: java.util.TimeZone = java.util.TimeZone.getDefault()): ComparacionPeriodo {
        val ant = mx.ambulantpoint.misventas.domain.regla.ReglaComparacion.periodoAnterior(periodo, ahora(), zona)
        // El periodo actual también se corta en "ahora" (no hay ventas futuras, pero así los dos tramos son iguales).
        val hasta = minOf(periodo.hasta, ahora())
        val a = reportes.resumenPeriodo(periodo.desde, hasta)
        val b = reportes.resumenPeriodo(ant.desde, ant.hasta)
        val (subieron, bajaron) = mx.ambulantpoint.misventas.domain.regla.ReglaComparacion.cambiosProductos(
            reportes.productosVendidos(periodo.desde, hasta), reportes.productosVendidos(ant.desde, ant.hasta),
        )
        return ComparacionPeriodo(
            anterior = ant,
            total = mx.ambulantpoint.misventas.domain.regla.Diferencia(a.totalVendidoCentavos, b.totalVendidoCentavos),
            numeroVentas = mx.ambulantpoint.misventas.domain.regla.Diferencia(a.numeroVentas.toLong(), b.numeroVentas.toLong()),
            ticketPromedio = mx.ambulantpoint.misventas.domain.regla.Diferencia(
                ReglaTicketPromedio.calcular(a.totalVendidoCentavos, a.numeroVentas),
                ReglaTicketPromedio.calcular(b.totalVendidoCentavos, b.numeroVentas),
            ),
            subieron = subieron,
            bajaron = bajaron,
        )
    }
}

/** Ventas por hora del día y por día de la semana. */
data class ReporteHorario(
    val periodo: Periodo,
    val porHora: List<mx.ambulantpoint.misventas.domain.regla.Barra>,
    val porDia: List<mx.ambulantpoint.misventas.domain.regla.Barra>,
) {
    val sinVentas: Boolean get() = porHora.isEmpty()
}

class GenerarReporteHorario(private val r: mx.ambulantpoint.misventas.domain.puertos.PuertoReportesExtra) {
    suspend operator fun invoke(periodo: Periodo) = ReporteHorario(
        periodo,
        mx.ambulantpoint.misventas.domain.regla.ReglaHorario.porHora(r.ventasPorHora(periodo.desde, periodo.hasta)),
        mx.ambulantpoint.misventas.domain.regla.ReglaHorario.porDiaSemana(r.ventasPorDiaSemana(periodo.desde, periodo.hasta)),
    )
}

/** Fiado y abonos: del periodo y, aparte, lo que se debe hoy. */
data class ReporteFiado(
    val periodo: Periodo,
    val ventasFiadas: Int,
    val fiadoCentavos: Long,
    val abonosPorForma: List<FilaPorcentaje>,
    val abonosCentavos: Long,
    val devolucionesCentavos: Long,
    val porCobrarCentavos: Long,
    /** Clientes con saldo, del que más debe al que menos. */
    val clientes: List<mx.ambulantpoint.misventas.data.local.dao.ClienteConSaldo>,
    val avisoSaldoCentavos: Long,
) {
    val sinDatos: Boolean get() = ventasFiadas == 0 && abonosPorForma.isEmpty() && devolucionesCentavos == 0L && clientes.isEmpty()
}

class GenerarReporteFiado(private val r: mx.ambulantpoint.misventas.domain.puertos.PuertoReportesExtra) {
    suspend operator fun invoke(periodo: Periodo): ReporteFiado {
        val fiado = r.fiadoEnPeriodo(periodo.desde, periodo.hasta)
        val abonos = r.abonosEnPeriodo(periodo.desde, periodo.hasta)
        val totalAbonos = abonos.sumOf { it.totalCentavos }
        val clientes = r.clientesConSaldo().filter { it.saldoCentavos > 0 }
            .sortedWith(compareByDescending<mx.ambulantpoint.misventas.data.local.dao.ClienteConSaldo> { it.saldoCentavos }.thenBy { it.cliente.nombreNormalizado })
        return ReporteFiado(
            periodo = periodo,
            ventasFiadas = fiado.numeroVentas,
            fiadoCentavos = fiado.totalVendidoCentavos,
            abonosPorForma = abonos.map { FilaPorcentaje(it.nombreFormaPago, it.numeroVentas, it.totalCentavos, ReglaPorcentaje.decimas(it.totalCentavos, totalAbonos)) },
            abonosCentavos = totalAbonos,
            devolucionesCentavos = r.devolucionesEnPeriodo(periodo.desde, periodo.hasta),
            porCobrarCentavos = clientes.sumOf { it.saldoCentavos },
            clientes = clientes,
            avisoSaldoCentavos = r.avisoSaldo(),
        )
    }
}

/** Mermas y pérdidas valoradas a precio de venta. */
data class ReporteMermas(
    val periodo: Periodo,
    val totalCentavos: Long,
    val porMotivo: List<mx.ambulantpoint.misventas.domain.regla.FilaMotivo>,
    val porProducto: List<mx.ambulantpoint.misventas.domain.regla.FilaPerdidaProducto>,
) {
    val sinMermas: Boolean get() = porProducto.isEmpty()
    val algunaConPrecioActual: Boolean get() = porProducto.any { it.conPrecioActual }
}

class GenerarReporteMermas(private val r: mx.ambulantpoint.misventas.domain.puertos.PuertoReportesExtra) {
    suspend operator fun invoke(periodo: Periodo): ReporteMermas {
        val salidas = r.salidasPorAjuste(periodo.desde, periodo.hasta)
        return ReporteMermas(
            periodo,
            mx.ambulantpoint.misventas.domain.regla.ReglaMermas.total(salidas),
            mx.ambulantpoint.misventas.domain.regla.ReglaMermas.porMotivo(salidas),
            mx.ambulantpoint.misventas.domain.regla.ReglaMermas.porProducto(salidas),
        )
    }
}
