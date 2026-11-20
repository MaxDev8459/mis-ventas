package mx.ambulantpoint.misventas

import mx.ambulantpoint.misventas.data.notificaciones.Recordatorios
import mx.ambulantpoint.misventas.data.notificaciones.DestinoNotificacion
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import mx.ambulantpoint.misventas.ui.navigation.NavegacionApp
import mx.ambulantpoint.misventas.ui.theme.MisVentasTheme

/** FragmentActivity: la requiere BiometricPrompt (RF 1.2). */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Íconos claros en la barra de estado, sobre la barra superior verde.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // RF 5.4 / 7.6: si se abrió desde una notificación, a dónde llevar al vendedor.
        intent?.getStringExtra(Recordatorios.EXTRA_DESTINO)
            ?.let { n -> DestinoNotificacion.entries.firstOrNull { it.name == n } }
            ?.let { (application as MisVentasApp).contenedor.destinoNotificacion.value = it }
        setContent {
            MisVentasTheme {
                NavegacionApp()
            }
        }
    }
}
