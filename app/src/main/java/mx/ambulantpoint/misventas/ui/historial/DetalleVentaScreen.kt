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

/** RF 3.4 (detalle) y RF 3.3 Cancelar venta. */
@Composable
fun PantallaDetalleVenta(ventaId: String, alRegresar: () -> Unit) {
    val modelo = vm(key = ventaId) { DetalleVentaViewModel(it, ventaId) }
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
                BotonSecundario("Cancelar venta", { modelo.confirmando = true })
            }
        }
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
