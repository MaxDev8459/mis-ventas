package mx.ambulantpoint.misventas.ui.perfil

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import mx.ambulantpoint.misventas.data.preferencias.TemaApp
import mx.ambulantpoint.misventas.domain.regla.TiempoReverificacion
import mx.ambulantpoint.misventas.ui.components.ALTO_BOTON

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.imePadding
import mx.ambulantpoint.misventas.ui.components.BotonPrincipal
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.FormularioRegistro
import mx.ambulantpoint.misventas.domain.usecase.EditarPerfil
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import mx.ambulantpoint.misventas.core.Resultado
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.domain.usecase.CerrarSesion
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonSecundario
import mx.ambulantpoint.misventas.ui.components.DialogoConfirmacion
import mx.ambulantpoint.misventas.ui.components.atender
import mx.ambulantpoint.misventas.ui.components.vm

class PerfilViewModel(c: AppContainer) : ViewModel() {
    private val cerrarSesion = CerrarSesion(c.auth)
    private val notificador = c.notificador

    val perfil: StateFlow<Perfil?> = c.perfilRepo.observar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var confirmando by mutableStateOf(false)

    private val configuracion = c.configuracionRepo

    /** Ajustes solo de este teléfono (no van a la nube). */
    private val preferencias = c.preferencias
    val tema: StateFlow<TemaApp> = preferencias.tema
    val reverificacion: StateFlow<TiempoReverificacion> = preferencias.reverificacion
    fun cambiarTema(t: TemaApp) = preferencias.cambiarTema(t)
    fun cambiarReverificacion(t: TiempoReverificacion) = preferencias.cambiarReverificacion(t)

