package mx.ambulantpoint.misventas.ui.auth

import mx.ambulantpoint.misventas.domain.regla.FallaMensaje
import mx.ambulantpoint.misventas.domain.regla.FallaCampos
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.domain.usecase.RecuperarContrasena
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.FormularioRegistro
import mx.ambulantpoint.misventas.domain.usecase.IniciarSesion
import mx.ambulantpoint.misventas.domain.usecase.RegistrarVendedor
import mx.ambulantpoint.misventas.ui.components.atender

/** RF 1.1. Al registrarse, la sesión queda activa y la navegación pasa sola a la pantalla principal. */
class RegistroViewModel(c: AppContainer) : ViewModel() {
    private val registrarVendedor = RegistrarVendedor(c.auth)
    private val notificador = c.notificador

    var formulario by mutableStateOf(FormularioRegistro())
        private set
    var errores by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var cargando by mutableStateOf(false)
        private set

    /** Al corregir un campo se quita su alerta. */
    fun cambiar(nuevo: FormularioRegistro) {
        val v = formulario
        formulario = nuevo
        val corregidos = buildSet {
            if (v.nombres != nuevo.nombres) add(Campos.NOMBRES)
            if (v.apellidoPaterno != nuevo.apellidoPaterno) add(Campos.APELLIDO_PATERNO)
            if (v.apellidoMaterno != nuevo.apellidoMaterno) add(Campos.APELLIDO_MATERNO)
            if (v.telefono != nuevo.telefono) add(Campos.TELEFONO)
            if (v.nombreNegocio != nuevo.nombreNegocio) add(Campos.NOMBRE_NEGOCIO)
            if (v.correo != nuevo.correo) add(Campos.CORREO)
            if (v.contrasena != nuevo.contrasena) add(Campos.CONTRASENA)
            if (v.confirmarContrasena != nuevo.confirmarContrasena) add(Campos.CONFIRMAR_CONTRASENA)
            if (v.aceptaAvisoPrivacidad != nuevo.aceptaAvisoPrivacidad) add(Campos.AVISO_PRIVACIDAD)
        }
        if (corregidos.isNotEmpty()) errores = errores - corregidos
    }

    fun registrar() {
        if (cargando) return
        viewModelScope.launch {
            cargando = true
            registrarVendedor(formulario).atender(notificador) { errores = it }?.let { hecho ->
                hecho.mensaje?.let(notificador::mostrar)
            }
            cargando = false
        }
    }
}

/** RF 1.2. */
class LoginViewModel(c: AppContainer) : ViewModel() {
    private val iniciarSesion = IniciarSesion(c.auth)
    private val notificador = c.notificador

    var correo by mutableStateOf("")
    var contrasena by mutableStateOf("")
    var errores by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var cargando by mutableStateOf(false)
        private set

    fun entrar() {
        if (cargando) return
        viewModelScope.launch {
            cargando = true
            iniciarSesion(correo, contrasena).atender(notificador) { errores = it }
            cargando = false
        }
    }

    // ── RF 1.4: recuperar contraseña (diálogo dentro del login) ──
    private val recuperarContrasena = RecuperarContrasena(c.auth)

    var recuperando by mutableStateOf(false)
        private set
    var correoRecuperar by mutableStateOf("")
    /** Alerta del campo o mensaje de falla, mostrados dentro del diálogo. */
    var errorRecuperar by mutableStateOf<String?>(null)
        private set

    fun abrirRecuperar() {
        correoRecuperar = correo
        errorRecuperar = null
        recuperando = true
    }

    fun cerrarRecuperar() { if (!cargando) recuperando = false }

    fun enviarRecuperar() {
        if (cargando) return
        viewModelScope.launch {
            cargando = true
            when (val r = recuperarContrasena(correoRecuperar)) {
                is Resultado.Exito -> {
                    recuperando = false
                    r.valor.mensaje?.let(notificador::mostrar)
                }
                is Resultado.Fallo -> errorRecuperar = when (val f = r.falla) {
                    is FallaCampos -> f.errores.values.firstOrNull()
                    is FallaMensaje -> f.mensaje
                    else -> f.toString()
                }
            }
            cargando = false
        }
    }
}
