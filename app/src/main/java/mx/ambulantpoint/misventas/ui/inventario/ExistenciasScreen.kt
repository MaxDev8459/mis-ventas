package mx.ambulantpoint.misventas.ui.inventario

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.dao.ProductoConCategoria
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.domain.regla.NivelExistencia
import mx.ambulantpoint.misventas.domain.regla.ReglaExistenciaBaja
import mx.ambulantpoint.misventas.ui.catalogo.ImagenProducto
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.vm

/** `apartadoMilesimas`: lo separado en apartados pendientes (el nivel se calcula con lo disponible). */
data class FilaExistencia(val p: ProductoConCategoria, val nivel: NivelExistencia, val apartadoMilesimas: Long = 0)

data class FiltroExistencias(val texto: String = "", val categoriaId: String? = null, val soloBaja: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class ExistenciasViewModel(c: AppContainer) : ViewModel() {
    val filtro = MutableStateFlow(FiltroExistencias())

    /** RF 4.3: productos activos con su nivel de existencia (RF 4.5). */
    val filas: StateFlow<List<FilaExistencia>?> = filtro.flatMapLatest { f ->
        combine(
            c.productoRepo.observar(f.texto, f.categoriaId, activo = true),
            c.reporteRepo.observarVentasRecientesDeTodos(),
            c.apartadoRepo.observarApartadoPorProducto(),
        ) { lista, recientes, apartado ->
            lista.map { p ->
                val r = recientes[p.producto.id]
                val ap = apartado[p.producto.id] ?: 0L
                val disponible = mx.ambulantpoint.misventas.domain.regla.ReglaApartado.disponible(p.producto.existenciaMilesimas, ap)
                FilaExistencia(p, ReglaExistenciaBaja.nivel(disponible, p.producto.tipoVenta, r?.dias ?: 0, r?.cantidadMilesimas ?: 0), ap)
            }.filter { !f.soloBaja || it.nivel != NivelExistencia.NORMAL }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categorias: StateFlow<List<Categoria>> = c.categoriaRepo.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun buscar(t: String) = filtro.update { it.copy(texto = t) }
    fun categoria(id: String?) = filtro.update { it.copy(categoriaId = id) }
    fun soloBaja(v: Boolean) = filtro.update { it.copy(soloBaja = v) }
}

/** Colores de resaltado (RF 4.3: "se distinguirán visualmente del resto"). */
internal val FondoBaja = Color(0xFFFFF1D6)
internal val TextoBaja = Color(0xFF7A4F00)
internal val FondoAgotado = Color(0xFFFFE1DE)
internal val TextoAgotado = Color(0xFF93000A)

/**
 * Nombre y categoría sobre los fondos pastel de baja/agotado: siempre oscuros, también en modo
 * oscuro (ahí el texto normal es claro y no se leía sobre el pastel).
 */
internal val TextoSobrePastel = Color(0xFF1A1C19)
internal val TextoSecundarioSobrePastel = Color(0xFF43483F)

/** Colores del nombre y la categoría según el nivel: oscuros sobre pastel, los del tema en lo normal. */
@Composable
internal fun coloresNombre(nivel: NivelExistencia): Pair<Color, Color> =
    if (nivel == NivelExistencia.NORMAL) {
        MaterialTheme.colorScheme.onSurface to MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        TextoSobrePastel to TextoSecundarioSobrePastel
    }

/** RF 4.3 — Consultar existencias. */
@Composable
fun PantallaExistencias(alRegresar: () -> Unit, alAbrir: (String) -> Unit) {
    val modelo = vm { ExistenciasViewModel(it) }
    val filtro by modelo.filtro.collectAsStateWithLifecycle()
    val filas by modelo.filas.collectAsStateWithLifecycle()
    val categorias by modelo.categorias.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }

    Scaffold(topBar = { BarraSuperior("Existencias", alRegresar) }) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            OutlinedTextField(
                value = filtro.texto, onValueChange = modelo::buscar,
                placeholder = { Text("Buscar por nombre") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(filtro.soloBaja, { modelo.soloBaja(!filtro.soloBaja) }, { Text("Solo existencia baja") }, Modifier.heightIn(min = 48.dp))
                Box {
                    FilterChip(
                        filtro.categoriaId != null, { menu = true },
                        { Text(categorias.firstOrNull { it.id == filtro.categoriaId }?.nombre ?: "Todas las categorías") },
                        Modifier.heightIn(min = 48.dp),
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    )
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Todas las categorías") }, { modelo.categoria(null); menu = false })
                        categorias.forEach { cat -> DropdownMenuItem({ Text(cat.nombre) }, { modelo.categoria(cat.id); menu = false }) }
                    }
                }
            }
            val lista = filas
            when {
                lista == null -> Unit
                lista.isEmpty() -> MensajeVacio(Mensajes.SIN_PRODUCTOS)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(lista, key = { it.p.producto.id }) { f ->
                        FilaExistenciaVista(f) { alAbrir(f.p.producto.id) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Imagen, nombre, categoría, existencia y unidad; baja y agotado resaltados. */
@Composable
private fun FilaExistenciaVista(f: FilaExistencia, alPulsar: () -> Unit) {
    val p = f.p.producto
    val (fondo, texto, etiqueta) = when (f.nivel) {
        NivelExistencia.AGOTADO -> Triple(FondoAgotado, TextoAgotado, "Agotado")
        NivelExistencia.BAJA -> Triple(FondoBaja, TextoBaja, "Existencia baja")
        NivelExistencia.NORMAL -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurface, null)
    }
    Row(
        Modifier.fillMaxWidth().background(fondo).clickable(onClick = alPulsar).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ImagenProducto(p.imagenRuta, Modifier.size(56.dp))
        Spacer(Modifier.width(12.dp))
        val (colorNombre, colorCategoria) = coloresNombre(f.nivel)
        Column(Modifier.weight(1f)) {
            Text(p.nombre, style = MaterialTheme.typography.bodyLarge, color = colorNombre)
            Text(f.p.categoriaNombre, style = MaterialTheme.typography.bodyMedium, color = colorCategoria)
            if (f.apartadoMilesimas > 0) {
                Text(
                    "Apartado: ${Formato.cantidad(f.apartadoMilesimas, p.tipoVenta)}",
                    style = MaterialTheme.typography.bodyMedium, color = colorCategoria, fontWeight = FontWeight.SemiBold,
                )
            }
            if (etiqueta != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = texto, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(etiqueta, color = texto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            Formato.cantidad(p.existenciaMilesimas, p.tipoVenta),
            style = MaterialTheme.typography.titleLarge,
            color = texto,
        )
    }
}
