package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.data.local.dao.TotalPorDia
import mx.ambulantpoint.misventas.domain.puertos.PuertoReportes
import mx.ambulantpoint.misventas.domain.regla.Periodo
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
