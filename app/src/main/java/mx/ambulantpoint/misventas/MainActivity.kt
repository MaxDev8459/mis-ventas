package mx.ambulantpoint.misventas

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mx.ambulantpoint.misventas.ui.navigation.NavegacionApp
import mx.ambulantpoint.misventas.ui.theme.MisVentasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Íconos claros en la barra de estado, sobre la barra superior verde.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            MisVentasTheme {
                NavegacionApp()
            }
        }
    }
}
