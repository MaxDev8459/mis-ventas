package mx.ambulantpoint.misventas.ui.formaspago

import androidx.compose.foundation.clickable
import mx.ambulantpoint.misventas.domain.usecase.GuardarDatosTransferencia
import mx.ambulantpoint.misventas.domain.regla.ReglaTransferencia
import mx.ambulantpoint.misventas.domain.regla.DatosTransferencia
import mx.ambulantpoint.misventas.data.repository.datosTransferencia
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.flow.map
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.GuardarFormaPago
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.ui.components.atender
import mx.ambulantpoint.misventas.ui.components.vm

/** SC-01: diálogo de datos para transferencia. */
data class EdicionTransferencia(
    val clabe: String,
    val beneficiario: String,
    val banco: String,
    val existentes: Boolean,
    val errores: Map<String, String> = emptyMap(),
)

/** Diálogo abierto: agregar (forma = null) o modificar una forma de pago adicional. */
data class EdicionFormaPago(
    val forma: FormaPago?,
    val nombre: String,
    val activa: Boolean,
    val error: String? = null,
)

class FormasPagoViewModel(c: AppContainer) : ViewModel() {
    private val guardarForma = GuardarFormaPago(c.formaPagoRepo)
    private val guardarTransferencia = GuardarDatosTransferencia(c.configuracionRepo)
    private val notificador = c.notificador

    /** SC-01. */
    val datosTransferencia: StateFlow<DatosTransferencia?> = c.configuracionRepo.observar()
        .map { it.datosTransferencia() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var edicionTransferencia by mutableStateOf<EdicionTransferencia?>(null)

    fun abrirTransferencia() {
        val d = datosTransferencia.value
        edicionTransferencia = EdicionTransferencia(d?.clabe.orEmpty(), d?.beneficiario.orEmpty(), d?.banco.orEmpty(), existentes = d != null)
    }

    /** Al capturar la CLABE se propone el banco (si el vendedor no escribió otro). */
    fun cambiarClabe(texto: String) {
        val e = edicionTransferencia ?: return
        val anterior = ReglaTransferencia.bancoSugerido(ReglaTransferencia.soloDigitos(e.clabe))
        val sugerido = ReglaTransferencia.bancoSugerido(ReglaTransferencia.soloDigitos(texto))
        val banco = if ((e.banco.isBlank() || e.banco == anterior) && sugerido != null) sugerido else e.banco
        edicionTransferencia = e.copy(clabe = texto, banco = banco, errores = e.errores - Campos.CLABE - Campos.BANCO)
    }

    fun guardarDatosTransferencia() {
        val e = edicionTransferencia ?: return
        viewModelScope.launch {
            guardarTransferencia(e.clabe, e.beneficiario, e.banco)
                .atender(notificador) { errores -> edicionTransferencia = edicionTransferencia?.copy(errores = errores) }
                ?.let { it.mensaje?.let(notificador::mostrar); edicionTransferencia = null }
        }
    }

    fun borrarDatosTransferencia() {
        viewModelScope.launch {
            guardarTransferencia.borrar().atender(notificador) {}?.let { it.mensaje?.let(notificador::mostrar); edicionTransferencia = null }
        }
    }

    val formas: StateFlow<List<FormaPago>?> = c.formaPagoRepo.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var edicion by mutableStateOf<EdicionFormaPago?>(null)

    fun nueva() { edicion = EdicionFormaPago(null, "", activa = true) }

    /** "Efectivo" no se modifica (RF 3.5), así que no abre el diálogo. */
    fun editar(forma: FormaPago) {
        if (!forma.esEfectivo) edicion = EdicionFormaPago(forma, forma.nombre, forma.activa)
    }

    fun guardar() {
        val e = edicion ?: return
        viewModelScope.launch {
            guardarForma(e.forma?.id, e.nombre, e.activa)
                .atender(notificador) { errores -> edicion = edicion?.copy(error = errores[Campos.NOMBRE]) }
                ?.let {
                    it.mensaje?.let(notificador::mostrar)
                    edicion = null
                }
        }
    }

    /** Activar o desactivar desde la lista, sin abrir el diálogo. */
    fun cambiarEstado(forma: FormaPago, activa: Boolean) {
        if (forma.esEfectivo) return
        viewModelScope.launch {
            guardarForma(forma.id, forma.nombre, activa).atender(notificador) {}?.mensaje?.let(notificador::mostrar)
        }
    }
}

/** RF 3.5 — Formas de pago. */
@Composable
fun PantallaFormasPago(alRegresar: () -> Unit) {
    val modelo = vm { FormasPagoViewModel(it) }
    val formas by modelo.formas.collectAsStateWithLifecycle()
    val datosTransferencia by modelo.datosTransferencia.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BarraSuperior("Formas de pago", alRegresar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = modelo::nueva,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nueva forma de pago") },
            )
        },
    ) { relleno ->
        val lista = formas ?: return@Scaffold
        LazyColumn(Modifier.padding(relleno).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "transferencia") {
                FilaDatosTransferencia(datosTransferencia, modelo::abrirTransferencia)
                HorizontalDivider(thickness = 4.dp)
            }
            items(lista, key = { it.id }) { forma ->
                FilaFormaPago(
                    forma = forma,
                    alPulsar = { modelo.editar(forma) },
                    alCambiarEstado = { modelo.cambiarEstado(forma, it) },
                )
                HorizontalDivider()
            }
        }
    }

    modelo.edicionTransferencia?.let { DialogoDatosTransferencia(modelo, it) }

    modelo.edicion?.let { e ->
        AlertDialog(
            onDismissRequest = { modelo.edicion = null },
            title = { Text(if (e.forma == null) "Nueva forma de pago" else "Modificar forma de pago") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoTexto(
                        e.nombre, { modelo.edicion = e.copy(nombre = it, error = null) }, "Nombre de la forma de pago *",
                        error = e.error, imeAction = ImeAction.Done,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { modelo.edicion = e.copy(activa = !e.activa) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (e.activa) "Activa" else "Inactiva",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = e.activa, onCheckedChange = { modelo.edicion = e.copy(activa = it) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = modelo::guardar) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { modelo.edicion = null }) { Text("Cancelar") } },
        )
    }
}

