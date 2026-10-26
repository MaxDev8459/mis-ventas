package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.core.Falla
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Lectura
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.domain.regla.FallaCampos
import mx.ambulantpoint.misventas.domain.regla.FallaMensaje

/** Éxito de un caso de uso: el valor, el mensaje del DFR y avisos extra (ej. existencia baja). */
data class Hecho<out T>(
    val valor: T,
    val mensaje: String? = null,
    val avisos: List<String> = emptyList(),
)

/** Claves de campo para marcar el error en la pantalla. */
object Campos {
    const val NOMBRE = "nombre"
    const val CATEGORIA = "categoria"
    const val TIPO_VENTA = "tipoVenta"
    const val PRECIO = "precio"
    const val EXISTENCIA = "existencia"
    const val IMAGEN = "imagen"
    const val CANTIDAD = "cantidad"
    const val MONTO_RECIBIDO = "montoRecibido"
    const val FORMA_PAGO = "formaPago"
    const val FONDO = "fondo"
    const val EFECTIVO_CONTADO = "efectivoContado"
    const val MOTIVO = "motivo"
    const val ESPECIFICACION = "especificacion"
    const val DESCUENTO = "descuento"
    const val NOMBRES = "nombres"
    const val APELLIDO_PATERNO = "apellidoPaterno"
    const val APELLIDO_MATERNO = "apellidoMaterno"
    const val TELEFONO = "telefono"
    const val NOMBRE_NEGOCIO = "nombreNegocio"
    const val CORREO = "correo"
    const val CONTRASENA = "contrasena"
    const val CONFIRMAR_CONTRASENA = "confirmarContrasena"
    const val AVISO_PRIVACIDAD = "avisoPrivacidad"
}

/** Junta alertas de campo; si hay alguna, la operación no se intenta. */
internal class Errores {
    private val mapa = linkedMapOf<String, String>()
    fun si(campo: String, alerta: String?) { if (alerta != null && campo !in mapa) mapa[campo] = alerta }
    fun leer(campo: String, lectura: Lectura): Long? = when (lectura) {
        is Lectura.Valor -> lectura.valor
        is Lectura.Error -> { si(campo, lectura.mensaje); null }
    }
    val hay: Boolean get() = mapa.isNotEmpty()
    fun falla(): Resultado.Fallo = Resultado.Fallo(FallaCampos(mapa.toMap()))
}

internal fun mensaje(texto: String): Resultado.Fallo = Resultado.Fallo(FallaMensaje(texto))
internal fun campo(campo: String, texto: String): Resultado.Fallo = Resultado.Fallo(FallaCampos(mapOf(campo to texto)))

/**
 * Traduce una falla de datos a lo que ve el vendedor. [general] es el mensaje del DFR para
 * "no se completó" (ej. "El producto no se registró exitosamente, intente de nuevo").
 */
internal fun traducir(falla: Falla, general: String, campoNombre: String = Campos.NOMBRE): Resultado.Fallo = when (falla) {
    is FallaCampos, is FallaMensaje -> Resultado.Fallo(falla)
    FallaDatos.NombreDuplicado -> campo(campoNombre, Mensajes.Campo.NOMBRE_YA_REGISTRADO)
    FallaDatos.SinCajaAbierta -> mensaje(Mensajes.ABRA_CAJA)
    FallaDatos.CajaYaAbierta -> mensaje(Mensajes.Campo.CAJA_YA_ABIERTA)
    FallaDatos.VentaSinProductos -> mensaje(Mensajes.AGREGUE_UN_PRODUCTO)
    is FallaDatos.ExistenciaInsuficiente -> mensaje(
        Mensajes.soloHayDisponibles(Formato.cantidad(falla.disponibleMilesimas, falla.tipoVenta), falla.nombreProducto),
    )
    is FallaDatos.ProductoNoDisponible -> mensaje(Mensajes.Campo.PRODUCTO_NO_DISPONIBLE)
    FallaDatos.FormaPagoNoDisponible -> mensaje(Mensajes.Campo.FORMA_PAGO_NO_DISPONIBLE)
    FallaDatos.MontoRecibidoInsuficiente -> campo(Campos.MONTO_RECIBIDO, Mensajes.MONTO_RECIBIDO_MENOR)
    FallaDatos.DescuentoInvalido -> campo(Campos.DESCUENTO, Mensajes.DESCUENTO_NO_VALIDO)
    FallaDatos.VentaNoCancelable -> mensaje(Mensajes.Campo.VENTA_NO_CANCELABLE)
    FallaDatos.DisminucionMayorAExistencia -> campo(Campos.CANTIDAD, Mensajes.DISMINUCION_MAYOR)
    FallaDatos.CorteNoVigente -> mensaje(Mensajes.Campo.CORTE_PENDIENTE)
    FallaDatos.CategoriaConProductos -> mensaje(Mensajes.CATEGORIA_CON_PRODUCTOS)
    FallaDatos.EfectivoNoModificable -> mensaje(Mensajes.Campo.EFECTIVO_NO_MODIFICABLE)
    FallaDatos.TipoVentaNoModificable -> campo(Campos.TIPO_VENTA, Mensajes.Campo.TIPO_VENTA_CON_EXISTENCIA)
    FallaDatos.NoEncontrado, FallaDatos.CantidadInvalida -> mensaje(general)
    else -> mensaje(general)
}

/** Ejecuta la operación de datos y convierte fallas y errores inesperados en mensajes. */
internal suspend fun <T, R> ejecutar(
    general: String,
    campoNombre: String = Campos.NOMBRE,
    operacion: suspend () -> Resultado<T>,
    alTerminar: suspend (T) -> Hecho<R>,
): Resultado<Hecho<R>> {
    val r = try {
        operacion()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        return mensaje(general)
    }
    return when (r) {
        is Resultado.Exito -> Resultado.Exito(alTerminar(r.valor))
        is Resultado.Fallo -> traducir(r.falla, general, campoNombre)
    }
}
