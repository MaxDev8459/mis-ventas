package mx.ambulantpoint.misventas.ui.historial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.dao.VentaConFormaPago
import mx.ambulantpoint.misventas.data.local.entity.EstadoVenta
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.vm
import java.util.Calendar
import java.util.TimeZone

/** Periodo en hora local: [desde, hasta] en epoch ms. */
data class FiltroHistorial(
    val desde: Long = Periodo.inicioDeHoy(),
    val hasta: Long = Periodo.finDeHoy(),
    val etiqueta: String = "Hoy",
    val estado: EstadoVenta? = null,
    val formaPagoId: String? = null,
)

object Periodo {
    private fun cal(ms: Long = System.currentTimeMillis()) = Calendar.getInstance().apply { timeInMillis = ms }

    fun inicioDelDia(ms: Long): Long = cal(ms).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun finDelDia(ms: Long): Long = inicioDelDia(ms) + 24L * 60 * 60 * 1000 - 1
    fun inicioDeHoy() = inicioDelDia(System.currentTimeMillis())
    fun finDeHoy() = finDelDia(System.currentTimeMillis())

    fun haceDias(dias: Int): Long = cal().apply { add(Calendar.DAY_OF_YEAR, -dias) }.timeInMillis.let(::inicioDelDia)

    /** El selector de fechas entrega medianoche UTC; se convierte al mismo día en hora local. */
    fun utcADiaLocal(msUtc: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = msUtc }
        return Calendar.getInstance().apply {
            clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistorialViewModel(c: AppContainer) : ViewModel() {
    val filtro = MutableStateFlow(FiltroHistorial())

    val ventas: StateFlow<List<VentaConFormaPago>?> = filtro
        .flatMapLatest { c.ventaRepo.observarHistorial(it.desde, it.hasta, it.estado, it.formaPagoId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val formas: StateFlow<List<FormaPago>> = c.formaPagoRepo.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun hoy() = filtro.update { it.copy(desde = Periodo.inicioDeHoy(), hasta = Periodo.finDeHoy(), etiqueta = "Hoy") }
    fun ultimos7() = filtro.update { it.copy(desde = Periodo.haceDias(6), hasta = Periodo.finDeHoy(), etiqueta = "7 días") }
    fun rango(desdeUtc: Long, hastaUtc: Long) = filtro.update {
        val d = Periodo.utcADiaLocal(desdeUtc)
        val h = Periodo.finDelDia(Periodo.utcADiaLocal(hastaUtc))
        it.copy(desde = d, hasta = h, etiqueta = "${Formato.fecha(d)} – ${Formato.fecha(h)}")
    }
    fun estado(e: EstadoVenta?) = filtro.update { it.copy(estado = e) }
    fun forma(id: String?) = filtro.update { it.copy(formaPagoId = id) }
}

fun etiquetaEstado(e: EstadoVenta) = if (e == EstadoVenta.REGISTRADA) "Registrada" else "Cancelada"

/** RF 3.4 — Consultar historial de ventas (por defecto, el día actual). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaHistorial(alRegresar: () -> Unit, alAbrir: (String) -> Unit) {
    val modelo = vm { HistorialViewModel(it) }
    val filtro by modelo.filtro.collectAsStateWithLifecycle()
    val ventas by modelo.ventas.collectAsStateWithLifecycle()
    val formas by modelo.formas.collectAsStateWithLifecycle()
    var elegirFechas by remember { mutableStateOf(false) }
    var menuForma by remember { mutableStateOf(false) }

    Scaffold(topBar = { BarraSuperior("Historial de ventas", alRegresar) }) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(filtro.etiqueta == "Hoy", modelo::hoy, { Text("Hoy") }, Modifier.heightIn(min = 48.dp))
                FilterChip(filtro.etiqueta == "7 días", modelo::ultimos7, { Text("7 días") }, Modifier.heightIn(min = 48.dp))
                val esRango = filtro.etiqueta != "Hoy" && filtro.etiqueta != "7 días"
                FilterChip(esRango, { elegirFechas = true }, { Text(if (esRango) filtro.etiqueta else "Elegir fechas") }, Modifier.heightIn(min = 48.dp))
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(filtro.estado == null, { modelo.estado(null) }, { Text("Todas") }, Modifier.heightIn(min = 48.dp))
                FilterChip(filtro.estado == EstadoVenta.REGISTRADA, { modelo.estado(EstadoVenta.REGISTRADA) }, { Text("Registradas") }, Modifier.heightIn(min = 48.dp))
                FilterChip(filtro.estado == EstadoVenta.CANCELADA, { modelo.estado(EstadoVenta.CANCELADA) }, { Text("Canceladas") }, Modifier.heightIn(min = 48.dp))
                Box {
                    FilterChip(
                        filtro.formaPagoId != null, { menuForma = true },
                        { Text(formas.firstOrNull { it.id == filtro.formaPagoId }?.nombre ?: "Forma de pago") },
                        Modifier.heightIn(min = 48.dp),
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    )
                    DropdownMenu(menuForma, { menuForma = false }) {
                        DropdownMenuItem({ Text("Todas") }, { modelo.forma(null); menuForma = false })
                        formas.forEach { f -> DropdownMenuItem({ Text(f.nombre) }, { modelo.forma(f.id); menuForma = false }) }
                    }
                }
            }

            val lista = ventas
            when {
                lista == null -> Unit
                lista.isEmpty() -> MensajeVacio(Mensajes.SIN_VENTAS_PERIODO)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(lista, key = { it.venta.id }) { v ->
                        Row(
                            Modifier.fillMaxWidth().clickable { alAbrir(v.venta.id) }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(Formato.fechaHora(v.venta.fechaHora), style = MaterialTheme.typography.bodyLarge)
                                Text("${v.formaPagoNombre} · ${etiquetaEstado(v.venta.estado)}", style = MaterialTheme.typography.bodyMedium,
                                    color = if (v.venta.estado == EstadoVenta.CANCELADA) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Formato.moneda(v.venta.totalCentavos), style = MaterialTheme.typography.titleLarge)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (elegirFechas) {
        val estado = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { elegirFechas = false },
            confirmButton = {
                TextButton(onClick = {
                    val d = estado.selectedStartDateMillis
                    if (d != null) modelo.rango(d, estado.selectedEndDateMillis ?: d)
                    elegirFechas = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { elegirFechas = false }) { Text("Cancelar") } },
        ) {
            DateRangePicker(state = estado, modifier = Modifier.weight(1f))
        }
    }
}
