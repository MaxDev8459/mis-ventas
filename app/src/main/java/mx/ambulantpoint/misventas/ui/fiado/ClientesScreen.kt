package mx.ambulantpoint.misventas.ui.fiado

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.dao.AbonoConForma
import mx.ambulantpoint.misventas.data.local.dao.ClienteConSaldo
import mx.ambulantpoint.misventas.data.local.dao.VentaFiada
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.Cliente
import mx.ambulantpoint.misventas.data.local.entity.Configuracion
import mx.ambulantpoint.misventas.data.local.entity.EstadoAbono
import mx.ambulantpoint.misventas.data.local.entity.EstadoVenta
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.local.entity.TipoAbono
import mx.ambulantpoint.misventas.domain.regla.ReglaFiado
import mx.ambulantpoint.misventas.domain.regla.SaldoVenta
import mx.ambulantpoint.misventas.domain.usecase.CancelarAbono
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.GuardarAvisoSaldo
import mx.ambulantpoint.misventas.domain.usecase.GuardarCliente
import mx.ambulantpoint.misventas.domain.usecase.RegistrarAbono
import mx.ambulantpoint.misventas.ui.components.ALTO_BOTON
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonPrincipal
import mx.ambulantpoint.misventas.ui.components.BotonSecundario
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.ui.components.DialogoConfirmacion
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.atender
import mx.ambulantpoint.misventas.ui.components.vm

/** Captura del cliente (nuevo o editar). */
class FormularioCliente(val id: String?, nombre: String, telefono: String) {
    var nombre by mutableStateOf(nombre)
    var telefono by mutableStateOf(telefono)
    var errores by mutableStateOf<Map<String, String>>(emptyMap())
}

// ───────────────────────── Lista de clientes ─────────────────────────

class ClientesViewModel(c: AppContainer) : ViewModel() {
    private val repo = c.fiadoRepo
    private val guardarCliente = GuardarCliente(repo)
    private val guardarAviso = GuardarAvisoSaldo(repo)
    private val notificador = c.notificador

    val busqueda = MutableStateFlow("")

    private val todos = repo.observarClientes()

