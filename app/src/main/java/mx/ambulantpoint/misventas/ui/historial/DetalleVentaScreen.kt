package mx.ambulantpoint.misventas.ui.historial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.data.local.entity.EstadoVenta
import mx.ambulantpoint.misventas.data.local.entity.Venta
import mx.ambulantpoint.misventas.data.local.entity.VentaDetalle
import mx.ambulantpoint.misventas.domain.usecase.CancelarVenta
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.domain.venta.LineaACorregir
import mx.ambulantpoint.misventas.domain.venta.ProductoEnVenta
import mx.ambulantpoint.misventas.domain.venta.ReglaCorreccion
import androidx.compose.runtime.LaunchedEffect
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonSecundario
import mx.ambulantpoint.misventas.ui.components.DialogoConfirmacion
import mx.ambulantpoint.misventas.ui.components.atender
import mx.ambulantpoint.misventas.ui.components.vm

class DetalleVentaViewModel(c: AppContainer, private val ventaId: String) : ViewModel() {
    private val repo = c.ventaRepo
    private val formas = c.formaPagoRepo
    private val cancelarVenta = CancelarVenta(c.ventaRepo)
    private val notificador = c.notificador
    private val productos = c.productoRepo
    private val carritoPorCorregir = c.carritoPorCorregir

    val detalles: StateFlow<List<VentaDetalle>> = repo.observarDetalles(ventaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var venta by mutableStateOf<Venta?>(null)
        private set
    var formaPago by mutableStateOf("")
        private set
    /** RF 3.3: solo ventas registradas de la caja abierta. */
    var cancelable by mutableStateOf(false)
        private set
    var confirmando by mutableStateOf(false)
    var confirmandoCorreccion by mutableStateOf(false)
    /** La venta se canceló para corregirla: la pantalla abre la venta con sus productos. */
    var corregida by mutableStateOf(false)
        private set

    init { cargar() }

    private fun cargar() {
        viewModelScope.launch {
            val v = repo.porId(ventaId) ?: return@launch
            venta = v
            formaPago = formas.observarTodas().first().firstOrNull { it.id == v.formaPagoId }?.nombre.orEmpty()
            cancelable = repo.esCancelable(ventaId)
        }
    }

    fun textoConfirmacion(): String = venta?.let { cancelarVenta.confirmacion(it) }.orEmpty()

    fun textoCorreccion(): String =
        venta?.let { Mensajes.confirmarCorregirVenta(Formato.moneda(it.totalCentavos), it.descuentoCentavos > 0) }.orEmpty()

    /** Corregir venta = cancelar (la existencia regresa) y cargar sus productos en una venta nueva. */
    fun corregir() {
        confirmandoCorreccion = false
        viewModelScope.launch {
            val lineas = repo.observarDetalles(ventaId).first()
                .map { LineaACorregir(it.productoId, it.nombreProducto, it.cantidadMilesimas) }
            cancelarVenta(ventaId).atender(notificador) {} ?: return@launch
            val hoy = lineas.map { it.productoId }.distinct().associateWith { productos.porId(it) }
            val correccion = ReglaCorreccion.armar(lineas) { id ->
                hoy[id]?.takeIf { it.activo }?.let { p ->
                    ProductoEnVenta(p.id, p.nombre, p.tipoVenta, p.precioCentavos, p.existenciaMilesimas)
                }
            }
            carritoPorCorregir.value = correccion.carrito
            notificador.mostrar(
                listOfNotNull(
                    Mensajes.VENTA_EN_CORRECCION,
                    correccion.omitidos.takeIf { it.isNotEmpty() }?.let(Mensajes::productosNoRecargados),
                ).joinToString("\n"),
            )
            corregida = true
        }
    }

    fun cancelar() {
        confirmando = false
        viewModelScope.launch {
            cancelarVenta(ventaId).atender(notificador) {}?.let { hecho ->
                hecho.mensaje?.let(notificador::mostrar)
                cargar()
            }
        }
    }
}

/** RF 3.4 (detalle), RF 3.3 Cancelar venta y Corregir venta (cancelar y recapturar). */
@Composable
fun PantallaDetalleVenta(ventaId: String, alRegresar: () -> Unit, alCorregir: () -> Unit) {
    val modelo = vm(key = ventaId) { DetalleVentaViewModel(it, ventaId) }
    LaunchedEffect(modelo.corregida) { if (modelo.corregida) alCorregir() }
    val detalles by modelo.detalles.collectAsStateWithLifecycle()
    val v = modelo.venta

    Scaffold(topBar = { BarraSuperior("Detalle de venta", alRegresar) }) { relleno ->
        Column(
            Modifier.padding(relleno).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (v == null) return@Column
            Text(Formato.fechaHora(v.fechaHora), style = MaterialTheme.typography.titleLarge)
            Text(
                "Estado: ${etiquetaEstado(v.estado)}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (v.estado == EstadoVenta.CANCELADA) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider()
            detalles.forEach { d ->
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
            Renglon("Subtotal", Formato.moneda(v.subtotalCentavos))
            Renglon("Descuento", Formato.moneda(v.descuentoCentavos))
            Renglon("Total", Formato.moneda(v.totalCentavos), fuerte = true)
            Renglon("Forma de pago", modelo.formaPago)
            v.montoRecibidoCentavos?.let { Renglon("Monto recibido", Formato.moneda(it)) }
            v.cambioCentavos?.let { Renglon("Cambio", Formato.moneda(it)) }

            if (modelo.cancelable) {
                Spacer(Modifier.height(12.dp))
                BotonSecundario("Corregir venta", { modelo.confirmandoCorreccion = true })
                BotonSecundario("Cancelar venta", { modelo.confirmando = true })
            }
        }
    }

    if (modelo.confirmandoCorreccion) {
        DialogoConfirmacion(
            texto = modelo.textoCorreccion(),
            alConfirmar = modelo::corregir,
            alCancelar = { modelo.confirmandoCorreccion = false },
            textoConfirmar = "Corregir",
            textoCancelar = "Regresar",
        )
    }

    if (modelo.confirmando) {
        DialogoConfirmacion(
            texto = modelo.textoConfirmacion(),
            alConfirmar = modelo::cancelar,
            alCancelar = { modelo.confirmando = false },
            textoConfirmar = "Aceptar",
            textoCancelar = "Regresar",
        )
    }
}

@Composable
private fun Renglon(etiqueta: String, valor: String, fuerte: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(etiqueta, style = if (fuerte) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(valor, style = if (fuerte) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
    }
}
