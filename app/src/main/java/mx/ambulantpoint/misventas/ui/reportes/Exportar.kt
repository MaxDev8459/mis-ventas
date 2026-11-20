package mx.ambulantpoint.misventas.ui.reportes

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.export.ArchivoExportado
import mx.ambulantpoint.misventas.data.export.FormatoExportacion
import mx.ambulantpoint.misventas.domain.export.DocumentoReporte
import mx.ambulantpoint.misventas.ui.components.contenedor

/**
 * RF 6.6 — Botón de la barra superior de cada reporte. Exporta el reporte con el periodo y filtros
 * seleccionados: elegir PDF o CSV → "El reporte se exportó exitosamente" → Guardar o Compartir.
 * [documento] es null mientras el reporte se calcula.
 */
@Composable
fun AccionExportar(documento: DocumentoReporte?) {
    val c = contenedor()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var elegir by remember { mutableStateOf(false) }
    var generando by remember { mutableStateOf(false) }
    var exportado by remember { mutableStateOf<ArchivoExportado?>(null) }

    // "Guardar": el vendedor elige carpeta y nombre (Descargas, Drive, etc.) con el selector del sistema.
    fun copiarA(uri: android.net.Uri?) {
        val origen = exportado?.archivo ?: return
        if (uri == null) return
        alcance.launch {
            withContext(Dispatchers.IO) {
                contexto.contentResolver.openOutputStream(uri)?.use { salida -> origen.inputStream().use { it.copyTo(salida) } }
            }
            exportado = null
        }
    }
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExportacion.PDF.tipoMime)) { copiarA(it) }
    val guardarCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExportacion.CSV.tipoMime)) { copiarA(it) }

    IconButton(onClick = {
        when {
            documento == null -> Unit
            documento.vacio -> c.notificador.mostrar(Mensajes.SIN_INFORMACION_EXPORTAR)
            else -> elegir = true
        }
    }) {
        Icon(Icons.Filled.Share, contentDescription = "Exportar reporte")
    }

    if (elegir && documento != null) {
        AlertDialog(
            onDismissRequest = { if (!generando) elegir = false },
            title = { Text("Exportar reporte") },
            text = {
                if (generando) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormatoExportacion.entries.forEach { f ->
                            OutlinedButton(
                                onClick = {
                                    generando = true
                                    alcance.launch {
                                        val negocio = c.db.perfilDao().obtener()?.nombreNegocio.orEmpty()
                                        val r = runCatching { c.exportador.exportar(documento, f, negocio) }
                                        generando = false
                                        elegir = false
                                        r.onSuccess { exportado = it }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(f.name) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { if (!generando) TextButton({ elegir = false }) { Text("Cancelar") } },
        )
    }

    exportado?.let { a ->
        AlertDialog(
            onDismissRequest = { exportado = null },
            text = { Text(Mensajes.REPORTE_EXPORTADO) },
            confirmButton = {
                TextButton({
                    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", a.archivo)
                    val envio = Intent(Intent.ACTION_SEND).apply {
                        type = a.formato.tipoMime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, a.archivo.nameWithoutExtension)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    contexto.startActivity(Intent.createChooser(envio, "Compartir reporte"))
                    exportado = null
                }) { Text("Compartir") }
            },
            dismissButton = {
                TextButton({
                    when (a.formato) {
                        FormatoExportacion.PDF -> guardarPdf.launch(a.archivo.name)
                        FormatoExportacion.CSV -> guardarCsv.launch(a.archivo.name)
                    }
                }) { Text("Guardar") }
            },
        )
    }
}
