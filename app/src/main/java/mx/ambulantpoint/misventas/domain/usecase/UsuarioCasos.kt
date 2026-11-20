package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.domain.puertos.PuertoPerfil
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.Validadores
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.domain.puertos.DatosRegistro
import mx.ambulantpoint.misventas.domain.puertos.PuertoAuth
import mx.ambulantpoint.misventas.domain.regla.FallaCampos

/** RF 1.1: datos del registro tal como se capturan. */
data class FormularioRegistro(
    val nombres: String = "",
    val apellidoPaterno: String = "",
    val apellidoMaterno: String = "",
    val telefono: String = "",
    val nombreNegocio: String = "",
    val correo: String = "",
    val contrasena: String = "",
    val confirmarContrasena: String = "",
    val aceptaAvisoPrivacidad: Boolean = false,
)

/** RF 1.1. */
class RegistrarVendedor(private val auth: PuertoAuth) {
    suspend operator fun invoke(f: FormularioRegistro): Resultado<Hecho<Perfil>> {
        ValidarUsuario.registro(f)?.let { return Resultado.Fallo(it) }
        val datos = DatosRegistro(
            f.nombres, f.apellidoPaterno, f.apellidoMaterno, f.telefono, f.nombreNegocio, f.correo, f.contrasena,
        )
        return ejecutar(Mensajes.CUENTA_NO_REGISTRADA, campoNombre = Campos.CORREO, operacion = {
            auth.registrar(datos).sinInternetComo(Mensajes.REGISTRO_SIN_INTERNET)
        }) {
            Hecho(it, Mensajes.CUENTA_REGISTRADA)
        }
    }
}

/** RF 1.2 — inicio de sesión con correo y contraseña (la verificación del teléfono es aparte). */
class IniciarSesion(private val auth: PuertoAuth) {
    suspend operator fun invoke(correo: String, contrasena: String): Resultado<Hecho<Unit>> {
        ValidarUsuario.inicioSesion(correo, contrasena)?.let { return Resultado.Fallo(it) }
        return ejecutar(Mensajes.CREDENCIALES_INCORRECTAS, operacion = {
            auth.iniciarSesion(correo, contrasena).sinInternetComo(Mensajes.LOGIN_SIN_INTERNET)
        }) {
            Hecho(Unit)
        }
    }
}

/**
 * RF 1.4 — Recuperar contraseña. El mensaje de éxito es el mismo exista o no el correo (el DFR lo
 * redacta así para no revelar qué correos tienen cuenta).
 */
class RecuperarContrasena(private val auth: PuertoAuth) {
    suspend operator fun invoke(correo: String): Resultado<Hecho<Unit>> {
        Validadores.correo(correo)?.let { return campo(Campos.CORREO, it) }
        return ejecutar(Mensajes.RECUPERAR_SIN_INTERNET, operacion = {
            auth.recuperarContrasena(correo).sinInternetComo(Mensajes.RECUPERAR_SIN_INTERNET)
        }) {
            Hecho(Unit, Mensajes.RECUPERAR_CONTRASENA_ENVIADO)
        }
    }
}

/**
 * RF 1.6 — Editar perfil. Todos los campos son opcionales para editar, pero nombre(s), apellidos y
 * nombre del negocio no pueden quedar vacíos y el teléfono debe tener 10 dígitos. El correo no se
 * edita. Funciona sin conexión; el cambio se sincroniza después (queda pendiente).
 */
class EditarPerfil(private val perfiles: PuertoPerfil) {
    suspend operator fun invoke(f: FormularioRegistro): Resultado<Hecho<Perfil>> {
        ValidarUsuario.edicionPerfil(f)?.let { return Resultado.Fallo(it) }
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = {
            val actual = perfiles.obtener() ?: return@ejecutar Resultado.Fallo(FallaDatos.NoEncontrado)
            perfiles.guardar(
                actual.copy(
                    nombres = f.nombres.trim(),
                    apellidoPaterno = f.apellidoPaterno.trim(),
                    apellidoMaterno = f.apellidoMaterno.trim(),
                    telefono = f.telefono.trim(),
                    nombreNegocio = f.nombreNegocio.trim(),
                ),
            )
        }) { Hecho(it, Mensajes.PERFIL_ACTUALIZADO) }
    }
}

/** Cambia la falla "sin internet" por el mensaje del DFR de cada operación. */
private fun <T> Resultado<T>.sinInternetComo(mensaje: String): Resultado<T> =
    if (this is Resultado.Fallo && falla == FallaDatos.SinInternet) mensaje(mensaje) else this

/** RF 1.3. La confirmación es [Mensajes.CONFIRMAR_CERRAR_SESION]. */
class CerrarSesion(private val auth: PuertoAuth) {
    suspend operator fun invoke(): Resultado<Hecho<Unit>> =
        ejecutar(Mensajes.PENDIENTES_AL_CERRAR_SESION, operacion = { auth.cerrarSesion() }) { Hecho(Unit) }
}

/** Validaciones del Módulo 1. */
object ValidarUsuario {

    /** RF 1.1. Devuelve null si todo es válido. */
    fun registro(f: FormularioRegistro): FallaCampos? {
        val e = Errores()
        e.si(Campos.NOMBRES, Validadores.obligatorio(f.nombres))
        e.si(Campos.APELLIDO_PATERNO, Validadores.obligatorio(f.apellidoPaterno))
        e.si(Campos.APELLIDO_MATERNO, Validadores.obligatorio(f.apellidoMaterno))
        e.si(Campos.TELEFONO, Validadores.telefono(f.telefono))
        e.si(Campos.NOMBRE_NEGOCIO, Validadores.obligatorio(f.nombreNegocio))
        e.si(Campos.CORREO, Validadores.correo(f.correo))
        e.si(Campos.CONTRASENA, Validadores.contrasena(f.contrasena))
        e.si(Campos.CONFIRMAR_CONTRASENA, Validadores.confirmarContrasena(f.contrasena, f.confirmarContrasena))
        e.si(Campos.AVISO_PRIVACIDAD, if (!f.aceptaAvisoPrivacidad) Mensajes.Campo.ACEPTAR_AVISO else null)
        return (e.falla().falla as FallaCampos).takeIf { e.hay }
    }

    /** RF 1.2: solo campos sin llenar y formato de correo. */
    fun inicioSesion(correo: String, contrasena: String): FallaCampos? {
        val e = Errores()
        e.si(Campos.CORREO, Validadores.correo(correo))
        e.si(Campos.CONTRASENA, Validadores.obligatorio(contrasena))
        return (e.falla().falla as FallaCampos).takeIf { e.hay }
    }

    /** RF 1.6: nombres y negocio no vacíos, teléfono de 10 dígitos. */
    fun edicionPerfil(f: FormularioRegistro): FallaCampos? {
        val e = Errores()
        e.si(Campos.NOMBRES, Validadores.obligatorio(f.nombres))
        e.si(Campos.APELLIDO_PATERNO, Validadores.obligatorio(f.apellidoPaterno))
        e.si(Campos.APELLIDO_MATERNO, Validadores.obligatorio(f.apellidoMaterno))
        e.si(Campos.TELEFONO, Validadores.telefono(f.telefono))
        e.si(Campos.NOMBRE_NEGOCIO, Validadores.obligatorio(f.nombreNegocio))
        return (e.falla().falla as FallaCampos).takeIf { e.hay }
    }
}