/** SC-01: resumen de los datos para transferencia; al tocarla se capturan o modifican. */
@Composable
private fun FilaDatosTransferencia(datos: DatosTransferencia?, alPulsar: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = alPulsar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("Datos para recibir transferencias", style = MaterialTheme.typography.bodyLarge)
        Text(
            if (datos == null) "Sin capturar. Toque para agregar su CLABE."
            else "${datos.banco} · CLABE ${ReglaTransferencia.clabeEnGrupos(datos.clabe)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DialogoDatosTransferencia(modelo: FormasPagoViewModel, e: EdicionTransferencia) {
    AlertDialog(
        onDismissRequest = { modelo.edicionTransferencia = null },
        title = { Text("Datos para recibir transferencias") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTexto(
                    e.clabe, modelo::cambiarClabe, "CLABE interbancaria (18 dígitos) *",
                    error = e.errores[Campos.CLABE], teclado = KeyboardType.Number,
                )
                CampoTexto(
                    e.beneficiario,
                    { modelo.edicionTransferencia = e.copy(beneficiario = it, errores = e.errores - Campos.BENEFICIARIO) },
                    "Nombre del beneficiario *", error = e.errores[Campos.BENEFICIARIO],
                )
                CampoTexto(
                    e.banco,
                    { modelo.edicionTransferencia = e.copy(banco = it, errores = e.errores - Campos.BANCO) },
                    "Banco *", error = e.errores[Campos.BANCO], imeAction = ImeAction.Done,
                )
                if (e.existentes) {
                    TextButton(onClick = modelo::borrarDatosTransferencia, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Borrar datos", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = modelo::guardarDatosTransferencia) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { modelo.edicionTransferencia = null }) { Text("Cancelar") } },
    )
}

/** Nombre, estado e interruptor. "Efectivo" es predeterminada: siempre activa y no se edita. */
@Composable
private fun FilaFormaPago(forma: FormaPago, alPulsar: () -> Unit, alCambiarEstado: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = !forma.esEfectivo, onClick = alPulsar)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(forma.nombre, style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    forma.esEfectivo -> "Predeterminada · siempre activa"
                    forma.activa -> "Activa"
                    else -> "Inactiva"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = forma.activa,
            onCheckedChange = alCambiarEstado,
            enabled = !forma.esEfectivo,
            modifier = Modifier.semantics {
                contentDescription = (if (forma.activa) "Desactivar " else "Activar ") + forma.nombre
            },
        )
    }
}
