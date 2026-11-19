package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.Validadores
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.domain.puertos.PuertoFormasPago

/**
 * RF 3.5 — Configurar formas de pago: agregar ([id] = null), renombrar, activar y desactivar.
 * - Campo vacío → alerta en el campo nombre.
 * - Nombre repetido → alerta en el campo nombre (índice único en la capa de datos).
 * - "Efectivo" no se renombra ni se desactiva (la capa de datos lo rechaza).
 * Las formas de pago no se eliminan: solo cambian de estado.
 */
class GuardarFormaPago(private val formas: PuertoFormasPago) {
    suspend operator fun invoke(id: String?, nombre: String, activa: Boolean): Resultado<Hecho<FormaPago>> {
        Validadores.obligatorio(nombre)?.let { return campo(Campos.NOMBRE, it) }
        return ejecutar(
            Mensajes.Campo.NO_ENCONTRADO,
            operacion = { if (id == null) formas.crear(nombre, activa) else formas.actualizar(id, nombre, activa) },
        ) { Hecho(it, Mensajes.FORMA_PAGO_GUARDADA) }
    }
}
