package mx.ambulantpoint.misventas.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonPrincipal
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.ui.components.vm

/** RF 1.1 — Registrar vendedor. */
@Composable
fun PantallaRegistro(irALogin: () -> Unit) {
    val modelo = vm { RegistroViewModel(it) }
    val f = modelo.formulario
    val e = modelo.errores
    var verAviso by remember { mutableStateOf(false) }

    Scaffold(topBar = { BarraSuperior("Crear cuenta") }) { relleno ->
        Column(
            Modifier
                .padding(relleno)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CampoTexto(f.nombres, { modelo.cambiar(f.copy(nombres = it)) }, "Nombre(s) *", error = e[Campos.NOMBRES])
            CampoTexto(f.apellidoPaterno, { modelo.cambiar(f.copy(apellidoPaterno = it)) }, "Apellido paterno *", error = e[Campos.APELLIDO_PATERNO])
            CampoTexto(f.apellidoMaterno, { modelo.cambiar(f.copy(apellidoMaterno = it)) }, "Apellido materno *", error = e[Campos.APELLIDO_MATERNO])
            CampoTexto(
                f.telefono, { nuevo -> modelo.cambiar(f.copy(telefono = nuevo.filter(Char::isDigit).take(10))) },
                "Teléfono (10 dígitos) *", error = e[Campos.TELEFONO], teclado = KeyboardType.Phone,
            )
            CampoTexto(f.nombreNegocio, { modelo.cambiar(f.copy(nombreNegocio = it)) }, "Nombre del negocio *", error = e[Campos.NOMBRE_NEGOCIO])
            CampoTexto(f.correo, { modelo.cambiar(f.copy(correo = it.trim())) }, "Correo electrónico *", error = e[Campos.CORREO], teclado = KeyboardType.Email)
            CampoTexto(f.contrasena, { modelo.cambiar(f.copy(contrasena = it)) }, "Contraseña *", error = e[Campos.CONTRASENA], teclado = KeyboardType.Password, contrasena = true)
            CampoTexto(
                f.confirmarContrasena, { modelo.cambiar(f.copy(confirmarContrasena = it)) }, "Confirmar contraseña *",
                error = e[Campos.CONFIRMAR_CONTRASENA], teclado = KeyboardType.Password, contrasena = true, imeAction = ImeAction.Done,
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Checkbox(f.aceptaAvisoPrivacidad, { modelo.cambiar(f.copy(aceptaAvisoPrivacidad = it)) })
                Text("Acepto el aviso de privacidad *", Modifier.weight(1f))
            }
            TextButton(onClick = { verAviso = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Consultar aviso de privacidad")
            }
            e[Campos.AVISO_PRIVACIDAD]?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Text("Los campos con * son obligatorios.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            BotonPrincipal("Crear cuenta", modelo::registrar, cargando = modelo.cargando)
            TextButton(onClick = irALogin, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Ya tengo cuenta. Iniciar sesión")
            }
        }
    }

    if (verAviso) {
        AlertDialog(
            onDismissRequest = { verAviso = false },
            title = { Text("Aviso de privacidad") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(AVISO_PRIVACIDAD) } },
            confirmButton = { TextButton({ verAviso = false }) { Text("Cerrar") } },
        )
    }
}

/** RF 1.2 — Iniciar sesión. */
@Composable
fun PantallaLogin(irARegistro: () -> Unit) {
    val modelo = vm { LoginViewModel(it) }

    Scaffold(topBar = { BarraSuperior("Mis Ventas") }) { relleno ->
        Column(
            Modifier
                .padding(relleno)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Iniciar sesión", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            CampoTexto(modelo.correo, { modelo.correo = it.trim() }, "Correo electrónico *", error = modelo.errores[Campos.CORREO], teclado = KeyboardType.Email)
            CampoTexto(
                modelo.contrasena, { modelo.contrasena = it }, "Contraseña *", error = modelo.errores[Campos.CONTRASENA],
                teclado = KeyboardType.Password, contrasena = true, imeAction = ImeAction.Done,
            )
            Spacer(Modifier.height(8.dp))
            BotonPrincipal("Iniciar sesión", modelo::entrar, cargando = modelo.cargando)
            TextButton(onClick = irARegistro, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Crear cuenta")
            }
        }
    }
}

/**
 * BORRADOR pendiente de revisión del cliente (LFPDPPP). El DFR pide mostrarlo y aceptarlo al
 * registrarse, pero no incluye su texto.
 */
private const val AVISO_PRIVACIDAD =
    "Max Carlotas, responsable del tratamiento de sus datos personales, recaba su nombre, apellidos, " +
        "teléfono, nombre del negocio y correo electrónico con la finalidad de crear y administrar su cuenta " +
        "en la aplicación Mis Ventas y de respaldar la información de su negocio.\n\n" +
        "Sus datos no se compartirán con terceros salvo los proveedores tecnológicos necesarios para operar " +
        "la aplicación. Puede ejercer sus derechos de acceso, rectificación, cancelación u oposición (ARCO) " +
        "conforme a la Ley Federal de Protección de Datos Personales en Posesión de los Particulares.\n\n" +
        "(Borrador: texto pendiente de validación por el cliente.)"
