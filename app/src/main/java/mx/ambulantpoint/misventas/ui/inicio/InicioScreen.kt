package mx.ambulantpoint.misventas.ui.inicio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.domain.regla.EstadoIndicador
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.vm

data class EstadoInicio(
    val nombreNegocio: String = "",
    val caja: Caja? = null,
    val indicador: EstadoIndicador = EstadoIndicador.Sincronizado,
    val ultimaSincronizacion: Long? = null,
)

class InicioViewModel(c: AppContainer) : ViewModel() {
    private val sync = c.sincronizacion

    val estado: StateFlow<EstadoInicio> = combine(
        c.perfilRepo.observar(),
        c.cajaRepo.observarAbierta(),
        sync.indicador,
        sync.ultimaSincronizacion,
    ) { perfil, caja, indicador, ultima ->
        EstadoInicio(perfil?.nombreNegocio.orEmpty(), caja, indicador, ultima)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoInicio())

    /** RF 7.3. */
    fun sincronizarAhora() = sync.sincronizarAhora()
}

/** Destinos del menú principal. */
enum class Destino(val titulo: String, val icono: ImageVector) {
    VENDER("Vender", Icons.Filled.ShoppingCart),
    CAJA("Caja", Icons.Filled.Lock),
    HISTORIAL("Historial de ventas", Icons.Filled.DateRange),
    CLIENTES("Clientes / Fiado", Icons.Filled.Person),
    CATALOGO("Catálogo", Icons.AutoMirrored.Filled.List),
    CATEGORIAS("Categorías", Icons.Filled.Menu),
    EXISTENCIAS("Existencias", Icons.Filled.Info),
    REPORTES("Reportes", Icons.Filled.Search),
    FORMAS_PAGO("Formas de pago", Icons.Filled.Settings),
    PERFIL("Perfil", Icons.Filled.AccountCircle),
}

/**
 * RNF Interfaz: la pantalla principal muestra el nombre del negocio, el estado de la caja y el
 * indicador de sincronización (RF 7.2).
 */
@Composable
fun PantallaInicio(alElegir: (Destino) -> Unit, alVenderSinCaja: () -> Unit) {
    val modelo = vm { InicioViewModel(it) }
    val estado by modelo.estado.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            BarraSuperior(estado.nombreNegocio.ifBlank { "Mis Ventas" }) {
                // Perfil va como ícono en la barra para dejar 8 botones en la cuadrícula.
                IconButton(onClick = { alElegir(Destino.PERFIL) }) {
                    Icon(Destino.PERFIL.icono, contentDescription = Destino.PERFIL.titulo, modifier = Modifier.size(32.dp))
                }
            }
        },
    ) { relleno ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(relleno).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) { TarjetaEstado(estado, modelo::sincronizarAhora) }
            items(Destino.entries.filter { it != Destino.PERFIL }) { destino ->
                BotonMenu(destino) {
                    if (destino == Destino.VENDER && estado.caja == null) alVenderSinCaja() else alElegir(destino)
                }
            }
        }
    }
}

@Composable
private fun TarjetaEstado(estado: EstadoInicio, alSincronizarAhora: () -> Unit) {
    var verSync by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val caja = estado.caja
            Text(
                if (caja != null) "Caja abierta" else "Caja cerrada",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (caja != null) {
                Text(
                    "Desde ${Formato.fechaHora(caja.abiertaEn)} · Fondo ${Formato.moneda(caja.fondoCentavos)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            // RF 7.2: indicador de sincronización; al tocarlo muestra la última sincronización.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 48.dp).clickable { verSync = true },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    estado.indicador.texto,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
    if (verSync) {
        AlertDialog(
            onDismissRequest = { verSync = false },
            // RF 7.2: al tocar el indicador, fecha y hora de la última sincronización exitosa.
            text = { Text("Última sincronización exitosa: " + (estado.ultimaSincronizacion?.let(Formato::fechaHora) ?: "ninguna")) },
            // RF 7.3: "Sincronizar ahora".
            confirmButton = {
                TextButton({ verSync = false; alSincronizarAhora() }) { Text(Mensajes.BOTON_SINCRONIZAR_AHORA) }
            },
            dismissButton = { TextButton({ verSync = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun BotonMenu(destino: Destino, alPulsar: () -> Unit) {
    ElevatedCard(onClick = alPulsar, modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(destino.icono, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text(destino.titulo, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}
