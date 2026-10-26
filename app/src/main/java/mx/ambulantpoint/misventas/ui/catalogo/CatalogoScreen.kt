package mx.ambulantpoint.misventas.ui.catalogo

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.dao.ProductoConCategoria
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.vm
import java.io.File

/** RF 2.5: por defecto solo activos, ordenados alfabéticamente. */
data class FiltroCatalogo(val texto: String = "", val categoriaId: String? = null, val activos: Boolean = true)

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogoViewModel(c: AppContainer) : ViewModel() {
    val filtro = MutableStateFlow(FiltroCatalogo())

    val productos: StateFlow<List<ProductoConCategoria>?> = filtro
        .flatMapLatest { c.productoRepo.observar(it.texto, it.categoriaId, it.activos) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categorias: StateFlow<List<Categoria>> = c.categoriaRepo.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun buscar(texto: String) = filtro.update { it.copy(texto = texto) }
    fun categoria(id: String?) = filtro.update { it.copy(categoriaId = id) }
    fun activos(valor: Boolean) = filtro.update { it.copy(activos = valor) }
}

/** RF 2.5 — Consultar productos. */
@Composable
fun PantallaCatalogo(alRegresar: () -> Unit, alNuevo: () -> Unit, alEditar: (String) -> Unit) {
    val modelo = vm { CatalogoViewModel(it) }
    val filtro by modelo.filtro.collectAsStateWithLifecycle()
    val productos by modelo.productos.collectAsStateWithLifecycle()
    val categorias by modelo.categorias.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BarraSuperior("Catálogo", alRegresar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = alNuevo,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuevo producto") },
            )
        },
    ) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            OutlinedTextField(
                value = filtro.texto,
                onValueChange = modelo::buscar,
                placeholder = { Text("Buscar por nombre") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FiltrosCatalogo(filtro, categorias, modelo::categoria, modelo::activos)

            val lista = productos
            when {
                lista == null -> Unit
                lista.isEmpty() -> MensajeVacio(Mensajes.SIN_PRODUCTOS)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(lista, key = { it.producto.id }) { p ->
                        FilaProducto(p) { alEditar(p.producto.id) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltrosCatalogo(
    filtro: FiltroCatalogo,
    categorias: List<Categoria>,
    alCategoria: (String?) -> Unit,
    alActivos: (Boolean) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = filtro.activos, onClick = { alActivos(true) }, label = { Text("Activos") }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(selected = !filtro.activos, onClick = { alActivos(false) }, label = { Text("Inactivos") }, modifier = Modifier.heightIn(min = 48.dp))
        Box {
            val nombre = categorias.firstOrNull { it.id == filtro.categoriaId }?.nombre ?: "Todas las categorías"
            FilterChip(
                selected = filtro.categoriaId != null,
                onClick = { abierto = true },
                label = { Text(nombre) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                DropdownMenuItem(text = { Text("Todas las categorías") }, onClick = { alCategoria(null); abierto = false })
                categorias.forEach { cat ->
                    DropdownMenuItem(text = { Text(cat.nombre) }, onClick = { alCategoria(cat.id); abierto = false })
                }
            }
        }
    }
}

/** Imagen, nombre, categoría, precio y existencia con su unidad (RF 2.5). */
@Composable
private fun FilaProducto(p: ProductoConCategoria, alPulsar: () -> Unit) {
    val producto = p.producto
    Row(
        Modifier.fillMaxWidth().clickable(onClick = alPulsar).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ImagenProducto(producto.imagenRuta, Modifier.size(64.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(producto.nombre, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.categoriaNombre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${Formato.moneda(producto.precioCentavos)} / ${unidadSingular(producto.tipoVenta)} · " +
                    Formato.cantidad(producto.existenciaMilesimas, producto.tipoVenta),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

internal fun unidadSingular(tipo: mx.ambulantpoint.misventas.data.local.entity.TipoVenta) =
    if (tipo == mx.ambulantpoint.misventas.data.local.entity.TipoVenta.UNIDAD) "pza" else "kg"

@Composable
fun ImagenProducto(ruta: String?, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(8.dp)
    if (ruta.isNullOrBlank()) {
        Box(modifier.clip(forma).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("Sin imagen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        AsyncImage(
            model = File(ruta),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(forma).background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
