package mx.ambulantpoint.misventas.ui.caja

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.CorteFormaPago
import mx.ambulantpoint.misventas.domain.regla.ReglaCorte
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.vm
import mx.ambulantpoint.misventas.ui.historial.Periodo

/** Rango de fechas opcional (null = todos). */
data class RangoCortes(val desde: Long = 0, val hasta: Long = Long.MAX_VALUE, val etiqueta: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class CortesViewModel(c: AppContainer) : ViewModel() {
    val rango = MutableStateFlow(RangoCortes())

    /** RF 5.5: del más reciente al más antiguo; no se editan ni eliminan. */
    val cortes: StateFlow<List<Caja>?> = rango.flatMapLatest { c.cajaRepo.observarCortes(it.desde, it.hasta) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun todos() { rango.value = RangoCortes() }
    fun elegir(desdeUtc: Long, hastaUtc: Long) {
        val d = Periodo.utcADiaLocal(desdeUtc)
        val h = Periodo.finDelDia(Periodo.utcADiaLocal(hastaUtc))
        rango.value = RangoCortes(d, h, "${Formato.fecha(d)} – ${Formato.fecha(h)}")
    }
}

/** RF 5.5 — Consultar cortes de caja. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCortes(alRegresar: () -> Unit, alAbrir: (String) -> Unit) {
    val modelo = vm { CortesViewModel(it) }
    val cortes by modelo.cortes.collectAsStateWithLifecycle()
    val rango by modelo.rango.collectAsStateWithLifecycle()
    var elegir by remember { mutableStateOf(false) }

    Scaffold(topBar = { BarraSuperior("Cortes de caja", alRegresar) }) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(rango.etiqueta == null, modelo::todos, { Text("Todos") }, Modifier.heightIn(min = 48.dp))
                FilterChip(rango.etiqueta != null, { elegir = true }, { Text(rango.etiqueta ?: "Elegir fechas") }, Modifier.heightIn(min = 48.dp))
            }
            val lista = cortes
            when {
                lista == null -> Unit
                lista.isEmpty() -> MensajeVacio(Mensajes.SIN_CORTES_PERIODO)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(lista, key = { it.id }) { caja ->
                        val dif = caja.diferenciaCentavos ?: 0
                        Row(
                            Modifier.fillMaxWidth().clickable { alAbrir(caja.id) }.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Apertura ${Formato.fechaHora(caja.abiertaEn)}", style = MaterialTheme.typography.bodyMedium)
                                Text("Cierre ${caja.cerradaEn?.let(Formato::fechaHora).orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Diferencia: ${Formato.moneda(dif)} (${ReglaCorte.tipoDiferencia(dif).etiqueta})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (dif < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(Formato.moneda(caja.totalVendidoCentavos ?: 0), style = MaterialTheme.typography.titleLarge)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (elegir) {
        val estado = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { elegir = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedStartDateMillis?.let { modelo.elegir(it, estado.selectedEndDateMillis ?: it) }
                    elegir = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { elegir = false }) { Text("Cancelar") } },
        ) { DateRangePicker(state = estado, modifier = Modifier.weight(1f)) }
    }
}

class DetalleCorteViewModel(c: AppContainer, cajaId: String) : ViewModel() {
    val caja: StateFlow<Caja?> = c.cajaRepo.observarCortes()
        .map { lista -> lista.firstOrNull { it.id == cajaId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val porForma: StateFlow<List<CorteFormaPago>> = c.cajaRepo.observarCorteFormaPago(cajaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** RF 5.5 — Resumen de un corte. */
@Composable
fun PantallaDetalleCorte(cajaId: String, alRegresar: () -> Unit) {
    val modelo = vm(key = cajaId) { DetalleCorteViewModel(it, cajaId) }
    val caja by modelo.caja.collectAsStateWithLifecycle()
    val porForma by modelo.porForma.collectAsStateWithLifecycle()
    Scaffold(topBar = { BarraSuperior("Corte de caja", alRegresar) }) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            caja?.let { c ->
                Text("Apertura: ${Formato.fechaHora(c.abiertaEn)}", style = MaterialTheme.typography.bodyLarge)
                Text("Cierre: ${c.cerradaEn?.let(Formato::fechaHora).orEmpty()}", style = MaterialTheme.typography.bodyLarge)
                ResumenCorte(c, porForma)
            }
        }
    }
}
