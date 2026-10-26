package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.LectorNumeros
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.Validadores
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.puertos.PuertoInventario
import mx.ambulantpoint.misventas.domain.puertos.PuertoVentasRecientes
import mx.ambulantpoint.misventas.domain.regla.ReglaExistenciaBaja

/** Identificación mínima de un producto para inventario y avisos. */
data class ProductoRef(val id: String, val nombre: String, val tipoVenta: TipoVenta)

/** RF 4.5: se evalúa después de cada venta, ajuste o entrada. */
class EvaluarExistenciaBaja(private val ventas: PuertoVentasRecientes) {

    suspend fun esBaja(productoId: String, tipoVenta: TipoVenta, existenciaMilesimas: Long): Boolean {
        val r = ventas.ventasRecientes(productoId)
        return ReglaExistenciaBaja.esBaja(existenciaMilesimas, tipoVenta, r.dias, r.cantidadMilesimas)
    }

    /** "Quedan [existencia] [pzas/kg] de [producto]. Considere surtirlo", o null si no está baja. */
    suspend fun aviso(producto: ProductoRef, existenciaMilesimas: Long): String? =
        if (esBaja(producto.id, producto.tipoVenta, existenciaMilesimas)) {
            Mensajes.existenciaBaja(Formato.cantidad(existenciaMilesimas, producto.tipoVenta), producto.nombre)
        } else {
            null
        }
}

/** Resultado de un cambio de existencia: la nueva existencia ya formateada para el mensaje. */
data class CambioExistencia(val existenciaMilesimas: Long, val existenciaTexto: String)

/** RF 4.1. */
class RegistrarEntrada(
    private val inventario: PuertoInventario,
    private val existenciaBaja: EvaluarExistenciaBaja,
) {
    suspend operator fun invoke(producto: ProductoRef?, cantidad: String): Resultado<Hecho<CambioExistencia>> {
        val e = Errores()
        if (producto == null) e.si(Campos.NOMBRE, Mensajes.Campo.OBLIGATORIO)
        val milesimas = producto?.let { e.leer(Campos.CANTIDAD, LectorNumeros.cantidad(cantidad, it.tipoVenta)) }
        if (e.hay) return e.falla()
        val p = producto!!
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { inventario.registrarEntrada(p.id, milesimas!!) }) { nueva ->
            Hecho(
                CambioExistencia(nueva, Formato.cantidad(nueva, p.tipoVenta)),
                Mensajes.ENTRADA_REGISTRADA,
                listOfNotNull(existenciaBaja.aviso(p, nueva)),
            )
        }
    }
}

/** RF 4.2. */
class AjustarExistencia(
    private val inventario: PuertoInventario,
    private val existenciaBaja: EvaluarExistenciaBaja,
) {
    suspend operator fun invoke(
        producto: ProductoRef?,
        aumentar: Boolean?,
        cantidad: String,
        motivo: MotivoAjuste?,
        especificacion: String?,
    ): Resultado<Hecho<CambioExistencia>> {
        val e = Errores()
        if (producto == null) e.si(Campos.NOMBRE, Mensajes.Campo.OBLIGATORIO)
        if (aumentar == null) e.si(Campos.TIPO_VENTA, Mensajes.Campo.OBLIGATORIO)
        val milesimas = producto?.let { e.leer(Campos.CANTIDAD, LectorNumeros.cantidad(cantidad, it.tipoVenta)) }
        if (motivo == null) e.si(Campos.MOTIVO, Mensajes.Campo.OBLIGATORIO)
        if (motivo == MotivoAjuste.OTRO && Validadores.obligatorio(especificacion) != null) {
            e.si(Campos.ESPECIFICACION, Mensajes.Campo.ESPECIFIQUE_MOTIVO)
        }
        if (e.hay) return e.falla()
        val p = producto!!
        return ejecutar(
            Mensajes.Campo.NO_ENCONTRADO,
            operacion = { inventario.ajustar(p.id, aumentar!!, milesimas!!, motivo!!, especificacion) },
        ) { nueva ->
            Hecho(
                CambioExistencia(nueva, Formato.cantidad(nueva, p.tipoVenta)),
                Mensajes.AJUSTE_REGISTRADO,
                listOfNotNull(existenciaBaja.aviso(p, nueva)),
            )
        }
    }
}
