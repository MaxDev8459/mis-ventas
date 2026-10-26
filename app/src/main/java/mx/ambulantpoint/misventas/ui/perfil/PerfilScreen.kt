package mx.ambulantpoint.misventas.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.domain.usecase.CerrarSesion
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonSecundario
import mx.ambulantpoint.misventas.ui.components.DialogoConfirmacion
import mx.ambulantpoint.misventas.ui.components.atender
import mx.ambulantpoint.misventas.ui.components.vm

class PerfilViewModel(c: AppContainer) : ViewModel() {
    private val cerrarSesion = CerrarSesion(c.auth)
    private val notificador = c.notificador

    val perfil: StateFlow<Perfil?> = c.perfilRepo.observar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var confirmando by mutableStateOf(false)

    /** RF 1.3: si hay pendientes de sincronizar, se avisa y no se cierra. */
    fun cerrar() {
        confirmando = false
        viewModelScope.launch { cerrarSesion().atender(notificador) {} }
    }
}

/** RF 1.5 — Consultar perfil (solo lectura) y RF 1.3 — Cerrar sesión. */
@Composable
fun PantallaPerfil(alRegresar: () -> Unit) {
    val modelo = vm { PerfilViewModel(it) }
    val perfil by modelo.perfil.collectAsStateWithLifecycle()

    Scaffold(topBar = { BarraSuperior("Perfil", alRegresar) }) { relleno ->
        Column(
            Modifier.padding(relleno).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            perfil?.let { p ->
                Dato("Nombre(s)", p.nombres)
                Dato("Apellido paterno", p.apellidoPaterno)
                Dato("Apellido materno", p.apellidoMaterno)
                Dato("Teléfono", p.telefono)
                Dato("Nombre del negocio", p.nombreNegocio)
                Dato("Correo electrónico", p.correo)
            }
            Spacer(Modifier.height(16.dp))
            BotonSecundario("Cerrar sesión", { modelo.confirmando = true })
        }
    }

    if (modelo.confirmando) {
        DialogoConfirmacion(Mensajes.CONFIRMAR_CERRAR_SESION, modelo::cerrar, { modelo.confirmando = false })
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }
}
