package mx.ambulantpoint.misventas.ui.caja

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.entity.Configuracion
import mx.ambulantpoint.misventas.data.local.entity.Precarga
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.domain.regla.ProductoParaCarga
import mx.ambulantpoint.misventas.domain.regla.ReglaPrecarga
import mx.ambulantpoint.misventas.domain.regla.RenglonCarga
import mx.ambulantpoint.misventas.domain.venta.ReglaFiltroVenta
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonPrincipal
import mx.ambulantpoint.misventas.ui.components.BotonSecundario
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.ui.components.ControlCantidad
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.vm

/** Carga del día v2: + / − por producto, [Máx] y precargas (docs/08-ESPEC-CARGA-V2.md). */
class CargaViewModel(c: AppContainer) : ViewModel() {
    private val cargaRepo = c.cargaRepo
    private val precargaRepo = c.precargaRepo
    private val configuracion = c.configuracionRepo
    private val productoRepo = c.productoRepo
    private val notificador = c.notificador

    val busqueda = MutableStateFlow("")

    /** Productos activos con existencia; `existenciaMilesimas` = lo disponible al abrir la caja. */
    private val productos = MutableStateFlow<List<ProductoParaCarga>?>(null)

    val visibles: StateFlow<List<ProductoParaCarga>?> = combine(productos, busqueda) { lista, texto ->
        val t = ReglaFiltroVenta.normalizar(texto)
        lista?.filter { t.isEmpty() || ReglaFiltroVenta.normalizar(it.nombre).contains(t) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Unidad para kilo (a nivel cuenta). */
    val pasoKilo: StateFlow<Long> = configuracion.observar()
        .map { it?.pasoKiloMilesimas ?: Configuracion.PASO_KILO_DEFECTO }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Configuracion.PASO_KILO_DEFECTO)

    val precargas: StateFlow<List<Precarga>> = precargaRepo.observarActivas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cantidad por producto (milésimas). */
    var cantidades by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    var guardando by mutableStateOf(false)
        private set
    var terminado by mutableStateOf(false)
        private set
    var sinCaja by mutableStateOf(false)
        private set

    // Panel de precargas.
    var panelAbierto by mutableStateOf(false)
    var nDeCada by mutableStateOf(10L)
    var precargaElegida by mutableStateOf<Precarga?>(null)

    // "Guardar también como precarga".
    var guardarComoPrecarga by mutableStateOf(false)
    var nombrePrecarga by mutableStateOf("")
    var errorNombre by mutableStateOf<String?>(null)
        private set

    private var cajaId: String? = null

    val totalProductos: Int get() = cantidades.count { it.value > 0 }

    init {
        viewModelScope.launch {
            val caja = c.cajaRepo.abierta()
            if (caja == null) { sinCaja = true; productos.value = emptyList(); return@launch }
            cajaId = caja.id
            val lista = productoRepo.observarParaVenta().first().map { it.producto }
            val limites = cargaRepo.disponibles(caja.id, caja.abiertaEn, lista.associate { it.id to it.existenciaMilesimas })
            // Carga nueva: todo en cero. Si ya hay carga guardada, se muestra lo cargado.
            val actual = cargaRepo.cargaActual(caja.id)
            productos.value = lista.map { ProductoParaCarga(it.id, it.nombre, it.tipoVenta, limites[it.id] ?: it.existenciaMilesimas) }
            cantidades = lista.associate { it.id to (actual[it.id] ?: 0L) }
        }
    }

    fun fijar(productoId: String, milesimas: Long) { cantidades = cantidades + (productoId to milesimas) }

    fun maximo(p: ProductoParaCarga) = fijar(p.id, p.existenciaMilesimas)

    fun cambiarPasoKilo(milesimas: Long) {
        viewModelScope.launch { configuracion.guardarPasoKilo(milesimas) }
    }

    /** Repetir la carga de la última caja que tuvo carga. */
    fun repetirUltima() {
        val id = cajaId ?: return
        viewModelScope.launch {
            val anterior = cargaRepo.cargaAnterior(id)
            if (anterior.isEmpty()) { notificador.mostrar("No hay una carga anterior"); return@launch }
            aplicarCantidades(anterior)
            panelAbierto = false
        }
    }

    fun aplicarNDeCada() {
        cantidades = ReglaPrecarga.nDeCada(nDeCada, pasoKilo.value, productos.value.orEmpty())
        panelAbierto = false
        notificador.mostrar("Se aplicó $nDeCada de cada producto. Ajuste y guarde")
    }

    fun aplicarPrecarga(p: Precarga) {
        viewModelScope.launch {
            aplicarCantidades(ReglaPrecarga.decodificar(p.cantidades), p.nombre)
            precargaRepo.marcarUso(p.id)
            panelAbierto = false
        }
    }

    private suspend fun aplicarCantidades(origen: Map<String, Long>, nombre: String? = null) {
        val nombres = origen.keys.associateWith { productoRepo.porId(it)?.nombre }
        val r = ReglaPrecarga.aplicar(origen, productos.value.orEmpty()) { nombres[it] }
        cantidades = r.cantidades
        val base = if (nombre == null) "Se aplicó la última carga" else "Se aplicó \"$nombre\""
        notificador.mostrar(
            listOfNotNull(base + ". Ajuste y guarde", r.omitidos.takeIf { it.isNotEmpty() }?.let { "Sin existencia o inactivos: ${it.joinToString(", ")}" })
                .joinToString("\n"),
        )
    }

    fun renombrar(p: Precarga, nombre: String) {
        viewModelScope.launch {
            when (val r = precargaRepo.renombrar(p.id, nombre)) {
                is Resultado.Exito -> { precargaElegida = null; notificador.mostrar("Precarga renombrada") }
                is Resultado.Fallo -> notificador.mostrar(
                    if (r.falla == FallaDatos.NombreDuplicado) "Ya hay una precarga con ese nombre" else "Escriba un nombre",
                )
            }
        }
    }

    fun quitar(p: Precarga) {
        viewModelScope.launch {
            precargaRepo.quitar(p.id)
            precargaElegida = null
            notificador.mostrar("Se quitó la precarga \"${p.nombre}\"")
        }
    }

    fun cambiarNombrePrecarga(t: String) { nombrePrecarga = t; errorNombre = null }

    fun guardar() {
        val id = cajaId ?: return
        if (guardando) return
        if (guardarComoPrecarga && nombrePrecarga.isBlank()) { errorNombre = "Escriba un nombre para la precarga"; return }
        viewModelScope.launch {
            guardando = true
            when (val r = cargaRepo.guardar(id, cantidades)) {
                is Resultado.Exito -> {
                    val extra = if (guardarComoPrecarga) {
                        precargaRepo.guardarComo(nombrePrecarga, cantidades)
                        " y la precarga \"${nombrePrecarga.trim()}\""
                    } else {
                        ""
                    }
                    notificador.mostrar("Se guardó la carga del día$extra")
                    terminado = true
                }
                is Resultado.Fallo -> notificador.mostrar((r.falla as? FallaDatos.CargaExcedeExistencia)?.mensaje ?: Mensajes.ABRA_CAJA)
            }
            guardando = false
        }
    }
}

/**
 * Carga del día. Al abrir caja se llega con [desdeApertura] = true y se puede omitir. Sin casillas:
 * + / −, tocar el número para escribirlo y [Máx].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCarga(desdeApertura: Boolean, alTerminar: () -> Unit) {
    val modelo = vm { CargaViewModel(it) }
    val lista by modelo.visibles.collectAsStateWithLifecycle()
    val busqueda by modelo.busqueda.collectAsStateWithLifecycle()
    val pasoKilo by modelo.pasoKilo.collectAsStateWithLifecycle()
    LaunchedEffect(modelo.terminado) { if (modelo.terminado) alTerminar() }

    Scaffold(
        topBar = { BarraSuperior("Carga del día", alTerminar) },
        bottomBar = { if (!modelo.sinCaja) BarraGuardar(modelo, desdeApertura, alTerminar) },
    ) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            if (modelo.sinCaja) { MensajeVacio(Mensajes.ABRA_CAJA); return@Column }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { modelo.busqueda.value = it },
                    placeholder = { Text("Buscar") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            BotonSecundario("Precargas", { modelo.panelAbierto = true }, Modifier.padding(horizontal = 12.dp))
            val l = lista
            when {
                l == null -> Unit
                l.isEmpty() && busqueda.isBlank() -> MensajeVacio(Mensajes.SIN_PRODUCTOS)
                l.isEmpty() -> MensajeVacio("No hay productos que coincidan")
                else -> LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
                    items(l, key = { it.id }) { p ->
                        FilaCarga(p, modelo.cantidades[p.id] ?: 0L, pasoKilo, alCambiar = { modelo.fijar(p.id, it) }, alMaximo = { modelo.maximo(p) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (modelo.panelAbierto) {
        ModalBottomSheet(onDismissRequest = { modelo.panelAbierto = false }) { PanelPrecargas(modelo, pasoKilo) }
    }
    modelo.precargaElegida?.let { p -> DialogoAdministrarPrecarga(p, modelo) }
}

/** Abajo de la pantalla; sube con el teclado (sin dejar una franja vacía). */
@Composable
private fun BarraGuardar(modelo: CargaViewModel, desdeApertura: Boolean, alOmitir: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = Modifier.imePadding()) {
        Column(Modifier.navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .combinedClickable(onClick = { modelo.guardarComoPrecarga = !modelo.guardarComoPrecarga }),
            ) {
                Checkbox(checked = modelo.guardarComoPrecarga, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                Text("Guardar también como precarga", style = MaterialTheme.typography.bodyLarge)
            }
            if (modelo.guardarComoPrecarga) {
                CampoTexto(modelo.nombrePrecarga, modelo::cambiarNombrePrecarga, "Nombre de la precarga *", error = modelo.errorNombre)
            }
            BotonPrincipal(
                "Guardar carga (${modelo.totalProductos} productos)", modelo::guardar, cargando = modelo.guardando,
            )
            if (desdeApertura) BotonSecundario("Omitir por hoy", alOmitir)
        }
    }
}

