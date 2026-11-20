package mx.ambulantpoint.misventas.data.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.domain.export.DocumentoReporte
import mx.ambulantpoint.misventas.domain.export.GeneradorCsv
import mx.ambulantpoint.misventas.domain.export.TablaReporte
import mx.ambulantpoint.misventas.domain.export.Valor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FormatoExportacion(val extension: String, val tipoMime: String) {
    PDF("pdf", "application/pdf"),
    CSV("csv", "text/csv"),
}

/** Archivo generado, listo para guardar o compartir. */
data class ArchivoExportado(val archivo: File, val formato: FormatoExportacion)

/**
 * RF 6.6 — Genera el PDF o CSV de un reporte en la caché de la app (funciona sin conexión). Desde
 * ahí el vendedor lo guarda donde quiera o lo comparte.
 */
class ExportadorReportes(private val contexto: Context, private val reloj: Reloj) {

    suspend fun exportar(d: DocumentoReporte, formato: FormatoExportacion, nombreNegocio: String): ArchivoExportado =
        withContext(Dispatchers.IO) {
            val carpeta = File(contexto.cacheDir, CARPETA).apply { mkdirs() }
            // Solo se conserva el último archivo: son temporales hasta que se guardan o comparten.
            carpeta.listFiles()?.forEach { it.delete() }
            val ahora = reloj.ahora()
            val sello = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date(ahora))
            val archivo = File(carpeta, "MisVentas_${d.nombreArchivo}_$sello.${formato.extension}")
            when (formato) {
                FormatoExportacion.CSV -> archivo.writeText(GeneradorCsv.generar(d), Charsets.UTF_8)
                FormatoExportacion.PDF -> escribirPdf(d, nombreNegocio, ahora, archivo)
            }
            ArchivoExportado(archivo, formato)
        }

    // ───────────────────────── PDF ─────────────────────────

    private fun texto(tamano: Float, negrita: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tamano
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (negrita) Typeface.BOLD else Typeface.NORMAL)
    }

    private val pNegocio = texto(16f, negrita = true, color = VERDE)
    private val pTitulo = texto(14f, negrita = true)
    private val pDato = texto(10f, color = GRIS)
    private val pCuerpo = texto(10f)
    private val pEncabezado = texto(10f, negrita = true)
    private val pSeccion = texto(11f, negrita = true)
    private val pLinea = Paint().apply { color = Color.rgb(0xBD, 0xBD, 0xBD); strokeWidth = 0.5f }
    private val pFondo = Paint().apply { color = Color.rgb(0xE8, 0xF0, 0xE6) }

    /** A4 en puntos; varias páginas si el reporte no cabe en una. */
    private fun escribirPdf(d: DocumentoReporte, negocio: String, ahora: Long, destino: File) {
        val doc = PdfDocument()
        var numero = 1
        var pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create())
        var y = MARGEN

        fun nuevaPaginaSiHaceFalta(reserva: Float = RENGLON * 2) {
            if (y + reserva <= ALTO - MARGEN) return
            doc.finishPage(pagina)
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, ++numero).create())
            y = MARGEN
        }

        fun escribir(t: String, p: Paint, x: Float = MARGEN) {
            nuevaPaginaSiHaceFalta()
            y += p.textSize + 4f
            pagina.canvas.drawText(t, x, y, p)
        }

        // Encabezado: nombre del negocio, nombre del reporte, periodo, filtros y fecha de generación.
        escribir(negocio.ifBlank { "Mis Ventas" }, pNegocio)
        escribir(d.nombreReporte, pTitulo)
        y += 4f
        d.periodo?.let { escribir("Periodo: $it", pDato) }
        d.filtros?.let { escribir(it, pDato) }
        escribir("Generado: ${Formato.fechaHora(ahora)}", pDato)
        y += 8f
        pagina.canvas.drawLine(MARGEN, y, ANCHO - MARGEN, y, pLinea)
        y += 6f

        // Resumen: concepto a la izquierda, valor a la derecha.
        d.resumen.forEach { (concepto, valor) ->
            nuevaPaginaSiHaceFalta()
            y += RENGLON
            pagina.canvas.drawText(concepto, MARGEN, y, pCuerpo)
            val v = valor.legible()
            pagina.canvas.drawText(v, ANCHO - MARGEN - pEncabezado.measureText(v), y, pEncabezado)
        }
        if (d.resumen.isNotEmpty()) y += 10f

        d.tablas.filter { it.filas.isNotEmpty() }.forEach { tabla ->
            tabla.titulo?.let { y += 6f; escribir(it, pSeccion); y += 2f }
            val anchos = anchosColumnas(tabla)
            fun fila(celdas: List<String>, numericas: List<Boolean>, p: Paint, fondo: Boolean) {
                nuevaPaginaSiHaceFalta()
                if (fondo) pagina.canvas.drawRect(MARGEN, y + 3f, ANCHO - MARGEN, y + RENGLON + 5f, pFondo)
                y += RENGLON
                var x = MARGEN
                celdas.forEachIndexed { i, t ->
                    val ancho = anchos[i]
                    val ajustado = recortar(t, p, ancho - 6f)
                    val xt = if (numericas[i]) x + ancho - 3f - p.measureText(ajustado) else x + 3f
                    pagina.canvas.drawText(ajustado, xt, y, p)
                    x += ancho
                }
                pagina.canvas.drawLine(MARGEN, y + 5f, ANCHO - MARGEN, y + 5f, pLinea)
            }
            val numericas = tabla.filas.first().map { it.esNumero }
            fila(tabla.encabezados, numericas, pEncabezado, fondo = true)
            tabla.filas.forEach { f ->
                // El encabezado se repite al empezar página nueva.
                if (y + RENGLON * 2 > ALTO - MARGEN) {
                    nuevaPaginaSiHaceFalta()
                    fila(tabla.encabezados, numericas, pEncabezado, fondo = true)
                }
                fila(f.map(Valor::legible), numericas, pCuerpo, fondo = false)
            }
            y += 10f
        }

        doc.finishPage(pagina)
        destino.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    /** La primera columna (nombre) es más ancha; el resto se reparte igual. */
    private fun anchosColumnas(t: TablaReporte): List<Float> {
        val util = ANCHO - 2 * MARGEN
        val n = t.encabezados.size
        if (n == 1) return listOf(util)
        val primera = util * if (n >= 5) 0.32f else 0.40f
        val resto = (util - primera) / (n - 1)
        return listOf(primera) + List(n - 1) { resto }
    }

    private fun recortar(t: String, p: Paint, ancho: Float): String {
        if (p.measureText(t) <= ancho) return t
        var s = t
        while (s.isNotEmpty() && p.measureText("$s…") > ancho) s = s.dropLast(1)
        return "$s…"
    }

    private companion object {
        const val CARPETA = "reportes"
        const val ANCHO = 595
        const val ALTO = 842
        const val MARGEN = 40f
        const val RENGLON = 16f
        val VERDE = Color.rgb(0x48, 0x6B, 0x45)
        val GRIS = Color.rgb(0x55, 0x55, 0x55)
    }
}
