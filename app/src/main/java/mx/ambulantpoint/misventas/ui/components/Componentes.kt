package mx.ambulantpoint.misventas.ui.components

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
        visualTransformation = if (contrasena) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = imeAction),
    )
}

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
