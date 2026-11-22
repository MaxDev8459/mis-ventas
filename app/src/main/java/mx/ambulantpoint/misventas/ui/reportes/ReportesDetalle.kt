package mx.ambulantpoint.misventas.ui.reportes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.dao.ProductoVendido
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.domain.export.ExportarReportes
import mx.ambulantpoint.misventas.domain.regla.NivelExistencia
import mx.ambulantpoint.misventas.domain.regla.OrdenMasVendidos
import mx.ambulantpoint.misventas.domain.regla.Periodo
import mx.ambulantpoint.misventas.domain.usecase.FilaInventario
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteCategorias
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteFormasPago
import mx.ambulantpoint.misventas.domain.usecase.GenerarReporteMasVendidos
import mx.ambulantpoint.misventas.domain.usecase.ReporteInventario
import mx.ambulantpoint.misventas.domain.usecase.ReporteMasVendidos
import mx.ambulantpoint.misventas.domain.usecase.ReportePorcentajes
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.vm
import mx.ambulantpoint.misventas.ui.inventario.FondoAgotado
import mx.ambulantpoint.misventas.ui.inventario.FondoBaja
import mx.ambulantpoint.misventas.ui.inventario.TextoAgotado
import mx.ambulantpoint.misventas.ui.inventario.TextoBaja
import mx.ambulantpoint.misventas.ui.inventario.coloresNombre

// ───────────────────────── RF 6.2 / 6.4 ─────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class ReportePorcentajesViewModel(c: AppContainer, porCategoria: Boolean) : ViewModel() {
    private val generar: suspend (Periodo) -> ReportePorcentajes =
        if (porCategoria) GenerarReporteCategorias(c.reporteRepo)::invoke else GenerarReporteFormasPago(c.reporteRepo)::invoke
    val periodo = MutableStateFlow(periodoInicial())

    val reporte: StateFlow<ReportePorcentajes?> = periodo
        .mapLatest { generar(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cambiar(p: Periodo) { periodo.value = p }
}

/** RF 6.2 (por forma de pago) y RF 6.4 (por categoría): importe y porcentaje del total. */
@Composable
fun PantallaReportePorcentajes(reporte: Reporte, alRegresar: () -> Unit) {
    val porCategoria = reporte == Reporte.POR_CATEGORIA
    val modelo = vm(key = reporte.name) { ReportePorcentajesViewModel(it, porCategoria) }
    val periodo by modelo.periodo.collectAsStateWithLifecycle()
    val r by modelo.reporte.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        BarraSuperior(reporte.titulo, alRegresar) {
            AccionExportar(r?.takeIf { it.periodo == periodo }?.let { ExportarReportes.porcentajes(it, reporte.titulo, porCategoria) })
        }
    }) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            SelectorPeriodo(periodo, modelo::cambiar)
            val actual = r
            when {
                actual == null || actual.periodo != periodo -> Unit
                actual.sinVentas -> MensajeVacio(Mensajes.SIN_VENTAS_PERIODO)
                else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
                    item { TarjetaTotal("Total vendido", Formato.moneda(actual.totalCentavos)) }
                    item {
                        Spacer(Modifier.padding(top = 8.dp))
                        if (porCategoria) Columnas(listOf("Categoría", "Importe", "%"), PESOS_3, encabezado = true)
                        else Columnas(listOf("Forma de pago", "Ventas", "Importe", "%"), PESOS_4, encabezado = true)
                        HorizontalDivider()
                    }
                    items(actual.filas) { f ->
                        val pct = Formato.porcentaje(f.porcentajeDecimas)
                        if (porCategoria) Columnas(listOf(f.nombre, Formato.moneda(f.importeCentavos), pct), PESOS_3)
                        else Columnas(listOf(f.nombre, f.numeroVentas.toString(), Formato.moneda(f.importeCentavos), pct), PESOS_4)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private val PESOS_3 = listOf(1.5f, 1f, 0.6f)
private val PESOS_4 = listOf(1.3f, 0.6f, 1f, 0.6f)

// ───────────────────────── RF 6.3 ─────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class ReporteMasVendidosViewModel(c: AppContainer) : ViewModel() {
    private val generar = GenerarReporteMasVendidos(c.reporteRepo)
    val periodo = MutableStateFlow(periodoInicial())
    val orden = MutableStateFlow(OrdenMasVendidos.CANTIDAD)

    val reporte: StateFlow<ReporteMasVendidos?> = combine(periodo, orden) { p, o -> p to o }
        .mapLatest { (p, o) -> generar(p, o) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cambiar(p: Periodo) { periodo.value = p }
    fun ordenar(o: OrdenMasVendidos) { orden.value = o }
}

/** RF 6.3 — Productos más vendidos. */
@Composable
fun PantallaReporteMasVendidos(alRegresar: () -> Unit) {
    val modelo = vm { ReporteMasVendidosViewModel(it) }
    val periodo by modelo.periodo.collectAsStateWithLifecycle()
    val orden by modelo.orden.collectAsStateWithLifecycle()
    val r by modelo.reporte.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        BarraSuperior(Reporte.MAS_VENDIDOS.titulo, alRegresar) {
            AccionExportar(
                r?.takeIf { it.periodo == periodo && it.orden == orden }?.let { ExportarReportes.masVendidos(it, Reporte.MAS_VENDIDOS.titulo) },
            )
        }
    }) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            SelectorPeriodo(periodo, modelo::cambiar)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ordenar por:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OrdenMasVendidos.entries.forEach { o ->
                    FilterChip(orden == o, { modelo.ordenar(o) }, { Text(o.etiqueta) }, Modifier.heightIn(min = 48.dp))
                }
            }
            val actual = r
            when {
                actual == null || actual.periodo != periodo || actual.orden != orden -> Unit
                actual.sinVentas -> MensajeVacio(Mensajes.SIN_VENTAS_PERIODO)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(actual.filas, key = { _, f -> f.productoId }) { i, f ->
                        FilaMasVendido(i + 1, f)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaMasVendido(lugar: Int, f: ProductoVendido) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$lugar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(f.nombre, style = MaterialTheme.typography.bodyLarge)
            Text(f.categoriaNombre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Formato.cantidad(f.cantidadMilesimas, f.tipoVenta), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(Formato.moneda(f.importeCentavos), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ───────────────────────── RF 6.5 ─────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class ReporteInventarioViewModel(c: AppContainer) : ViewModel() {
    /** Filtro por categoría (opcional); null = todas. */
    val categoriaId = MutableStateFlow<String?>(null)

    val reporte: StateFlow<ReporteInventario?> = categoriaId.flatMapLatest { cat ->
        combine(c.productoRepo.observar("", cat, activo = true), c.reporteRepo.observarVentasRecientesDeTodos()) { productos, recientes ->
            ReporteInventario.armar(productos, recientes)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categorias: StateFlow<List<Categoria>> = c.categoriaRepo.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun categoria(id: String?) { categoriaId.value = id }
}

/** RF 6.5 — Reporte de inventario: existencia al momento de consultarlo, sin periodo. */
@Composable
fun PantallaReporteInventario(alRegresar: () -> Unit) {
    val modelo = vm { ReporteInventarioViewModel(it) }
    val categoriaId by modelo.categoriaId.collectAsStateWithLifecycle()
    val categorias by modelo.categorias.collectAsStateWithLifecycle()
    val r by modelo.reporte.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        BarraSuperior(Reporte.INVENTARIO.titulo, alRegresar) {
            val categoria = categorias.firstOrNull { it.id == categoriaId }?.nombre
            AccionExportar(r?.let { ExportarReportes.inventario(it, Reporte.INVENTARIO.titulo, categoria) })
        }
    }) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                FilterChip(
                    categoriaId != null, { menu = true },
                    { Text(categorias.firstOrNull { it.id == categoriaId }?.nombre ?: "Todas las categorías") },
                    Modifier.heightIn(min = 48.dp),
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Todas las categorías") }, { modelo.categoria(null); menu = false })
                    categorias.forEach { cat -> DropdownMenuItem({ Text(cat.nombre) }, { modelo.categoria(cat.id); menu = false }) }
                }
            }
            val actual = r
            when {
                actual == null -> Unit
                actual.filas.isEmpty() -> MensajeVacio(Mensajes.SIN_PRODUCTOS)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Contador("Activos", actual.activos, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f))
                            Contador("Existencia baja", actual.conExistenciaBaja, FondoBaja, TextoBaja, Modifier.weight(1f))
                            Contador("Agotados", actual.agotados, FondoAgotado, TextoAgotado, Modifier.weight(1f))
                        }
                    }
                    items(actual.filas, key = { it.producto.producto.id }) { f ->
                        FilaInventarioVista(f)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun Contador(etiqueta: String, valor: Int, fondo: Color, texto: Color, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = fondo), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(valor.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = texto)
            Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = texto)
        }
    }
}