    /** RF 3.6: deshabilitados por defecto. */
    val descuentosHabilitados: StateFlow<Boolean> = configuracion.observar()
        .map { it?.descuentosHabilitados == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun cambiarDescuentos(habilitados: Boolean) {
        viewModelScope.launch {
            when (configuracion.guardarDescuentos(habilitados)) {
                is Resultado.Exito -> notificador.mostrar(Mensajes.CONFIGURACION_GUARDADA)
                is Resultado.Fallo -> Unit
            }
        }
    }

    /** RF 1.3: si hay pendientes de sincronizar, se avisa y no se cierra. */
    fun cerrar() {
        confirmando = false
        viewModelScope.launch { cerrarSesion().atender(notificador) {} }
    }
}

/** RF 1.5 — Consultar perfil (solo lectura), RF 3.6 — Configurar descuentos y RF 1.3 — Cerrar sesión. */
@Composable
fun PantallaPerfil(alRegresar: () -> Unit, alEditar: () -> Unit) {
    val modelo = vm { PerfilViewModel(it) }
    val perfil by modelo.perfil.collectAsStateWithLifecycle()
    val descuentos by modelo.descuentosHabilitados.collectAsStateWithLifecycle()

    Scaffold(topBar = { BarraSuperior("Perfil", alRegresar) }) { relleno ->
        Column(
            Modifier.padding(relleno).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            perfil?.let { p ->
                Dato("Nombre(s)", p.nombres)
                Dato("Apellido paterno", p.apellidoPaterno)
                Dato("Apellido materno", p.apellidoMaterno)
                Dato("Teléfono", p.telefono)
                Dato("Nombre del negocio", p.nombreNegocio)
                Dato("Correo electrónico", p.correo)
                BotonSecundario("Editar perfil", alEditar)
            }
            // RF 3.6 — Configurar descuentos.
            Text("Configuración", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(descuentos, role = Role.Switch, onValueChange = modelo::cambiarDescuentos),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Descuentos", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (descuentos) "Habilitados: se pueden aplicar al cobrar" else "Deshabilitados",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = descuentos, onCheckedChange = null)
            }
            HorizontalDivider()
            AjustesTelefono(modelo)
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            BotonSecundario("Cerrar sesión", { modelo.confirmando = true })
        }
    }

    if (modelo.confirmando) {
        DialogoConfirmacion(Mensajes.CONFIRMAR_CERRAR_SESION, modelo::cerrar, { modelo.confirmando = false })
    }
}

/** Tema y tiempo para volver a pedir huella: se guardan solo en este teléfono. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AjustesTelefono(modelo: PerfilViewModel) {
    val tema by modelo.tema.collectAsStateWithLifecycle()
    val reverificacion by modelo.reverificacion.collectAsStateWithLifecycle()
    Text("Este teléfono", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    Text("Apariencia", style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TemaApp.entries.forEach { t ->
            FilterChip(
                selected = tema == t, onClick = { modelo.cambiarTema(t) },
                label = { Text(t.etiqueta, style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.heightIn(min = ALTO_BOTON),
            )
        }
    }
    Text("Pedir huella o PIN al regresar a la app", style = MaterialTheme.typography.bodyLarge)
    Text(
        "Si la app estuvo en segundo plano este tiempo o más. Al abrirla desde cero siempre se pide.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TiempoReverificacion.entries.forEach { t ->
            FilterChip(
                selected = reverificacion == t, onClick = { modelo.cambiarReverificacion(t) },
                label = { Text(if (t == TiempoReverificacion.NUNCA) "Nunca" else t.etiqueta, style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.heightIn(min = ALTO_BOTON),
            )
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }
}

class EditarPerfilViewModel(c: AppContainer) : ViewModel() {
    private val editar = EditarPerfil(c.perfilRepo)
    private val notificador = c.notificador

    var formulario by mutableStateOf<FormularioRegistro?>(null)
        private set
    var correo by mutableStateOf("")
        private set
    var errores by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var guardando by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            c.perfilRepo.obtener()?.let { p ->
                correo = p.correo
                formulario = FormularioRegistro(p.nombres, p.apellidoPaterno, p.apellidoMaterno, p.telefono, p.nombreNegocio)
            }
        }
    }

    fun cambiar(f: FormularioRegistro) { formulario = f }

    fun guardar(alTerminar: () -> Unit) {
        val f = formulario ?: return
        if (guardando) return
        viewModelScope.launch {
            guardando = true
            editar(f).atender(notificador) { errores = it }?.let { hecho ->
                hecho.mensaje?.let(notificador::mostrar)
                alTerminar()
            }
            guardando = false
        }
    }
}

/** RF 1.6 — Editar perfil. El correo se muestra solo como consulta. */
@Composable
fun PantallaEditarPerfil(alRegresar: () -> Unit) {
    val modelo = vm { EditarPerfilViewModel(it) }
    val f = modelo.formulario
    val e = modelo.errores

    Scaffold(topBar = { BarraSuperior("Editar perfil", alRegresar) }) { relleno ->
        if (f == null) return@Scaffold
        Column(
            Modifier.padding(relleno).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CampoTexto(f.nombres, { modelo.cambiar(f.copy(nombres = it)) }, "Nombre(s)", error = e[Campos.NOMBRES])
            CampoTexto(f.apellidoPaterno, { modelo.cambiar(f.copy(apellidoPaterno = it)) }, "Apellido paterno", error = e[Campos.APELLIDO_PATERNO])
            CampoTexto(f.apellidoMaterno, { modelo.cambiar(f.copy(apellidoMaterno = it)) }, "Apellido materno", error = e[Campos.APELLIDO_MATERNO])
            CampoTexto(
                f.telefono, { t -> modelo.cambiar(f.copy(telefono = t.filter(Char::isDigit).take(10))) }, "Teléfono",
                error = e[Campos.TELEFONO], teclado = KeyboardType.Phone,
            )
            CampoTexto(
                f.nombreNegocio, { modelo.cambiar(f.copy(nombreNegocio = it)) }, "Nombre del negocio",
                error = e[Campos.NOMBRE_NEGOCIO], imeAction = ImeAction.Done,
            )
            CampoTexto(modelo.correo, {}, "Correo electrónico (no editable)", soloLectura = true)
            Spacer(Modifier.height(8.dp))
            BotonPrincipal("Guardar", { modelo.guardar(alRegresar) }, cargando = modelo.guardando)
        }
    }
}
