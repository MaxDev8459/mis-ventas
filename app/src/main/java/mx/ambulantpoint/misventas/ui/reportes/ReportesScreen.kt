package mx.ambulantpoint.misventas.ui.reportes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.domain.export.ExportarReportes
import mx.ambulantpoint.misventas.domain.regla.Periodo
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteVentas
import mx.ambulantpoint.misventas.domain.usecase.ReporteVentasPeriodo
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.vm
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reportes del Módulo 6 disponibles en el menú (se agregan conforme se terminan). */
enum class Reporte(val titulo: String, val descripcion: String) {
    VENTAS_PERIODO("Ventas por periodo", "Total vendido, número de ventas, ticket promedio y desglose por día"),
    POR_FORMA_PAGO("Ventas por forma de pago", "Número de ventas, importe y porcentaje de cada forma de pago"),
    MAS_VENDIDOS("Productos más vendidos", "Cantidad e importe vendido de cada producto"),
    POR_CATEGORIA("Ventas por categoría", "Importe y porcentaje de cada categoría"),
    INVENTARIO("Inventario", "Existencia actual, productos con existencia baja y agotados"),
}

/** Módulo 6 — Reportes. */
@Composable
fun PantallaReportes(alRegresar: () -> Unit, alAbrir: (Reporte) -> Unit) {
    Scaffold(topBar = { BarraSuperior("Reportes", alRegresar) }) { relleno ->
        LazyColumn(Modifier.padding(relleno).fillMaxSize()) {
            items(Reporte.entries) { r ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .clickable { alAbrir(r) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(r.titulo, style = MaterialTheme.typography.titleMedium)
                    Text(r.descripcion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReporteVentasViewModel(c: AppContainer) : ViewModel() {
    private val generar = GenerarReporteVentas(c.reporteRepo)
    val periodo = MutableStateFlow(periodoInicial())

    val reporte: StateFlow<ReporteVentasPeriodo?> = periodo
        .mapLatest { generar(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cambiar(p: Periodo) { periodo.value = p }
}

/** RF 6.1 — Reporte de ventas por periodo. */
@Composable
fun PantallaReporteVentas(alRegresar: () -> Unit) {
    val modelo = vm { ReporteVentasViewModel(it) }
    val periodo by modelo.periodo.collectAsStateWithLifecycle()
    val reporte by modelo.reporte.collectAsStateWithLifecycle()

    val titulo = Reporte.VENTAS_PERIODO.titulo
    Scaffold(topBar = {
        BarraSuperior(titulo, alRegresar) {
            AccionExportar(reporte?.takeIf { it.periodo == periodo }?.let { ExportarReportes.ventas(it, titulo) })
        }
    }) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            SelectorPeriodo(periodo, modelo::cambiar)
            val r = reporte
            when {
                // Mientras se calcula, o si el resultado es de un periodo anterior, no se muestra nada.
                r == null || r.periodo != periodo -> Unit
                r.sinVentas -> MensajeVacio(Mensajes.SIN_VENTAS_PERIODO)
                else -> ContenidoReporteVentas(r)
            }
        }
    }
}

@Composable
private fun ContenidoReporteVentas(r: ReporteVentasPeriodo) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Total vendido", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        Formato.moneda(r.totalVendidoCentavos),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(vertical = 8.dp)) {
                Cifra("Número de ventas", r.numeroVentas.toString())
                Cifra("Ticket promedio", Formato.moneda(r.ticketPromedioCentavos))
                Cifra("Descuentos aplicados", Formato.moneda(r.descuentosCentavos))
                Cifra("Ventas canceladas", "${r.numeroCanceladas} · ${Formato.moneda(r.importeCanceladasCentavos)}")
            }
        }
        if (r.porDia.isNotEmpty()) {
            item {
                Text(
                    "Desglose por día",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                FilaTabla("Día", "Ventas", "Total", encabezado = true)
                HorizontalDivider()
            }
            items(r.porDia, key = { it.dia }) { d ->
                FilaTabla(etiquetaDia(d.dia), d.numeroVentas.toString(), Formato.moneda(d.totalCentavos))
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Cifra(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FilaTabla(dia: String, ventas: String, total: String, encabezado: Boolean = false) {
    val estilo = if (encabezado) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge
    val color = if (encabezado) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(dia, style = estilo, color = color, modifier = Modifier.weight(1.4f))
        Text(ventas, style = estilo, color = color, modifier = Modifier.weight(0.6f))
        Text(total, style = estilo, color = color, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEE dd/MM/yyyy", Locale.forLanguageTag("es-MX"))

/** "2026-11-16" → "lun 16/11/2026". */
private fun etiquetaDia(dia: String): String = runCatching { LocalDate.parse(dia).format(FORMATO_DIA) }.getOrDefault(dia)