    /** Primero los que deben (de mayor a menor saldo); después los de $0 por nombre. */
    val clientes: StateFlow<List<ClienteConSaldo>?> = combine(todos, busqueda) { l, b ->
        val t = ReglaFiado.normalizarNombre(b)
        l.filter { t.isEmpty() || ReglaFiado.normalizarNombre(it.cliente.nombre).contains(t) }
            .sortedWith(compareByDescending<ClienteConSaldo> { it.saldoCentavos }.thenBy { it.cliente.nombreNormalizado })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val totalPorCobrar: StateFlow<Long> = todos.map { l -> l.sumOf { maxOf(0, it.saldoCentavos) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val aviso: StateFlow<Long> = c.configuracionRepo.observar()
        .map { it?.avisoSaldoCentavos ?: Configuracion.AVISO_SALDO_DEFECTO }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Configuracion.AVISO_SALDO_DEFECTO)

    var formulario by mutableStateOf<FormularioCliente?>(null)
        private set
    var guardando by mutableStateOf(false)
        private set

    fun nuevo() { formulario = FormularioCliente(null, busqueda.value.trim(), "") }
    fun cerrarFormulario() { formulario = null }

    fun guardar() {
        val f = formulario ?: return
        if (guardando) return
        viewModelScope.launch {
            guardando = true
            guardarCliente(f.id, f.nombre, f.telefono).atender(notificador) { f.errores = it }?.let { h ->
                h.mensaje?.let(notificador::mostrar)
                formulario = null
            }
            guardando = false
        }
    }

    // Aviso de saldo alto.
    var editandoAviso by mutableStateOf(false)
        private set
    var avisoTexto by mutableStateOf("")
    var avisoError by mutableStateOf<String?>(null)
        private set

    fun editarAviso() {
        val c = aviso.value
        avisoTexto = "${c / 100}" + if (c % 100 != 0L) ".${(c % 100).toString().padStart(2, '0')}" else ""
        avisoError = null
        editandoAviso = true
    }
    fun cerrarAviso() { editandoAviso = false }

    fun guardarAviso() {
        viewModelScope.launch {
            guardarAviso(avisoTexto).atender(notificador) { avisoError = it.values.firstOrNull() }?.let { h ->
                h.mensaje?.let(notificador::mostrar)
                editandoAviso = false
            }
        }
    }
}

/** Fiado: clientes con su saldo y total por cobrar (desde Inicio). */
@Composable
fun PantallaClientes(alRegresar: () -> Unit, alAbrir: (String) -> Unit) {
    val modelo = vm { ClientesViewModel(it) }
    val clientes by modelo.clientes.collectAsStateWithLifecycle()
    val busqueda by modelo.busqueda.collectAsStateWithLifecycle()
    val total by modelo.totalPorCobrar.collectAsStateWithLifecycle()
    val aviso by modelo.aviso.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            BarraSuperior("Clientes / Fiado", alRegresar) {
                IconButton(onClick = modelo::editarAviso) { Icon(Icons.Filled.Settings, contentDescription = "Aviso de saldo alto") }
            }
        },
    ) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            ElevatedCard(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total por cobrar", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formato.moneda(total), style = MaterialTheme.typography.displaySmall)
                    Text(
                        "Aviso de saldo alto: más de ${Formato.moneda(aviso)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = busqueda,
                onValueChange = { modelo.busqueda.value = it },
                placeholder = { Text("Buscar cliente") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            BotonPrincipal("Nuevo cliente", modelo::nuevo, Modifier.padding(12.dp))
            val lista = clientes
            when {
                lista == null -> Unit
                lista.isEmpty() && busqueda.isBlank() -> MensajeVacio("Aún no hay clientes. Se agregan aquí o al fiar una venta.")
                lista.isEmpty() -> MensajeVacio("No hay clientes que coincidan")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(lista, key = { it.cliente.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { alAbrir(c.cliente.id) }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.cliente.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                c.cliente.telefono?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text(
                                if (c.saldoCentavos > 0) Formato.moneda(c.saldoCentavos) else "Al corriente",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (c.saldoCentavos > aviso) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    c.saldoCentavos > aviso -> MaterialTheme.colorScheme.error
                                    c.saldoCentavos > 0 -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    modelo.formulario?.let { f -> DialogoCliente(f, modelo.guardando, modelo::guardar, modelo::cerrarFormulario) }

    if (modelo.editandoAviso) {
        AlertDialog(
            onDismissRequest = modelo::cerrarAviso,
            title = { Text("Aviso de saldo alto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Al fiar, si el cliente debería más de este monto, la app avisa (no bloquea).")
                    CampoTexto(
                        modelo.avisoTexto, { modelo.avisoTexto = it }, "Monto *", error = modelo.avisoError,
                        teclado = KeyboardType.Decimal, prefijo = "$", imeAction = ImeAction.Done,
                    )
                }
            },
            confirmButton = { TextButton(onClick = modelo::guardarAviso, modifier = Modifier.heightIn(min = 48.dp)) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = modelo::cerrarAviso, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DialogoCliente(f: FormularioCliente, guardando: Boolean, alGuardar: () -> Unit, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(if (f.id == null) "Nuevo cliente" else "Editar cliente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTexto(f.nombre, { f.nombre = it; f.errores = emptyMap() }, "Nombre *", error = f.errores[Campos.NOMBRE])
                CampoTexto(
                    f.telefono, { f.telefono = it.filter(Char::isDigit).take(10); f.errores = emptyMap() }, "Teléfono (opcional)",
                    error = f.errores[Campos.TELEFONO], teclado = KeyboardType.Phone, imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = { TextButton(onClick = alGuardar, enabled = !guardando, modifier = Modifier.heightIn(min = 48.dp)) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = alCerrar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
    )
}

// ───────────────────────── Detalle del cliente ─────────────────────────

/** Un renglón del historial del cliente: venta fiada o abono / devolución. */
sealed interface Movimiento {
    val fechaHora: Long
    data class DeVenta(val v: VentaFiada) : Movimiento { override val fechaHora get() = v.fechaHora }
    data class DeAbono(val a: AbonoConForma) : Movimiento { override val fechaHora get() = a.abono.fechaHora }
}

class ClienteViewModel(c: AppContainer, private val clienteId: String) : ViewModel() {
    private val repo = c.fiadoRepo
    private val notificador = c.notificador
    private val registrarAbono = RegistrarAbono(repo)
    private val cancelarAbono = CancelarAbono(repo)
    private val guardarCliente = GuardarCliente(repo)

    val cliente: StateFlow<Cliente?> = repo.observarCliente(clienteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val ventas = repo.observarVentasFiadas(clienteId)

    /** Mismo cálculo que el cobro y los abonos (ReglaFiado). */
    val saldo: StateFlow<Long> = ventas.map { l ->
        ReglaFiado.saldo(l.filter { it.estado == EstadoVenta.REGISTRADA }.map { SaldoVenta(it.ventaId, it.fechaHora, it.totalCentavos, it.abonadoCentavos) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val historial: StateFlow<List<Movimiento>> = combine(ventas, repo.observarAbonos(clienteId)) { v, a ->
        (v.map { Movimiento.DeVenta(it) } + a.map { Movimiento.DeAbono(it) }).sortedByDescending { it.fechaHora }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val formas: StateFlow<List<FormaPago>> = c.formaPagoRepo.observarActivas()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val caja: StateFlow<Caja?> = c.cajaRepo.observarAbierta()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Abono.
    var abonando by mutableStateOf(false)
        private set
    var montoAbono by mutableStateOf("")
    var formaAbono by mutableStateOf<FormaPago?>(null)
    var erroresAbono by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var guardando by mutableStateOf(false)
        private set

    fun abrirAbono() {
        if (caja.value == null) { notificador.mostrar(Mensajes.Fiado.ABRA_CAJA_ABONO); return }
        montoAbono = ""
        formaAbono = formas.value.firstOrNull { it.esEfectivo } ?: formas.value.firstOrNull()
        erroresAbono = emptyMap()
        abonando = true
    }
    fun cerrarAbono() { abonando = false }
    fun liquidarTodo() {
        val s = saldo.value
        montoAbono = "${s / 100}.${(s % 100).toString().padStart(2, '0')}"
        erroresAbono = emptyMap()
    }

    fun guardarAbono() {
        if (guardando) return
        viewModelScope.launch {
            guardando = true
            registrarAbono(clienteId, montoAbono, formaAbono).atender(notificador) { erroresAbono = it }?.let { h ->
                h.mensaje?.let(notificador::mostrar)
                abonando = false
            }
            guardando = false
        }
    }

    // Cancelar un abono equivocado.
    var porCancelar by mutableStateOf<AbonoConForma?>(null)
        private set

    fun tocarAbono(a: AbonoConForma) {
        if (a.abono.tipo != TipoAbono.ABONO || a.abono.estado != EstadoAbono.REGISTRADO) return
        viewModelScope.launch {
            if (repo.esCancelable(a.abono.id)) porCancelar = a else notificador.mostrar(Mensajes.Fiado.ABONO_NO_CANCELABLE)
        }
    }
    fun cerrarCancelar() { porCancelar = null }
    fun confirmarCancelar() {
        val a = porCancelar ?: return
        porCancelar = null
        viewModelScope.launch {
            cancelarAbono(a.abono.id).atender(notificador) {}?.let { h -> h.mensaje?.let(notificador::mostrar) }
        }
    }

    // Editar.
    var formulario by mutableStateOf<FormularioCliente?>(null)
        private set
    fun editar() { cliente.value?.let { formulario = FormularioCliente(it.id, it.nombre, it.telefono.orEmpty()) } }
    fun cerrarFormulario() { formulario = null }
    fun guardarCliente() {
        val f = formulario ?: return
        viewModelScope.launch {
            guardarCliente(f.id, f.nombre, f.telefono).atender(notificador) { f.errores = it }?.let { h ->
                h.mensaje?.let(notificador::mostrar)
                formulario = null
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaCliente(clienteId: String, alRegresar: () -> Unit, alVerVenta: (String) -> Unit) {
    val modelo = vm(key = clienteId) { ClienteViewModel(it, clienteId) }
    val cliente by modelo.cliente.collectAsStateWithLifecycle()
    val saldo by modelo.saldo.collectAsStateWithLifecycle()
    val historial by modelo.historial.collectAsStateWithLifecycle()
    val formas by modelo.formas.collectAsStateWithLifecycle()

    Scaffold(topBar = { BarraSuperior(cliente?.nombre ?: "Cliente", alRegresar) }) { relleno ->
        LazyColumn(Modifier.padding(relleno).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Debe", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formato.moneda(saldo), style = MaterialTheme.typography.displaySmall)
                cliente?.telefono?.let { Text("Tel. $it", style = MaterialTheme.typography.bodyLarge) }
            }
            item { BotonPrincipal("Registrar abono", modelo::abrirAbono, habilitado = saldo > 0) }
            item { BotonSecundario("Editar cliente", modelo::editar) }
            item {
                Text("Historial", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Un abono de la caja abierta se puede cancelar tocándolo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (historial.isEmpty()) item { MensajeVacio("Sin ventas fiadas ni abonos") }
            items(historial, key = { m -> when (m) { is Movimiento.DeVenta -> "v" + m.v.ventaId; is Movimiento.DeAbono -> "a" + m.a.abono.id } }) { m ->
                when (m) {
                    is Movimiento.DeVenta -> FilaVenta(m.v) { alVerVenta(m.v.ventaId) }
                    is Movimiento.DeAbono -> FilaAbono(m.a) { modelo.tocarAbono(m.a) }
                }
                HorizontalDivider()
            }
        }
    }

    if (modelo.abonando) {
        AlertDialog(
            onDismissRequest = modelo::cerrarAbono,
            title = { Text("Registrar abono") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Debe ${Formato.moneda(saldo)}. Se aplica de la venta más vieja a la más nueva.")
                    CampoTexto(
                        modelo.montoAbono, { modelo.montoAbono = it }, "Monto *", error = modelo.erroresAbono[Campos.CANTIDAD],
                        teclado = KeyboardType.Decimal, prefijo = "$", imeAction = ImeAction.Done,
                    )
                    TextButton(onClick = modelo::liquidarTodo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Liquidar todo") }
                    Text("Forma de pago *")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        formas.forEach { f ->
                            FilterChip(
                                selected = modelo.formaAbono?.id == f.id,
                                onClick = { modelo.formaAbono = f },
                                label = { Text(f.nombre) },
                                modifier = Modifier.heightIn(min = ALTO_BOTON),
                            )
                        }
                    }
                    modelo.erroresAbono[Campos.FORMA_PAGO]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = modelo::guardarAbono, enabled = !modelo.guardando, modifier = Modifier.heightIn(min = 48.dp)) { Text("Registrar") } },
            dismissButton = { TextButton(onClick = modelo::cerrarAbono, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
        )
    }

    modelo.porCancelar?.let { a ->
        DialogoConfirmacion(
            texto = Mensajes.Fiado.confirmarCancelarAbono(Formato.moneda(a.abono.montoCentavos)),
            alConfirmar = modelo::confirmarCancelar,
            alCancelar = modelo::cerrarCancelar,
            textoConfirmar = "Cancelar abono",
            textoCancelar = "Regresar",
        )
    }

    modelo.formulario?.let { f -> DialogoCliente(f, false, modelo::guardarCliente, modelo::cerrarFormulario) }
}

@Composable
private fun FilaVenta(v: VentaFiada, alTocar: () -> Unit) {
    val cancelada = v.estado == EstadoVenta.CANCELADA
    Row(Modifier.fillMaxWidth().clickable(onClick = alTocar).heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (cancelada) "Venta fiada (cancelada)" else "Venta fiada", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(Formato.fechaHora(v.fechaHora), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!cancelada) {
                Text(
                    "Pagado ${Formato.moneda(v.abonadoCentavos)} · debe ${Formato.moneda(maxOf(0, v.totalCentavos - v.abonadoCentavos))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            Formato.moneda(v.totalCentavos), style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (cancelada) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun FilaAbono(a: AbonoConForma, alTocar: () -> Unit) {
    val ab = a.abono
    val cancelado = ab.estado == EstadoAbono.CANCELADO
    val devolucion = ab.tipo == TipoAbono.DEVOLUCION
    Row(Modifier.fillMaxWidth().clickable(onClick = alTocar).heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    devolucion -> "Devolución en efectivo"
                    cancelado -> "Abono (cancelado)"
                    else -> "Abono · ${a.formaPagoNombre}"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(Formato.fechaHora(ab.fechaHora), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            (if (devolucion) "−" else "") + Formato.moneda(ab.montoCentavos),
            style = MaterialTheme.typography.bodyLarge,
            color = if (devolucion || cancelado) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            textDecoration = if (cancelado) TextDecoration.LineThrough else null,
        )
    }
}
