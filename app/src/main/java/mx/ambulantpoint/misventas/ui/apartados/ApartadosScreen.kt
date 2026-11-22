package mx.ambulantpoint.misventas.ui.apartados

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.dao.ApartadoConDetalles
import mx.ambulantpoint.misventas.data.local.entity.EstadoApartado
import mx.ambulantpoint.misventas.domain.regla.MomentoApartado
import mx.ambulantpoint.misventas.domain.regla.ProductoApartado
import mx.ambulantpoint.misventas.domain.regla.ReglaApartado
import mx.ambulantpoint.misventas.domain.usecase.CamposApartado
import mx.ambulantpoint.misventas.domain.usecase.CancelarApartado
import mx.ambulantpoint.misventas.domain.usecase.EditarApartado
import mx.ambulantpoint.misventas.domain.venta.Carrito
import mx.ambulantpoint.misventas.domain.venta.EntregaApartado
import mx.ambulantpoint.misventas.domain.venta.ProductoEnVenta
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonPrincipal
import mx.ambulantpoint.misventas.ui.components.BotonSecundario
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.ui.components.DialogoConfirmacion
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.atender
import mx.ambulantpoint.misventas.ui.components.vm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Datos de entrega que se capturan al apartar o al editar. */
class FormularioApartado(
    nombre: String = "",
    telefono: String = "",
    lugar: String = "",
    fecha: LocalDate? = LocalDate.now(),
    hora: LocalTime? = null,
) {
    var nombre by mutableStateOf(nombre)
    var telefono by mutableStateOf(telefono)
    var lugar by mutableStateOf(lugar)
    var fecha by mutableStateOf(fecha)
    var hora by mutableStateOf(hora)
    var errores by mutableStateOf<Map<String, String>>(emptyMap())

    /** Fecha + hora en la zona del teléfono; null si falta alguna. */
    fun entregaEn(zona: ZoneId = ZoneId.systemDefault()): Long? {
        val f = fecha ?: return null
        val h = hora ?: return null
        return f.atTime(h).atZone(zona).toInstant().toEpochMilli()
    }

    companion object {
        fun de(a: mx.ambulantpoint.misventas.data.local.entity.Apartado, zona: ZoneId = ZoneId.systemDefault()): FormularioApartado {
            val t = Instant.ofEpochMilli(a.entregaEn).atZone(zona)
            return FormularioApartado(a.nombre, a.telefono.orEmpty(), a.lugar, t.toLocalDate(), t.toLocalTime().withSecond(0).withNano(0))
        }
    }
}

