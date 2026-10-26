package mx.ambulantpoint.misventas.ui.categorias

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.CrearCategoria
import mx.ambulantpoint.misventas.domain.usecase.EliminarCategoria
import mx.ambulantpoint.misventas.domain.usecase.RenombrarCategoria
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.ui.components.DialogoConfirmacion
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.atender
import mx.ambulantpoint.misventas.ui.components.vm

/** Diálogo abierto: crear (categoria = null) o renombrar. */
data class EdicionCategoria(val categoria: Categoria?, val nombre: String, val error: String? = null)

class CategoriasViewModel(c: AppContainer) : ViewModel() {
    private val crear = CrearCategoria(c.categoriaRepo)
    private val renombrar = RenombrarCategoria(c.categoriaRepo)
    private val eliminar = EliminarCategoria(c.categoriaRepo)
    private val notificador = c.notificador
    private val repo = c.categoriaRepo

    val categorias: StateFlow<List<Categoria>?> = c.categoriaRepo.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var edicion by mutableStateOf<EdicionCategoria?>(null)
    var porEliminar by mutableStateOf<Categoria?>(null)

    fun nueva() { edicion = EdicionCategoria(null, "") }
    fun editar(categoria: Categoria) { edicion = EdicionCategoria(categoria, categoria.nombre) }

    fun guardar() {
        val e = edicion ?: return
        viewModelScope.launch {
            val r = if (e.categoria == null) crear(e.nombre) else renombrar(e.categoria.id, e.nombre)
            r.atender(notificador) { errores -> edicion = edicion?.copy(error = errores[Campos.NOMBRE]) }?.let {
                it.mensaje?.let(notificador::mostrar)
                edicion = null
            }
        }
    }

    /** RF 2.9: si tiene productos, se avisa sin pedir confirmación. */
    fun pedirEliminar(categoria: Categoria) {
        viewModelScope.launch {
            edicion = null
            if (repo.contarProductos(categoria.id) > 0) {
                notificador.mostrar(Mensajes.CATEGORIA_CON_PRODUCTOS)
            } else {
                porEliminar = categoria
            }
        }
    }

    fun confirmarEliminar() {
        val cat = porEliminar ?: return
        porEliminar = null
        viewModelScope.launch {
            eliminar(cat.id).atender(notificador) {}?.mensaje?.let(notificador::mostrar)
        }
    }
}

/** RF 2.6 – 2.9 — Categorías. */
@Composable
fun PantallaCategorias(alRegresar: () -> Unit) {
    val modelo = vm { CategoriasViewModel(it) }
    val categorias by modelo.categorias.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BarraSuperior("Categorías", alRegresar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = modelo::nueva,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nueva categoría") },
            )
        },
    ) { relleno ->
        val lista = categorias
        when {
            lista == null -> Unit
            lista.isEmpty() -> MensajeVacio(Mensajes.SIN_CATEGORIAS, Modifier.padding(relleno))
            else -> LazyColumn(Modifier.padding(relleno).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(lista, key = { it.id }) { cat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { modelo.editar(cat) }
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(cat.nombre, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { modelo.pedirEliminar(cat) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar ${cat.nombre}")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    modelo.edicion?.let { e ->
        AlertDialog(
            onDismissRequest = { modelo.edicion = null },
            title = { Text(if (e.categoria == null) "Nueva categoría" else "Modificar categoría") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoTexto(
                        e.nombre, { modelo.edicion = e.copy(nombre = it, error = null) }, "Nombre de la categoría *",
                        error = e.error, imeAction = ImeAction.Done,
                    )
                }
            },
            confirmButton = { TextButton(onClick = modelo::guardar) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { modelo.edicion = null }) { Text("Cancelar") } },
        )
    }

    modelo.porEliminar?.let { cat ->
        DialogoConfirmacion(
            texto = Mensajes.confirmarEliminarCategoria(cat.nombre),
            alConfirmar = modelo::confirmarEliminar,
            alCancelar = { modelo.porEliminar = null },
        )
    }
}
