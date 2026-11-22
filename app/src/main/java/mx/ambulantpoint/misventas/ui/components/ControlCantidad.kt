package mx.ambulantpoint.misventas.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.LectorNumeros
import mx.ambulantpoint.misventas.core.Lectura
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.regla.ReglaCarga
import mx.ambulantpoint.misventas.domain.regla.ReglaPrecarga

/**
 * [ − ] cantidad [ + ]: por pieza de 1 en 1, por kilo según [pasoKilo]. Tocar el número abre el
 * teclado numérico para escribir la cantidad exacta (sin pasar de [maximo]).
 */
@Composable
fun ControlCantidad(
    milesimas: Long,
    tipoVenta: TipoVenta,
    pasoKilo: Long,
    maximo: Long,
    nombre: String,
    alCambiar: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var escribiendo by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { alCambiar(ReglaPrecarga.menos(milesimas, tipoVenta, pasoKilo)) },
            enabled = milesimas > 0,
            modifier = Modifier.size(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        TextButton(onClick = { escribiendo = true }, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp)) {
            Text(
                Formato.numeroCantidad(milesimas, tipoVenta),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (milesimas > 0) FontWeight.Bold else FontWeight.Normal,
            )
        }
        FilledTonalButton(
            onClick = { alCambiar(ReglaPrecarga.mas(milesimas, tipoVenta, pasoKilo, maximo)) },
            enabled = milesimas < maximo,
            modifier = Modifier.size(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
    if (escribiendo) {
        DialogoCantidad(milesimas, tipoVenta, maximo, nombre, alAceptar = { alCambiar(it); escribiendo = false }) { escribiendo = false }
    }
}

/** Teclado numérico para una cantidad exacta: piezas enteras o kg con decimales. */
@Composable
private fun DialogoCantidad(
    inicial: Long,
    tipoVenta: TipoVenta,
    maximo: Long,
    nombre: String,
    alAceptar: (Long) -> Unit,
    alCancelar: () -> Unit,
) {
    var texto by remember { mutableStateOf(ReglaCarga.textoCaptura(inicial, tipoVenta)) }
    var error by remember { mutableStateOf<String?>(null) }
    val aceptar = {
        when (val l = LectorNumeros.cantidad(texto.ifBlank { "0" }, tipoVenta, permitirCero = true)) {
            is Lectura.Error -> error = l.mensaje
            is Lectura.Valor -> if (l.valor > maximo) {
                error = "Solo hay ${Formato.cantidad(maximo, tipoVenta)} de $nombre"
            } else {
                alAceptar(l.valor)
            }
        }
    }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(nombre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Máximo: ${Formato.cantidad(maximo, tipoVenta)}", style = MaterialTheme.typography.bodyMedium)
                CampoTexto(
                    texto, { texto = it; error = null }, "Cantidad", error = error,
                    teclado = if (tipoVenta == TipoVenta.PESO) KeyboardType.Decimal else KeyboardType.Number,
                    sufijo = Formato.unidad(tipoVenta), imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = { TextButton(onClick = aceptar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = alCancelar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
    )
}
