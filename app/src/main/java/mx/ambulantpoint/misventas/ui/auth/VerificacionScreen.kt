package mx.ambulantpoint.misventas.ui.auth

import android.content.Intent
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Huella, rostro, PIN, patrón o contraseña del teléfono. BIOMETRIC_WEAK | DEVICE_CREDENTIAL es la
 * combinación que Android admite desde API 26 (minSdk) hasta las versiones actuales.
 */
private const val METODOS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/**
 * RF 1.2 — Verificación de identidad al volver a abrir la app con una sesión activa. No requiere
 * internet. Si falla o se cancela, el acceso sigue bloqueado; si el teléfono no tiene método de
 * seguridad, se pide configurarlo antes de entrar.
 */
@Composable
fun PantallaVerificacion(alVerificar: () -> Unit) {
    val actividad = LocalContext.current as FragmentActivity
    // Se vuelve a revisar al regresar de Ajustes, por si el vendedor configuró un método.
    var hayMetodo by remember { mutableStateOf(hayMetodoSeguridad(actividad)) }
    var intento by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hayMetodo = hayMetodoSeguridad(actividad) }

    // El diálogo del sistema se abre solo al mostrar la pantalla y cada vez que se pulsa el botón.
    LaunchedEffect(hayMetodo, intento) {
        if (hayMetodo) pedirVerificacion(actividad, alVerificar)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Text("Mis Ventas", style = MaterialTheme.typography.headlineMedium)
            if (hayMetodo) {
                Text(
                    "Verifique su identidad con el método de seguridad de su teléfono para continuar.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                )
                Button({ intento++ }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Verificar identidad") }
            } else {
                Text(
                    "Su teléfono no tiene un método de seguridad. Configure huella, PIN, patrón o contraseña para continuar.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                )
                Button(
                    { actividad.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("Configurar seguridad") }
            }
        }
    }
}

private fun hayMetodoSeguridad(actividad: FragmentActivity): Boolean =
    BiometricManager.from(actividad).canAuthenticate(METODOS) == BiometricManager.BIOMETRIC_SUCCESS

private fun pedirVerificacion(actividad: FragmentActivity, alVerificar: () -> Unit) {
    val prompt = BiometricPrompt(
        actividad,
        ContextCompat.getMainExecutor(actividad),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = alVerificar()
            // Error o cancelación: no se hace nada; la pantalla sigue bloqueada con su botón para reintentar.
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verificar identidad")
            .setSubtitle("Mis Ventas")
            .setAllowedAuthenticators(METODOS)
            .build(),
    )
}
