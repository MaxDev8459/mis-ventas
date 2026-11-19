package mx.ambulantpoint.misventas.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.MisVentasApp

/** RNF Usabilidad: elementos táctiles de al menos 48 dp; los botones principales usan 56 dp. */
val ALTO_BOTON = 56.dp

/** Mensajes de una sola vez (éxito / error) que muestra la barra inferior (snackbar). */
class Notificador {
    private val _mensajes = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val mensajes: SharedFlow<String> = _mensajes
    fun mostrar(texto: String) { _mensajes.tryEmit(texto) }
}

@Composable
fun contenedor(): AppContainer = (LocalContext.current.applicationContext as MisVentasApp).contenedor

/** ViewModel con dependencias del contenedor (DI manual). */
@Composable
inline fun <reified VM : ViewModel> vm(key: String? = null, crossinline crear: (AppContainer) -> VM): VM {
    val c = contenedor()
    return viewModel(key = key, factory = viewModelFactory { initializer { crear(c) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior(titulo: String, alRegresar: (() -> Unit)? = null, acciones: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(titulo, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (alRegresar != null) {
                IconButton(onClick = alRegresar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                }
            }
        },
        actions = { acciones() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/** Campo de texto con su alerta debajo (RNF: indicar claramente el campo con error). */
@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    teclado: KeyboardType = KeyboardType.Text,
    contrasena: Boolean = false,
    soloLectura: Boolean = false,
    prefijo: String? = null,
    sufijo: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    // Contraseñas: ocultas por defecto; el ojito las muestra u oculta.
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        modifier = modifier.fillMaxWidth(),
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        readOnly = soloLectura,
        prefix = prefijo?.let { { Text(it) } },
        suffix = sufijo?.let { { Text(it) } },
        trailingIcon = if (contrasena) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) IconoOcultar else IconoVer,
                        contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                    )
                }
            }
        } else {
            null
        },
        visualTransformation = if (contrasena && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (contrasena && visible) KeyboardType.Text else teclado, imeAction = imeAction),
    )
}

/** Íconos "visibility" y "visibility_off" de Material (no vienen en material-icons-core). */
private fun iconoMaterial(nombre: String, trazo: String): ImageVector =
    ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(trazo), fill = SolidColor(Color.Black))
        .build()

private val IconoVer = iconoMaterial(
    "Ver",
    "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17" +
        "c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z",
)

private val IconoOcultar = iconoMaterial(
    "Ocultar",
    "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5" +
        "-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12" +
        "c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55" +
        "c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5" +
        " 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z",
)

@Composable
fun BotonPrincipal(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true, cargando: Boolean = false) {
    Button(
        onClick = alPulsar,
        enabled = habilitado && !cargando,
        modifier = modifier.fillMaxWidth().height(ALTO_BOTON),
    ) {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text(texto, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun BotonSecundario(texto: String, alPulsar: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    OutlinedButton(onClick = alPulsar, enabled = habilitado, modifier = modifier.fillMaxWidth().height(ALTO_BOTON)) {
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Confirmaciones del DFR ("¿Desea…?"). */
@Composable
fun DialogoConfirmacion(
    texto: String,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
    textoConfirmar: String = "Aceptar",
    textoCancelar: String = "Cancelar",
) {
    AlertDialog(
        onDismissRequest = alCancelar,
        text = { Text(texto, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = alConfirmar, modifier = Modifier.heightIn(min = 48.dp)) { Text(textoConfirmar) }
        },
        dismissButton = {
            TextButton(onClick = alCancelar, modifier = Modifier.heightIn(min = 48.dp)) { Text(textoCancelar) }
        },
    )
}

/** Mensaje centrado para listas vacías ("No se encontraron productos", etc.). */
@Composable
fun MensajeVacio(texto: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
