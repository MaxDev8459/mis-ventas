package mx.ambulantpoint.misventas.domain

import mx.ambulantpoint.misventas.data.local.dao.ProductoVendido
import mx.ambulantpoint.misventas.data.local.dao.TotalPorDia
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.export.DocumentoReporte
import mx.ambulantpoint.misventas.domain.export.ExportarReportes
import mx.ambulantpoint.misventas.domain.export.GeneradorCsv
import mx.ambulantpoint.misventas.domain.export.TablaReporte
import mx.ambulantpoint.misventas.domain.export.Valor
import mx.ambulantpoint.misventas.domain.regla.OrdenMasVendidos
import mx.ambulantpoint.misventas.domain.regla.Periodo
import mx.ambulantpoint.misventas.domain.regla.TipoPeriodo
import mx.ambulantpoint.misventas.domain.usecase.ReporteMasVendidos
import mx.ambulantpoint.misventas.domain.usecase.ReporteVentasPeriodo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RF 6.6 — valores: legibles en el PDF, números simples en el CSV. */
class ValorExportacionTest {
    @Test fun `dinero`() {
        assertEquals("$1,234.50", Valor.Dinero(123450).legible())
        assertEquals("1234.50", Valor.Dinero(123450).crudo())
        assertEquals("0.05", Valor.Dinero(5).crudo())
        assertEquals("-12.00", Valor.Dinero(-1200).crudo())
    }

    @Test fun `cantidades sin separador de miles en el CSV`() {
        assertEquals("1234", Valor.Cantidad(1_234_000, TipoVenta.UNIDAD).crudo())
        assertEquals("0.250", Valor.Cantidad(250, TipoVenta.PESO).crudo())
    }

    @Test fun `porcentaje`() {
        assertEquals("45.3 %", Valor.Porcentaje(453).legible())
        assertEquals("45.3", Valor.Porcentaje(453).crudo())
    }
}

/** RF 6.6 — CSV en columnas con fila de encabezados. */
class GeneradorCsvTest {
    @Test fun `escapa comas, comillas y saltos de linea`() {
        assertEquals("simple", GeneradorCsv.escapar("simple"))
        assertEquals("\"Papas, chile\"", GeneradorCsv.escapar("Papas, chile"))
        assertEquals("\"Refresco \"\"grande\"\"\"", GeneradorCsv.escapar("Refresco \"grande\""))
    }

    @Test fun `resumen y tabla con encabezados, con marca UTF-8`() {
        val d = DocumentoReporte(
            "Reporte", "reporte", null, null,
            resumen = listOf("Total vendido" to Valor.Dinero(150000)),
            tablas = listOf(
                TablaReporte(null, listOf("Forma de pago", "Importe"), listOf(listOf(Valor.Texto("Efectivo"), Valor.Dinero(150000)))),
            ),
            vacio = false,
        )
        val csv = GeneradorCsv.generar(d)
        assertTrue(csv.startsWith(GeneradorCsv.BOM))
        assertEquals(
            listOf("Concepto,Valor", "Total vendido,1500.00", "", "Forma de pago,Importe", "Efectivo,1500.00", ""),
            csv.removePrefix(GeneradorCsv.BOM).split("\r\n"),
        )
    }

    @Test fun `sin resumen la tabla empieza en la primera linea`() {
        val d = DocumentoReporte(
            "R", "r", null, null, emptyList(),
            listOf(TablaReporte(null, listOf("A"), listOf(listOf(Valor.Entero(1))))), false,
        )
        assertEquals(GeneradorCsv.BOM + "A\r\n1\r\n", GeneradorCsv.generar(d))
    }
}

/** RF 6.6 — "No hay información para exportar" y unidad en su propia columna. */
class ExportarReportesTest {
    private val periodo = Periodo(TipoPeriodo.HOY, 0, 1)

    @Test fun `reporte de ventas sin ventas queda vacio`() {
        val r = ReporteVentasPeriodo(periodo, 0, 0, 0, 0, 0, 0, emptyList())
        assertTrue(ExportarReportes.ventas(r, "Ventas").vacio)
    }

    @Test fun `reporte de ventas con desglose por dia`() {
        val r = ReporteVentasPeriodo(periodo, 9000, 2, 4500, 0, 0, 0, listOf(TotalPorDia("2026-11-16", 2, 9000)))
        val d = ExportarReportes.ventas(r, "Ventas")
        assertFalse(d.vacio)
        assertEquals(listOf(Valor.Texto("16/11/2026"), Valor.Entero(2), Valor.Dinero(9000)), d.tablas.single().filas.single())
    }

    @Test fun `mas vendidos lleva la unidad en su columna y el orden en los filtros`() {
        val r = ReporteMasVendidos(
            periodo, OrdenMasVendidos.IMPORTE,
            listOf(ProductoVendido("a", "Cacahuate", "Botanas", TipoVenta.PESO, 2_500, 20_000)),
        )
        val d = ExportarReportes.masVendidos(r, "Más vendidos")
        assertEquals("Ordenado por: importe vendido", d.filtros)
        assertEquals(listOf("Cacahuate", "Botanas", "2.500", "kg", "200.00"), d.tablas.single().filas.single().map { it.crudo() })
    }
}
