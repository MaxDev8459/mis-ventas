package mx.ambulantpoint.misventas.ui.caja

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.CorteFormaPago
import mx.ambulantpoint.misventas.domain.regla.ReglaCorte
import mx.ambulantpoint.misventas.domain.regla.TipoDiferencia
import mx.ambulantpoint.misventas.domain.usecase.CalcularCorte
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.CerrarCaja
import mx.ambulantpoint.misventas.domain.usecase.CorteCalculado
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonPrincipal
import mx.ambulantpoint.misventas.ui.components.BotonSecundario
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.ui.components.DialogoConfirmacion
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.atender
import mx.ambulantpoint.misventas.ui.components.vm

class CorteViewModel(c: AppContainer) : ViewModel() {
    private val calcularCorte = CalcularCorte(c.cajaRepo)
    private val cerrarCaja = CerrarCaja(c.cajaRepo)
    private val notificador = c.notificador

    val caja: StateFlow<Caja?> = c.cajaRepo.observarAbierta()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var contado by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set
    var corte by mutableStateOf<CorteCalculado?>(null)
        private set
    var confirmarCierre by mutableStateOf(false)
    var cerrada by mutableStateOf(false)
        private set

    fun cambiarContado(t: String) { contado = t; error = null }

    /** RF 5.2: calcula y muestra el resumen; la caja sigue abierta. */
    fun calcular() {
        viewModelScope.launch {
            calcularCorte(contado).atender(notificador) { error = it[Campos.EFECTIVO_CONTADO] }?.let { corte = it.valor }
        }
    }

    /** RF 5.3: tras confirmar, guarda el corte con la hora de cierre. */
    fun cerrar() {
        confirmarCierre = false
        viewModelScope.launch {
            cerrarCaja().atender(notificador) {}?.let { hecho ->
                hecho.mensaje?.let(notificador::mostrar)
                cerrada = true
            }
        }
    }
}

/** RF 5.2 Calcular corte y RF 5.3 Cerrar caja. */
@Composable
fun PantallaCorte(alRegresar: () -> Unit, alCerrar: () -> Unit) {
    val modelo = vm { CorteViewModel(it) }
    val caja by modelo.caja.collectAsStateWithLifecycle()
    LaunchedEffect(modelo.cerrada) { if (modelo.cerrada) alCerrar() }

    Scaffold(topBar = { BarraSuperior("Corte de caja", alRegresar) }) { relleno ->
        Column(
            Modifier.padding(relleno).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (caja == null && !modelo.cerrada) {
                MensajeVacio(Mensajes.ABRA_CAJA)
                return@Column
            }
            CampoTexto(
                modelo.contado, modelo::cambiarContado, "Efectivo contado *", error = modelo.error,
                teclado = KeyboardType.Decimal, prefijo = "$", imeAction = ImeAction.Done,
            )
            BotonPrincipal("Calcular corte", modelo::calcular)

            modelo.corte?.let { c ->
                ResumenCorte(c.corte.caja, c.corte.porFormaPago)
                BotonSecundario("Cerrar caja", { modelo.confirmarCierre = true })
            }
        }
    }

    if (modelo.confirmarCierre) {
        DialogoConfirmacion(Mensajes.CONFIRMAR_CERRAR_CAJA, modelo::cerrar, { modelo.confirmarCierre = false })
    }
}

/**
 * RF 5.2 / 5.5: fondo, total vendido, número de ventas, descuentos, total por forma de pago,
 * efectivo esperado, efectivo contado y diferencia (sobrante / faltante / sin diferencia).
 */
@Composable
fun ResumenCorte(caja: Caja, porFormaPago: List<CorteFormaPago>) {
    val diferencia = caja.diferenciaCentavos ?: 0
    val tipo = ReglaCorte.tipoDiferencia(diferencia)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Resumen del corte", style = MaterialTheme.typography.titleLarge)
            Linea("Fondo de caja", Formato.moneda(caja.fondoCentavos))
            Linea("Total vendido", Formato.moneda(caja.totalVendidoCentavos ?: 0))
            Linea("Número de ventas", (caja.numeroVentas ?: 0).toString())
            Linea("Descuentos aplicados", Formato.moneda(caja.descuentosCentavos ?: 0))
            HorizontalDivider()
            Text("Total por forma de pago", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (porFormaPago.isEmpty()) Linea("Sin ventas", Formato.moneda(0))
            porFormaPago.forEach { Linea("${it.nombreFormaPago} (${it.numeroVentas})", Formato.moneda(it.totalCentavos)) }
            HorizontalDivider()
            Linea("Efectivo esperado", Formato.moneda(caja.efectivoEsperadoCentavos ?: 0))
            Linea("Efectivo contado", Formato.moneda(caja.efectivoContadoCentavos ?: 0))
            val color = when (tipo) {
                TipoDiferencia.FALTANTE -> MaterialTheme.colorScheme.error
                TipoDiferencia.SOBRANTE -> MaterialTheme.colorScheme.primary
                TipoDiferencia.SIN_DIFERENCIA -> MaterialTheme.colorScheme.onSurface
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Diferencia (${tipo.etiqueta})", style = MaterialTheme.typography.titleLarge, color = color, modifier = Modifier.weight(1f))
                Text(Formato.moneda(diferencia), style = MaterialTheme.typography.titleLarge, color = color)
            }
        }
    }
}

@Composable
private fun Linea(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(etiqueta, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}