@Composable
private fun FilaInventarioVista(f: FilaInventario) {
    val p = f.producto.producto
    val (fondo, texto, etiqueta) = when (f.nivel) {
        NivelExistencia.AGOTADO -> Triple(FondoAgotado, TextoAgotado, "Agotado")
        NivelExistencia.BAJA -> Triple(FondoBaja, TextoBaja, "Existencia baja")
        NivelExistencia.NORMAL -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurface, null)
    }
    Row(
        Modifier.fillMaxWidth().background(fondo).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (colorNombre, colorCategoria) = coloresNombre(f.nivel)
        Column(Modifier.weight(1f)) {
            Text(p.nombre, style = MaterialTheme.typography.bodyLarge, color = colorNombre)
            Text(f.producto.categoriaNombre, style = MaterialTheme.typography.bodyMedium, color = colorCategoria)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Formato.cantidad(p.existenciaMilesimas, p.tipoVenta), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = texto)
            if (etiqueta != null) Text(etiqueta, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = texto)
        }
    }
}

// ───────────────────────── Comunes ─────────────────────────

@Composable
internal fun TarjetaTotal(etiqueta: String, valor: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(etiqueta, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(valor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** Fila de tabla: la primera columna a la izquierda, las demás alineadas a la derecha. */
@Composable
internal fun Columnas(textos: List<String>, pesos: List<Float>, encabezado: Boolean = false) {
    val estilo = if (encabezado) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge
    val color = if (encabezado) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        textos.forEachIndexed { i, t ->
            Text(
                t, style = estilo, color = color,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(pesos[i]),
            )
        }
    }
}
