package mx.ambulantpoint.misventas.ui.catalogo

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.domain.usecase.CambiarEstadoProducto
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.CrearCategoria
import mx.ambulantpoint.misventas.domain.usecase.EditarProducto
import mx.ambulantpoint.misventas.domain.usecase.FormularioProducto
import mx.ambulantpoint.misventas.domain.usecase.RegistrarProducto
import mx.ambulantpoint.misventas.ui.components.atender

/** Diálogo "Nueva categoría" dentro del formulario (RF 2.1). */
data class NuevaCategoria(val nombre: String = "", val error: String? = null)

/** RF 2.1 (productoId = null) y RF 2.2 / 2.3 / 2.4 (edición). */
class ProductoFormViewModel(c: AppContainer, private val productoId: String?) : ViewModel() {
    private val registrar = RegistrarProducto(c.productoRepo)
    private val editar = EditarProducto(c.productoRepo)
    private val cambiarEstado = CambiarEstadoProducto(c.productoRepo)
    private val crearCategoria = CrearCategoria(c.categoriaRepo)
    private val productos = c.productoRepo
    private val procesador = c.procesadorImagen
    private val notificador = c.notificador

    val esNuevo = productoId == null

    val categorias: StateFlow<List<Categoria>> = c.categoriaRepo.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var formulario by mutableStateOf(FormularioProducto())
        private set
    var errores by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var original by mutableStateOf<Producto?>(null)
        private set
    var guardando by mutableStateOf(false)
        private set
    var procesandoImagen by mutableStateOf(false)
        private set
    var nuevaCategoria by mutableStateOf<NuevaCategoria?>(null)
    var confirmarDesactivar by mutableStateOf(false)

    /** La pantalla se cierra cuando pasa a true. */
    var terminado by mutableStateOf(false)
        private set

    init {
        if (productoId != null) {
            viewModelScope.launch {
                productos.porId(productoId)?.let { p ->
                    original = p
                    formulario = FormularioProducto(
                        nombre = p.nombre,
                        categoriaId = p.categoriaId,
                        tipoVenta = p.tipoVenta,
                        precio = centavosATexto(p.precioCentavos),
                        imagenRuta = p.imagenRuta,
                    )
                }
            }
        }
    }

    /** Al corregir un campo se quita su alerta (RNF: validar en el momento de captura). */
    fun cambiar(nuevo: FormularioProducto) {
        val v = formulario
        formulario = nuevo
        val corregidos = buildSet {
            if (v.nombre != nuevo.nombre) add(Campos.NOMBRE)
            if (v.categoriaId != nuevo.categoriaId) add(Campos.CATEGORIA)
            if (v.tipoVenta != nuevo.tipoVenta) { add(Campos.TIPO_VENTA); add(Campos.EXISTENCIA) }
            if (v.precio != nuevo.precio) add(Campos.PRECIO)
            if (v.existencia != nuevo.existencia) add(Campos.EXISTENCIA)
        }
        if (corregidos.isNotEmpty()) errores = errores - corregidos
    }

    /** Comprime la foto elegida o tomada (máx. 1024 px y 300 KB). */
    fun imagen(uri: Uri) {
        viewModelScope.launch {
            procesandoImagen = true
            val ruta = procesador.procesar(uri)
            procesandoImagen = false
            if (ruta == null) {
                errores = errores + (Campos.IMAGEN to Mensajes.Campo.IMAGEN_NO_LEIDA)
            } else {
                formulario = formulario.copy(imagenRuta = ruta)
                errores = errores - Campos.IMAGEN
            }
        }
    }

    fun guardar() {
        if (guardando) return
        viewModelScope.launch {
            guardando = true
            val r = if (productoId == null) registrar(formulario) else editar(productoId, formulario)
            r.atender(notificador) { errores = it }?.let { hecho ->
                hecho.mensaje?.let(notificador::mostrar)
                terminado = true
            }
            guardando = false
        }
    }

    fun guardarCategoria() {
        val nc = nuevaCategoria ?: return
        viewModelScope.launch {
            crearCategoria(nc.nombre).atender(notificador) { e -> nuevaCategoria = nc.copy(error = e[Campos.NOMBRE]) }?.let { hecho ->
                hecho.mensaje?.let(notificador::mostrar)
                formulario = formulario.copy(categoriaId = hecho.valor.id)
                errores = errores - Campos.CATEGORIA
                nuevaCategoria = null
            }
        }
    }

    /** RF 2.3 (tras confirmar) / RF 2.4. */
    fun alternarEstado() {
        val p = original ?: return
        confirmarDesactivar = false
        viewModelScope.launch {
            cambiarEstado(p.id, activar = !p.activo).atender(notificador) {}?.let { hecho ->
                hecho.mensaje?.let(notificador::mostrar)
                terminado = true
            }
        }
    }

    private fun centavosATexto(c: Long) = "${c / 100}.${(c % 100).toString().padStart(2, '0')}"
}
