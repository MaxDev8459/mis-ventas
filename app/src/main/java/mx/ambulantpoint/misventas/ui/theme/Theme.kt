package mx.ambulantpoint.misventas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import mx.ambulantpoint.misventas.data.preferencias.TemaApp

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

/** Modo oscuro con el mismo verde del logo, aclarado para leerse sobre fondo oscuro. */
private val EsquemaOscuro = darkColorScheme(
    primary = Color(0xFFADD3A4),
    onPrimary = Color(0xFF19381A),
    primaryContainer = Color(0xFF2F4F2B),
    onPrimaryContainer = Color(0xFFC9EFBF),
    secondary = Color(0xFFA9CFA2),
    onSecondary = Color(0xFF153815),
    background = Color(0xFF111410),
    onBackground = Color(0xFFE1E4DB),
    surface = Color(0xFF111410),
    onSurface = Color(0xFFE1E4DB),
    onSurfaceVariant = Color(0xFFC2C8BB),
    outline = Color(0xFF8C9387),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    secondaryContainer = Color(0xFF2F4F2B),
    onSecondaryContainer = Color(0xFFC9EFBF),
    tertiary = Color(0xFFA9CFA2),
    surfaceVariant = Color(0xFF43483F),
    surfaceTint = Color(0xFFADD3A4),
    surfaceContainerLowest = Color(0xFF0C0F0B),
    surfaceContainerLow = Color(0xFF191D17),
    surfaceContainer = Color(0xFF1D211B),
    surfaceContainerHigh = Color(0xFF282B25),
    surfaceContainerHighest = Color(0xFF323630),
)

/** true si la app se está mostrando en modo oscuro (lo usa la barra superior). */
val LocalTemaOscuro = staticCompositionLocalOf { false }

/** Tema de la app. Sin colores dinámicos: siempre la identidad del logo, en claro u oscuro. */
@Composable
fun MisVentasTheme(tema: TemaApp = TemaApp.SISTEMA, content: @Composable () -> Unit) {
    val oscuro = when (tema) {
        TemaApp.SISTEMA -> isSystemInDarkTheme()
        TemaApp.CLARO -> false
        TemaApp.OSCURO -> true
    }
    CompositionLocalProvider(LocalTemaOscuro provides oscuro) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
            typography = Tipografia,
            content = content,
        )
    }
}
