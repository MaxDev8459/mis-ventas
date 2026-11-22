package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.entity.Apartado
import mx.ambulantpoint.misventas.data.repository.ApartadoRepo
import mx.ambulantpoint.misventas.data.repository.DatosApartado
import mx.ambulantpoint.misventas.data.repository.LineaVenta
import mx.ambulantpoint.misventas.domain.regla.ReglaApartado
import mx.ambulantpoint.misventas.domain.regla.ReglaFiado
import mx.ambulantpoint.misventas.domain.venta.Carrito

/** Campos del formulario del apartado. */
object CamposApartado {
    const val NOMBRE = Campos.NOMBRE
    const val TELEFONO = Campos.TELEFONO
    const val LUGAR = "lugar"
    const val ENTREGA = "entrega"
}

/** Valida los datos de entrega como campos (para mostrarlos debajo de cada uno). */
private fun validar(entregaEn: Long?, ahora: Long, nombre: String, telefono: String, lugar: String): Errores {
    val e = Errores()
    e.si(CamposApartado.NOMBRE, ReglaFiado.validarNombre(nombre))
    e.si(CamposApartado.TELEFONO, ReglaFiado.validarTelefono(telefono))
    e.si(CamposApartado.LUGAR, ReglaApartado.validarLugar(lugar))
    e.si(CamposApartado.ENTREGA, ReglaApartado.validarEntrega(entregaEn, ahora))
    return e
}

/** Apartar desde Vender: solo productos del catálogo. */
class Apartar(private val repo: ApartadoRepo, private val ahora: () -> Long) {
    suspend operator fun invoke(
        carrito: Carrito,
        nombre: String,
        telefono: String,
        lugar: String,
        entregaEn: Long?,
    ): Resultado<Hecho<Apartado>> {
        if (carrito.estaVacio) return mensaje(Mensajes.AGREGUE_UN_PRODUCTO)
        if (carrito.tieneLibres) return mensaje(Mensajes.Apartado.NO_APARTAR_VARIOS)
        val e = validar(entregaEn, ahora(), nombre, telefono, lugar)
        if (e.hay) return e.falla()
        val lineas = carrito.renglones.map { LineaVenta(it.producto.id, it.cantidadMilesimas) }
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = {
            repo.apartar(DatosApartado(nombre, telefono, lugar, entregaEn!!), lineas)
        }) { Hecho(it, Mensajes.Apartado.APARTADO_GUARDADO) }
    }
}

class EditarApartado(private val repo: ApartadoRepo, private val ahora: () -> Long) {
    suspend operator fun invoke(id: String, nombre: String, telefono: String, lugar: String, entregaEn: Long?): Resultado<Hecho<Apartado>> {
        val e = validar(entregaEn, ahora(), nombre, telefono, lugar)
        if (e.hay) return e.falla()
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = {
            repo.editar(id, DatosApartado(nombre, telefono, lugar, entregaEn!!))
        }) { Hecho(it, Mensajes.Apartado.APARTADO_ACTUALIZADO) }
    }
}

class CancelarApartado(private val repo: ApartadoRepo) {
    suspend operator fun invoke(id: String): Resultado<Hecho<Unit>> =
        ejecutar(Mensajes.Apartado.NO_PENDIENTE, operacion = { repo.cancelar(id) }) { Hecho(Unit, Mensajes.Apartado.APARTADO_CANCELADO) }
}
