package mx.ambulantpoint.misventas.ui.reportes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import mx.ambulantpoint.misventas.domain.regla.Barra
import mx.ambulantpoint.misventas.domain.regla.Periodo
import mx.ambulantpoint.misventas.domain.regla.ReglaMermas
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteFiado
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteHorario
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteMermas
import mx.ambulantpoint.misventas.domain.usecase.ReporteFiado
import mx.ambulantpoint.misventas.domain.usecase.ReporteHorario
import mx.ambulantpoint.misventas.domain.usecase.ReporteMermas
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.vm

/** ViewModel común: un reporte que depende solo del periodo. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportePeriodoViewModel<T>(private val generar: suspend (Periodo) -> T) : ViewModel() {
    val periodo = MutableStateFlow(periodoInicial())
    val reporte: StateFlow<Pair<Periodo, T>?> = periodo
        .mapLatest { it to generar(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun cambiar(p: Periodo) { periodo.value = p }
}

/** Esqueleto de los reportes nuevos: barra con exportar, selector de periodo y contenido del periodo vigente. */
@Composable
private fun <T> PantallaPorPeriodo(
    reporte: Reporte,
    modelo: ReportePeriodoViewModel<T>,
    alRegresar: () -> Unit,
    exportar: (T) -> mx.ambulantpoint.misventas.domain.export.DocumentoReporte,
    vacio: (T) -> Boolean,
    mensajeVacio: String,
    contenido: @Composable (T) -> Unit,
) {
    val periodo by modelo.periodo.collectAsStateWithLifecycle()
    val par by modelo.reporte.collectAsStateWithLifecycle()
    val actual = par?.takeIf { it.first == periodo }?.second
    Scaffold(topBar = { BarraSuperior(reporte.titulo, alRegresar) { AccionExportar(actual?.let(exportar)) } }) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            SelectorPeriodo(periodo, modelo::cambiar)
            when {
                actual == null -> Unit
                vacio(actual) -> MensajeVacio(mensajeVacio)
                else -> contenido(actual)
            }
        }
    }
}

// ───────────────────────── Ventas por hora y día ─────────────────────────

@Composable
fun PantallaReporteHorario(alRegresar: () -> Unit) {
    val modelo = vm { c -> ReportePeriodoViewModel(GenerarReporteHorario(c.reporteRepo)::invoke) }
    PantallaPorPeriodo(
        Reporte.HORA_Y_DIA, modelo, alRegresar,
        exportar = { ExportarReportes.horario(it, Reporte.HORA_Y_DIA.titulo) },
        vacio = ReporteHorario::sinVentas, mensajeVacio = Mensajes.SIN_VENTAS_PERIODO,
    ) { r ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                val hora = r.porHora.firstOrNull { it.mejor }
                val dia = r.porDia.firstOrNull { it.mejor }
                TarjetaTotal("Tu mejor hora", hora?.etiqueta.orEmpty())
                if (dia != null) {
                    Text(
                        "Tu mejor día: ${dia.etiqueta}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text("Por hora del día", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            }
            items(r.porHora) { BarraVenta(it) }
            item { Text("Por día de la semana", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
            items(r.porDia) { BarraVenta(it) }
        }
    }
}

/** Barra horizontal con el importe a un lado; la mejor, resaltada. */
@Composable
private fun BarraVenta(b: Barra) {
    val maximo = 1000f
    val color = if (b.mejor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                b.etiqueta, style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (b.mejor) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f),
            )
            Text(
                "${Formato.moneda(b.totalCentavos)} · ${b.numeroVentas} ${if (b.numeroVentas == 1) "venta" else "ventas"}",
                style = MaterialTheme.typography.bodyMedium, fontWeight = if (b.mejor) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                Modifier.fillMaxWidth((b.porcentajeDecimas.coerceIn(0, 1000) / maximo).coerceAtLeast(if (b.totalCentavos > 0) 0.02f else 0f))
                    .height(12.dp).clip(RoundedCornerShape(6.dp)).background(color),
            )
        }
    }
}

// ───────────────────────── Fiado y abonos ─────────────────────────

@Composable
fun PantallaReporteFiado(alRegresar: () -> Unit) {
    val modelo = vm { c -> ReportePeriodoViewModel(GenerarReporteFiado(c.reporteRepo)::invoke) }
    PantallaPorPeriodo(
        Reporte.FIADO, modelo, alRegresar,
        exportar = { ExportarReportes.fiado(it, Reporte.FIADO.titulo) },
        vacio = ReporteFiado::sinDatos, mensajeVacio = "No hay fiado ni abonos en este periodo",
    ) { r ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                TarjetaTotal("Total por cobrar (hoy)", Formato.moneda(r.porCobrarCentavos))
                Text("En el periodo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Fila("Ventas fiadas (${r.ventasFiadas})", Formato.moneda(r.fiadoCentavos))
                Fila("Abonos recibidos", Formato.moneda(r.abonosCentavos))
                r.abonosPorForma.forEach { Fila("   ${it.nombre} (${it.numeroVentas})", "${Formato.moneda(it.importeCentavos)} · ${Formato.porcentaje(it.porcentajeDecimas)}") }
                if (r.devolucionesCentavos > 0) Fila("Devoluciones en efectivo", "−" + Formato.moneda(r.devolucionesCentavos))
                Text("Clientes con saldo (hoy)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                if (r.clientes.isEmpty()) Text("Nadie te debe", style = MaterialTheme.typography.bodyLarge)
            }
            items(r.clientes, key = { it.cliente.id }) { c ->
                val alto = c.saldoCentavos > r.avisoSaldoCentavos
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.cliente.nombre, style = MaterialTheme.typography.bodyLarge)
                        c.cliente.telefono?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Text(
                        Formato.moneda(c.saldoCentavos), style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (alto) FontWeight.Bold else FontWeight.Normal,
                        color = if (alto) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

// ───────────────────────── Mermas y pérdidas ─────────────────────────

@Composable
fun PantallaReporteMermas(alRegresar: () -> Unit) {
    val modelo = vm { c -> ReportePeriodoViewModel(GenerarReporteMermas(c.reporteRepo)::invoke) }
    PantallaPorPeriodo(
        Reporte.MERMAS, modelo, alRegresar,
        exportar = { ExportarReportes.mermas(it, Reporte.MERMAS.titulo) },
        vacio = ReporteMermas::sinMermas, mensajeVacio = "No hay mermas registradas en este periodo",
    ) { r ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                TarjetaTotal("Total perdido (a precio de venta)", Formato.moneda(r.totalCentavos))
                Text("Por motivo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                r.porMotivo.forEach {
                    Fila("${ReglaMermas.etiqueta(it.motivo)} (${it.registros})", "${Formato.moneda(it.valorCentavos)} · ${Formato.porcentaje(it.porcentajeDecimas)}")
                }
                Text("Por producto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            }
            items(r.porProducto, key = { it.productoId }) { p ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.nombre + if (p.conPrecioActual) " *" else "", style = MaterialTheme.typography.bodyLarge)
                        Text(Formato.cantidad(p.cantidadMilesimas, p.tipoVenta), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Formato.moneda(p.valorCentavos), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
            }
            if (r.algunaConPrecioActual) {
                item {
                    Text(
                        ExportarReportes.NOTA_PRECIO_ACTUAL, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Fila(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
