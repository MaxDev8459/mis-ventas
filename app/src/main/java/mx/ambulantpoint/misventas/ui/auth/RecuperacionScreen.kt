package mx.ambulantpoint.misventas.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.sync.EstadoRecuperacion
import mx.ambulantpoint.misventas.ui.components.contenedor

/**
 * RF 7.4 — "Recuperando su información…" con indicador de progreso. Ocupa toda la pantalla: la
 * app no deja operar hasta terminar. Si falla (sin conexión), muestra el mensaje del DFR y sigue
 * bloqueada hasta que se recupere.
 */
@Composable
fun PantallaRecuperacion() {
    val recuperacion = contenedor().recuperacion
    val estado by recuperacion.estado.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()

    // Arranca sola al mostrarse (después de iniciar sesión o al volver a abrir la app si quedó pendiente).
    LaunchedEffect(Unit) { recuperacion.recuperar() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(
                Icons.Filled.Refresh, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp),
            )
            when (val e = estado) {
                EstadoRecuperacion.Fallida -> {
                    Text(Mensajes.RECUPERACION_FALLIDA, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Button(
                        { alcance.launch { recuperacion.recuperar() } },
                        Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("Intentar nuevamente") }
                }
                is EstadoRecuperacion.EnCurso -> {
                    Text(Mensajes.RECUPERANDO, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    LinearProgressIndicator(progress = { e.progreso }, modifier = Modifier.fillMaxWidth())
                }
                EstadoRecuperacion.EnEspera -> {
                    Text(Mensajes.RECUPERANDO, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
