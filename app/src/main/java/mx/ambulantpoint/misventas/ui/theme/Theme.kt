package mx.ambulantpoint.misventas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EsquemaClaro = lightColorScheme(
    primary = VerdeAccion,
    onPrimary = Color.White,
    primaryContainer = VerdeContenedor,
    onPrimaryContainer = VerdeSobreContenedor,
    secondary = VerdeLogo,
    onSecondary = Color.White,
    background = Fondo,
    onBackground = TextoPrincipal,
    surface = Superficie,
    onSurface = TextoPrincipal,
    onSurfaceVariant = TextoSecundario,
    outline = Contorno,
    error = Error,
    onError = Color.White,
    // Superficies neutras con matiz verde (sin esto Material 3 usa su morado por defecto).
    secondaryContainer = VerdeContenedor,
    onSecondaryContainer = VerdeSobreContenedor,
    tertiary = VerdeLogo,
    surfaceVariant = Color(0xFFE1E6DC),
    surfaceTint = VerdeAccion,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5EE),
    surfaceContainer = Color(0xFFEBF0E8),
    surfaceContainerHigh = Color(0xFFE5EBE2),
    surfaceContainerHighest = Color(0xFFDFE5DC),
)

/** Tema de la app. Sin colores dinámicos: siempre la identidad del logo. */
@Composable
fun MisVentasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaClaro,
        typography = Tipografia,
        content = content,
    )
}
