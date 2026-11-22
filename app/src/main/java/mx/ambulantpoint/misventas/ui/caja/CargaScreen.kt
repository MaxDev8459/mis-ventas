package mx.ambulantpoint.misventas.ui.caja

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.regla.ProductoParaCarga
import mx.ambulantpoint.misventas.domain.regla.ReglaCarga
import mx.ambulantpoint.misventas.domain.regla.RenglonCarga
import mx.ambulantpoint.misventas.domain.venta.ReglaFiltroVenta
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonPrincipal
import mx.ambulantpoint.misventas.ui.components.BotonSecundario
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.vm

/** Carga del día: cuánto lleva el vendedor de cada producto en la caja abierta. */
class CargaViewModel(c: AppContainer) : ViewModel() {
    private val cargaRepo = c.cargaRepo
    private val cajaRepo = c.cajaRepo
    private val notificador = c.notificador

    val busqueda = MutableStateFlow("")

    /** Productos activos con existencia; el límite de cada uno es lo que había al abrir la caja. */
    private val productos = MutableStateFlow<List<ProductoParaCarga>?>(null)

    val visibles: StateFlow<List<ProductoParaCarga>?> = combine(productos, busqueda) { lista, texto ->
        val t = ReglaFiltroVenta.normalizar(texto)
        lista?.filter { t.isEmpty() || ReglaFiltroVenta.normalizar(it.nombre).contains(t) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Texto capturado por producto. */
    var capturas by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var errores by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var guardando by mutableStateOf(false)
        private set
    var terminado by mutableStateOf(false)
        private set
    var sinCaja by mutableStateOf(false)
        private set

    private var cajaId: String? = null

    init {
        viewModelScope.launch {
            val caja = cajaRepo.abierta()
            if (caja == null) { sinCaja = true; productos.value = emptyList(); return@launch }
            cajaId = caja.id
            val lista = c.productoRepo.observarParaVenta().first().map { it.producto }
            val limites = cargaRepo.disponibles(caja.id, caja.abiertaEn, lista.associate { it.id to it.existenciaMilesimas })
            val actual = cargaRepo.cargaActual(caja.id)
            productos.value = lista.map { ProductoParaCarga(it.id, it.nombre, it.tipoVenta, limites[it.id] ?: it.existenciaMilesimas) }
            capturas = lista.associate { it.id to ReglaCarga.textoCaptura(actual[it.id] ?: 0, it.tipoVenta) }
        }
    }

    fun cambiar(productoId: String, texto: String) {
        capturas = capturas + (productoId to texto)
        errores = errores - productoId
    }

    /** Llena con la carga de la última caja que tuvo carga (sin pasar de lo que hay hoy). */
    fun usarAnterior() {
        val id = cajaId ?: return
        viewModelScope.launch {
            val anterior = cargaRepo.cargaAnterior(id)
            if (anterior.isEmpty()) { notificador.mostrar("No hay una carga anterior"); return@launch }
            val lista = productos.value.orEmpty()
            capturas = lista.associate { p -> p.id to ReglaCarga.textoCaptura(minOf(anterior[p.id] ?: 0, p.existenciaMilesimas), p.tipoVenta) }
            errores = emptyMap()
            notificador.mostrar("Se cargó la carga anterior. Revise y guarde")
        }
    }

    fun guardar() {
        val id = cajaId ?: return
        if (guardando) return
        val (cantidades, errs) = ReglaCarga.leerCaptura(capturas, productos.value.orEmpty())
        if (errs.isNotEmpty()) { errores = errs; notificador.mostrar("Revise las cantidades marcadas"); return }
        viewModelScope.launch {
            guardando = true
            when (val r = cargaRepo.guardar(id, cantidades)) {
                is Resultado.Exito -> { notificador.mostrar("La carga del día se guardó"); terminado = true }
                is Resultado.Fallo -> notificador.mostrar(
                    (r.falla as? mx.ambulantpoint.misventas.data.repository.FallaDatos.CargaExcedeExistencia)?.mensaje
                        ?: Mensajes.ABRA_CAJA,
                )
            }
            guardando = false
        }
    }
}

/**
 * Carga del día. Al abrir caja se llega aquí con [desdeApertura] = true y se puede omitir. Lo que se
 * deja vacío no se carga.
 */
@Composable
fun PantallaCarga(desdeApertura: Boolean, alTerminar: () -> Unit) {
    val modelo = vm { CargaViewModel(it) }
    val lista by modelo.visibles.collectAsStateWithLifecycle()
    val busqueda by modelo.busqueda.collectAsStateWithLifecycle()
    LaunchedEffect(modelo.terminado) { if (modelo.terminado) alTerminar() }

    Scaffold(
        topBar = { BarraSuperior("Carga del día", alTerminar) },
        bottomBar = {
            if (!modelo.sinCaja) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Column(Modifier.navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonPrincipal("Guardar carga", modelo::guardar, cargando = modelo.guardando)
                        if (desdeApertura) BotonSecundario("Omitir por hoy", alTerminar)
                    }
                }
            }
        },
    ) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize().imePadding()) {
            if (modelo.sinCaja) { MensajeVacio(Mensajes.ABRA_CAJA); return@Column }
            Text(
                "Capture cuánto lleva de cada producto. Lo que deje vacío no se carga. No cambia su inventario.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { modelo.busqueda.value = it },
                    placeholder = { Text("Buscar") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            BotonSecundario("Usar carga anterior", modelo::usarAnterior, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            val l = lista
            when {
                l == null -> Unit
                l.isEmpty() && busqueda.isBlank() -> MensajeVacio(Mensajes.SIN_PRODUCTOS)
                l.isEmpty() -> MensajeVacio("No hay productos que coincidan")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(l, key = { it.id }) { p ->
                        FilaCarga(p, modelo.capturas[p.id].orEmpty(), modelo.errores[p.id]) { modelo.cambiar(p.id, it) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaCarga(p: ProductoParaCarga, texto: String, error: String?, alCambiar: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.nombre, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Hay ${Formato.cantidad(p.existenciaMilesimas, p.tipoVenta)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = texto,
                onValueChange = alCambiar,
                placeholder = { Text("0") },
                suffix = { Text(Formato.unidad(p.tipoVenta)) },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (p.tipoVenta == TipoVenta.PESO) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.width(140.dp),
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
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
