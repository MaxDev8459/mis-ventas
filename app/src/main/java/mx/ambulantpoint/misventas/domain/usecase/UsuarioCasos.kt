package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Validadores
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

/** Validaciones del Módulo 1. El guardado (AuthRepoLocal) llega en la Fase 4. */
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