@Composable
private fun FilaCarga(p: ProductoParaCarga, milesimas: Long, pasoKilo: Long, alCambiar: (Long) -> Unit, alMaximo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = if (milesimas > 0) FontWeight.SemiBold else FontWeight.Normal)
            Text(
                "Hay ${Formato.cantidad(p.existenciaMilesimas, p.tipoVenta)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ControlCantidad(milesimas, p.tipoVenta, pasoKilo, p.existenciaMilesimas, p.nombre, alCambiar)
        TextButton(onClick = alMaximo, enabled = milesimas < p.existenciaMilesimas, modifier = Modifier.heightIn(min = 48.dp)) { Text("Máx") }
    }
}

/** Repetir última, N de cada producto, unidad para kilo y precargas guardadas (más usadas primero). */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PanelPrecargas(modelo: CargaViewModel, pasoKilo: Long) {
    val precargas by modelo.precargas.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Precargas", style = MaterialTheme.typography.titleLarge)
        BotonSecundario("Repetir última carga", modelo::repetirUltima)

        Text("N de cada producto", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ControlCantidad(
                milesimas = modelo.nDeCada * 1000, tipoVenta = TipoVenta.UNIDAD, pasoKilo = pasoKilo, maximo = 999_000,
                nombre = "N de cada producto", alCambiar = { modelo.nDeCada = it / 1000 },
            )
            TextButton(onClick = modelo::aplicarNDeCada, enabled = modelo.nDeCada > 0, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Aplicar")
            }
        }
        Text(
            "Por pieza: ${modelo.nDeCada} pzas. Por kilo: ${Formato.cantidad(modelo.nDeCada * pasoKilo, TipoVenta.PESO)}. Nunca más de lo que hay.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Unidad para productos por kilo", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReglaPrecarga.PASOS_KILO.forEach { g ->
                FilterChip(
                    selected = pasoKilo == g, onClick = { modelo.cambiarPasoKilo(g) },
                    label = { Text("$g g") }, modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        HorizontalDivider()
        Text("Guardadas", style = MaterialTheme.typography.titleMedium)
        if (precargas.isEmpty()) {
            Text(
                "Al guardar una carga, marque \"Guardar también como precarga\" para tenerla aquí.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        precargas.forEach { p ->
            val productos = ReglaPrecarga.decodificar(p.cantidades).size
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClickLabel = "Aplicar", onLongClickLabel = "Renombrar o quitar",
                            onLongClick = { modelo.precargaElegida = p }, onClick = { modelo.aplicarPrecarga(p) },
                        )
                        .padding(14.dp),
                ) {
                    Text(p.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$productos productos · usada ${p.vecesUsada} ${if (p.vecesUsada == 1) "vez" else "veces"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (precargas.isNotEmpty()) {
            Text(
                "Mantenga presionada una precarga para renombrarla o quitarla.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DialogoAdministrarPrecarga(p: Precarga, modelo: CargaViewModel) {
    var nombre by androidx.compose.runtime.remember(p.id) { mutableStateOf(p.nombre) }
    AlertDialog(
        onDismissRequest = { modelo.precargaElegida = null },
        title = { Text("Precarga") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoTexto(nombre, { nombre = it }, "Nombre")
                TextButton(onClick = { modelo.quitar(p) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Quitar precarga", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { modelo.renombrar(p, nombre) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Renombrar") } },
        dismissButton = { TextButton(onClick = { modelo.precargaElegida = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cerrar") } },
    )
}

/** Resumen de la carga en el corte: cargado, vendido, mermas y lo que sobra (regresa a casa). */
@Composable
fun SeccionCarga(renglones: List<RenglonCarga>) {
    if (renglones.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Carga del día", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth()) {
                listOf("Producto" to 2f, "Cargó" to 1f, "Vendió" to 1f, "Merma" to 1f, "Sobra" to 1f).forEach { (t, peso) ->
                    Text(
                        t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        textAlign = if (peso > 1f) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(peso),
                    )
                }
            }
            HorizontalDivider()
            renglones.forEach { r ->
                Row(Modifier.fillMaxWidth()) {
                    Text(r.nombre, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(2f))
                    listOf(r.cargadoMilesimas, r.vendidoMilesimas, r.mermasMilesimas, r.sobranteMilesimas).forEachIndexed { i, m ->
                        Text(
                            Formato.numeroCantidad(m, r.tipoVenta),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (i == 3) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Text(
                "Lo que sobra regresa a casa; su inventario no cambia por la carga.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
