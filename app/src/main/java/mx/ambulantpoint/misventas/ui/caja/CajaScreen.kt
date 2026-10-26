package mx.ambulantpoint.misventas.ui.caja

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.domain.usecase.AbrirCaja
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonPrincipal
import mx.ambulantpoint.misventas.ui.components.BotonSecundario
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.ui.components.atender
import mx.ambulantpoint.misventas.ui.components.vm

/** Estado de la caja: cargando (null), sin caja abierta o con caja abierta. */
sealed interface EstadoCaja {
    data object Cerrada : EstadoCaja
    data class Abierta(val caja: Caja) : EstadoCaja
}

class CajaViewModel(c: AppContainer) : ViewModel() {
    private val abrirCaja = AbrirCaja(c.cajaRepo)
    private val notificador = c.notificador

    val estado: StateFlow<EstadoCaja?> = c.cajaRepo.observarAbierta()
        .map { if (it == null) EstadoCaja.Cerrada else EstadoCaja.Abierta(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var fondo by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set
    var abriendo by mutableStateOf(false)
        private set

    /** RF 5.1. */
    fun abrir() {
        if (abriendo) return
        viewModelScope.launch {
            abriendo = true
            abrirCaja(fondo).atender(notificador) { error = it[Campos.FONDO] }?.let { hecho ->
                hecho.mensaje?.let(notificador::mostrar)
                fondo = ""
            }
            abriendo = false
        }
    }
}

/** RF 5.1 Abrir caja; con caja abierta da acceso a vender y al corte (RF 5.2). */
@Composable
fun PantallaCaja(alRegresar: () -> Unit, alVender: () -> Unit, alCorte: () -> Unit, alCortes: () -> Unit) {
    val modelo = vm { CajaViewModel(it) }
    val estado by modelo.estado.collectAsStateWithLifecycle()

    Scaffold(topBar = { BarraSuperior("Caja", alRegresar) }) { relleno ->
        Column(
            Modifier.padding(relleno).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val e = estado) {
                null -> Unit
                EstadoCaja.Cerrada -> {
                    // RF 5.1: si ya hay caja abierta, la opción de abrir no está disponible (esta rama no se muestra).
                    Text("Abrir caja", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Capture el efectivo con el que inicia para dar cambio. Puede ser cero.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CampoTexto(
                        modelo.fondo, { modelo.fondo = it }, "Fondo de caja *", error = modelo.error,
                        teclado = KeyboardType.Decimal, prefijo = "$", imeAction = ImeAction.Done,
                    )
                    BotonPrincipal("Abrir caja", modelo::abrir, cargando = modelo.abriendo)
                }
                is EstadoCaja.Abierta -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Caja abierta", style = MaterialTheme.typography.titleLarge)
                            Text("Desde ${Formato.fechaHora(e.caja.abiertaEn)}", style = MaterialTheme.typography.bodyLarge)
                            Text("Fondo de caja: ${Formato.moneda(e.caja.fondoCentavos)}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    BotonPrincipal("Vender", alVender)
                    BotonSecundario("Corte de caja", alCorte)
                }
            }
            BotonSecundario("Cortes de caja anteriores", alCortes)
        }
    }
}
