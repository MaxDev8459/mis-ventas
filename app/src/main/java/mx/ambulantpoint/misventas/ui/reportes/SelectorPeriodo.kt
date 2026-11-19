package mx.ambulantpoint.misventas.ui.reportes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.RelojSistema
import mx.ambulantpoint.misventas.domain.regla.Periodo
import mx.ambulantpoint.misventas.domain.regla.ReglaPeriodos
import mx.ambulantpoint.misventas.domain.regla.TipoPeriodo
import mx.ambulantpoint.misventas.ui.historial.Periodo as PeriodoHistorial

/**
 * Selector de rango de fechas de Material 3. Su encabezado de fábrica ("Fecha de inicio - Fecha de
 * finalización") no cabe en el diálogo con la tipografía del tema; se reemplaza por uno compacto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorRangoFechas(estado: DateRangePickerState, modifier: Modifier = Modifier) {
    // El selector trabaja en medianoche UTC; se muestra el mismo día en hora local.
    fun dia(msUtc: Long?): String? = msUtc?.let { Formato.fecha(PeriodoHistorial.utcADiaLocal(it)) }
    DateRangePicker(
        state = estado,
        modifier = modifier,
        headline = {
            Text(
                "${dia(estado.selectedStartDateMillis) ?: "Inicio"} – ${dia(estado.selectedEndDateMillis) ?: "Fin"}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
            )
        },
    )
}

/** Periodo con el que abre un reporte: Hoy. */
fun periodoInicial(): Periodo = ReglaPeriodos.calcular(TipoPeriodo.HOY, RelojSistema.ahora())

/** Módulo 6: Hoy, Esta semana, Este mes y Rango personalizado, con las fechas que abarca. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorPeriodo(periodo: Periodo, alCambiar: (Periodo) -> Unit) {
    var elegirFechas by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TipoPeriodo.HOY, TipoPeriodo.SEMANA, TipoPeriodo.MES).forEach { tipo ->
                FilterChip(
                    selected = periodo.tipo == tipo,
                    onClick = { alCambiar(ReglaPeriodos.calcular(tipo, RelojSistema.ahora())) },
                    label = { Text(tipo.etiqueta) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            FilterChip(
                selected = periodo.tipo == TipoPeriodo.RANGO,
                onClick = { elegirFechas = true },
                label = { Text("Rango") },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        Text(
            if (Formato.fecha(periodo.desde) == Formato.fecha(periodo.hasta)) Formato.fecha(periodo.desde)
            else "${Formato.fecha(periodo.desde)} – ${Formato.fecha(periodo.hasta)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }

    if (elegirFechas) {
        val estado = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { elegirFechas = false },
            confirmButton = {
                TextButton(onClick = {
                    val d = estado.selectedStartDateMillis
                    if (d != null) {
                        // El selector entrega medianoche UTC; se convierte al mismo día en hora local.
                        val inicio = PeriodoHistorial.utcADiaLocal(d)
                        val fin = PeriodoHistorial.utcADiaLocal(estado.selectedEndDateMillis ?: d)
                        alCambiar(ReglaPeriodos.rango(inicio, fin))
                    }
                    elegirFechas = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { elegirFechas = false }) { Text("Cancelar") } },
        ) {
            SelectorRangoFechas(estado, Modifier.weight(1f))
        }
    }
}