/** Formulario del apartado (nombre, teléfono, fecha, hora y lugar). Se usa al apartar y al editar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoApartado(titulo: String, f: FormularioApartado, guardando: Boolean, alGuardar: () -> Unit, alCerrar: () -> Unit) {
    var eligiendoFecha by remember { mutableStateOf(false) }
    var eligiendoHora by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(titulo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTexto(f.nombre, { f.nombre = it; f.errores = emptyMap() }, "Nombre *", error = f.errores[CamposApartado.NOMBRE])
                CampoTexto(
                    f.telefono, { f.telefono = it.filter(Char::isDigit).take(10); f.errores = emptyMap() }, "Teléfono (opcional)",
                    error = f.errores[CamposApartado.TELEFONO], teclado = KeyboardType.Phone,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { eligiendoFecha = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(f.fecha?.let { "%02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) } ?: "Fecha *")
                    }
                    OutlinedButton(onClick = { eligiendoHora = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(f.hora?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "Hora *")
                    }
                }
                f.errores[CamposApartado.ENTREGA]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                CampoTexto(
                    f.lugar, { if (it.length <= ReglaApartado.LUGAR_MAX) f.lugar = it; f.errores = emptyMap() }, "Lugar de entrega *",
                    error = f.errores[CamposApartado.LUGAR], imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = { TextButton(onClick = alGuardar, enabled = !guardando, modifier = Modifier.heightIn(min = 48.dp)) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = alCerrar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
    )

    if (eligiendoFecha) {
        // El DatePicker trabaja en UTC: el día elegido se toma tal cual (sin hora).
        val hoyUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = f.fecha?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= hoyUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { eligiendoFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { f.fecha = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    f.errores = emptyMap()
                    eligiendoFecha = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { eligiendoFecha = false }) { Text("Cancelar") } },
        ) { DatePicker(state = estado) }
    }

    if (eligiendoHora) {
        val inicial = f.hora ?: LocalTime.now().plusHours(1).withMinute(0)
        val estado = rememberTimePickerState(initialHour = inicial.hour, initialMinute = inicial.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { eligiendoHora = false },
            title = { Text("Hora de entrega") },
            text = { TimePicker(state = estado) },
            confirmButton = {
                TextButton(onClick = {
                    f.hora = LocalTime.of(estado.hour, estado.minute)
                    f.errores = emptyMap()
                    eligiendoHora = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { eligiendoHora = false }) { Text("Cancelar") } },
        )
    }
}

private fun productosDe(a: ApartadoConDetalles) =
    ReglaApartado.productos(a.detalles.map { ProductoApartado(it.nombreProducto, it.tipoVenta, it.cantidadMilesimas) })

private fun cuando(ms: Long): String = "${Formato.fecha(ms)} · ${Formato.hora(ms)}"

// ───────────────────────── Lista ─────────────────────────

class ApartadosViewModel(c: AppContainer) : ViewModel() {
    val pendientes: StateFlow<List<ApartadoConDetalles>?> = c.apartadoRepo.observarPendientes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val historial: StateFlow<List<ApartadoConDetalles>> = c.apartadoRepo.observarHistorial()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val reloj = c.reloj
    fun ahora() = reloj.ahora()
}

/** Apartados: pendientes por fecha de entrega e historial. */
@Composable
fun PantallaApartados(alRegresar: () -> Unit, alAbrir: (String) -> Unit, alNuevo: () -> Unit) {
    val modelo = vm { ApartadosViewModel(it) }
    val pendientes by modelo.pendientes.collectAsStateWithLifecycle()
    val historial by modelo.historial.collectAsStateWithLifecycle()
    val ahora = modelo.ahora()
    val zona = ZoneId.systemDefault()

    Scaffold(topBar = { BarraSuperior("Apartados", alRegresar) }) { relleno ->
        LazyColumn(Modifier.padding(relleno).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                BotonPrincipal("Nuevo apartado", alNuevo)
                Text(
                    "Se arma en Vender: agrega los productos y toca \"Apartar\".",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val lista = pendientes
            if (lista != null && lista.isEmpty()) item { MensajeVacio("No hay apartados pendientes") }
            items(lista.orEmpty(), key = { it.apartado.id }) { a ->
                val m = ReglaApartado.momento(a.apartado.entregaEn, ahora, zona)
                ElevatedCard(Modifier.fillMaxWidth().clickable { alAbrir(a.apartado.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Text(a.apartado.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(Formato.moneda(a.apartado.totalCentavos), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            when (m) {
                                MomentoApartado.ATRASADO -> "Atrasado · ${cuando(a.apartado.entregaEn)}"
                                MomentoApartado.HOY -> "Hoy · ${Formato.hora(a.apartado.entregaEn)}"
                                MomentoApartado.DESPUES -> cuando(a.apartado.entregaEn)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (m != MomentoApartado.DESPUES) FontWeight.SemiBold else FontWeight.Normal,
                            color = when (m) {
                                MomentoApartado.ATRASADO -> MaterialTheme.colorScheme.error
                                MomentoApartado.HOY -> MaterialTheme.colorScheme.primary
                                MomentoApartado.DESPUES -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(a.apartado.lugar, style = MaterialTheme.typography.bodyMedium)
                        Text(productosDe(a), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (historial.isNotEmpty()) {
                item { Text("Historial", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
                items(historial, key = { "h" + it.apartado.id }) { a ->
                    Row(Modifier.fillMaxWidth().clickable { alAbrir(a.apartado.id) }.heightIn(min = 56.dp).padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(a.apartado.nombre, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${etiqueta(a.apartado.estado)} · entrega ${cuando(a.apartado.entregaEn)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Formato.moneda(a.apartado.totalCentavos), style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun etiqueta(e: EstadoApartado) = when (e) {
    EstadoApartado.PENDIENTE -> "Pendiente"
    EstadoApartado.ENTREGADO -> "Entregado"
    EstadoApartado.CANCELADO -> "Cancelado"
    EstadoApartado.LIBERADO -> "Liberado (no se entregó)"
}

// ───────────────────────── Detalle ─────────────────────────

class ApartadoViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    private val notificador = c.notificador
    private val editar = EditarApartado(c.apartadoRepo) { c.reloj.ahora() }
    private val cancelar = CancelarApartado(c.apartadoRepo)

    val apartado: StateFlow<ApartadoConDetalles?> = c.apartadoRepo.observar(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var formulario by mutableStateOf<FormularioApartado?>(null)
        private set
    var guardando by mutableStateOf(false)
        private set
    var confirmandoCancelar by mutableStateOf(false)

    /** El cobro se abre con sus productos: la pantalla navega a Vender. */
    var aEntregar by mutableStateOf(false)
        private set

    fun abrirEditar() { apartado.value?.let { formulario = FormularioApartado.de(it.apartado) } }
    fun cerrarEditar() { formulario = null }

    fun guardar() {
        val f = formulario ?: return
        if (guardando) return
        viewModelScope.launch {
            guardando = true
            editar(id, f.nombre, f.telefono, f.lugar, f.entregaEn()).atender(notificador) { f.errores = it }?.let { h ->
                h.mensaje?.let(notificador::mostrar)
                formulario = null
            }
            guardando = false
        }
    }

    fun cancelarApartado() {
        confirmandoCancelar = false
        viewModelScope.launch {
            cancelar(id).atender(notificador) {}?.let { h -> h.mensaje?.let(notificador::mostrar) }
        }
    }

    /** Entregar: requiere caja abierta; abre el cobro con los productos y su precio fijo. */
    fun entregar() {
        val a = apartado.value ?: return
        viewModelScope.launch {
            if (c.cajaRepo.abierta() == null) { notificador.mostrar(Mensajes.Apartado.ABRA_CAJA_ENTREGAR); return@launch }
            var carrito = Carrito()
            for (d in a.detalles) {
                val p = ProductoEnVenta(d.productoId, d.nombreProducto, d.tipoVenta, d.precioUnitarioCentavos, d.cantidadMilesimas)
                carrito = (carrito.agregar(p, d.cantidadMilesimas) as? Resultado.Exito)?.valor ?: carrito
            }
            c.apartadoPorEntregar.value = EntregaApartado(a.apartado.id, a.apartado.nombre, carrito)
            aEntregar = true
        }
    }

    fun entregaAbierta() { aEntregar = false }
}

@Composable
fun PantallaApartado(apartadoId: String, alRegresar: () -> Unit, alEntregar: () -> Unit, alVerVenta: (String) -> Unit) {
    val modelo = vm(key = apartadoId) { ApartadoViewModel(it, apartadoId) }
    val a by modelo.apartado.collectAsStateWithLifecycle()
    LaunchedEffect(modelo.aEntregar) { if (modelo.aEntregar) { modelo.entregaAbierta(); alEntregar() } }

    Scaffold(topBar = { BarraSuperior("Apartado", alRegresar) }) { relleno ->
        Column(
            Modifier.padding(relleno).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val x = a ?: return@Column
            val ap = x.apartado
            Text(ap.nombre, style = MaterialTheme.typography.headlineSmall)
            ap.telefono?.let { Text("Tel. $it", style = MaterialTheme.typography.bodyLarge) }
            Text("Entrega: ${cuando(ap.entregaEn)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text("Lugar: ${ap.lugar}", style = MaterialTheme.typography.bodyLarge)
            Text("Estado: ${etiqueta(ap.estado)}", style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            x.detalles.forEach { d ->
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(d.nombreProducto, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${Formato.cantidad(d.cantidadMilesimas, d.tipoVenta)} × ${Formato.moneda(d.precioUnitarioCentavos)}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Formato.moneda(d.importeCentavos), style = MaterialTheme.typography.bodyLarge)
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth()) {
                Text("Total (precio fijo)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(Formato.moneda(ap.totalCentavos), style = MaterialTheme.typography.titleLarge)
            }
            if (ap.estado == EstadoApartado.PENDIENTE) {
                BotonPrincipal("Entregar y cobrar", modelo::entregar)
                BotonSecundario("Editar", modelo::abrirEditar)
                BotonSecundario("Cancelar apartado", { modelo.confirmandoCancelar = true })
            }
            ap.ventaId?.let { v -> BotonSecundario("Ver venta", { alVerVenta(v) }) }
        }
    }

    modelo.formulario?.let { f -> DialogoApartado("Editar apartado", f, modelo.guardando, modelo::guardar, modelo::cerrarEditar) }
    if (modelo.confirmandoCancelar) {
        DialogoConfirmacion(
            texto = Mensajes.Apartado.confirmarCancelar(a?.apartado?.nombre.orEmpty()),
            alConfirmar = modelo::cancelarApartado,
            alCancelar = { modelo.confirmandoCancelar = false },
            textoConfirmar = "Cancelar apartado",
            textoCancelar = "Regresar",
        )
    }
}
