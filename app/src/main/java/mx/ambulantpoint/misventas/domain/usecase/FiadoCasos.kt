package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.core.LectorNumeros
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.entity.Abono
import mx.ambulantpoint.misventas.data.local.entity.Cliente
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.repository.FiadoRepo
import mx.ambulantpoint.misventas.domain.regla.ReglaFiado

/** Fiado (Fase 3): alta y edición de clientes. Nombre y teléfono se validan como campos. */
class GuardarCliente(private val repo: FiadoRepo) {
    suspend operator fun invoke(id: String?, nombre: String, telefono: String): Resultado<Hecho<Cliente>> {
        val e = Errores()
        e.si(Campos.NOMBRE, ReglaFiado.validarNombre(nombre))
        e.si(Campos.TELEFONO, ReglaFiado.validarTelefono(telefono))
        if (e.hay) return e.falla()
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { repo.guardarCliente(id, nombre, telefono) }) {
            Hecho(it, Mensajes.Fiado.CLIENTE_GUARDADO)
        }
    }
}

/** Fiado: abono con su forma de pago; requiere la caja abierta y no puede pasar el saldo. */
class RegistrarAbono(private val repo: FiadoRepo) {
    suspend operator fun invoke(clienteId: String, monto: String, forma: FormaPago?): Resultado<Hecho<Abono>> {
        val e = Errores()
        val centavos = e.leer(Campos.CANTIDAD, LectorNumeros.monto(monto))
        if (forma == null) e.si(Campos.FORMA_PAGO, Mensajes.Campo.OBLIGATORIO)
        if (e.hay) return e.falla()
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { repo.registrarAbono(clienteId, centavos!!, forma!!.id) }) {
            Hecho(it, Mensajes.Fiado.ABONO_REGISTRADO)
        }
    }
}

class CancelarAbono(private val repo: FiadoRepo) {
    suspend operator fun invoke(abonoId: String): Resultado<Hecho<Unit>> =
        ejecutar(Mensajes.Fiado.ABONO_NO_CANCELABLE, operacion = { repo.cancelarAbono(abonoId) }) {
            Hecho(Unit, Mensajes.Fiado.ABONO_CANCELADO)
        }
}

/** Monto de aviso de saldo alto (mayor a cero). */
class GuardarAvisoSaldo(private val repo: FiadoRepo) {
    suspend operator fun invoke(monto: String): Resultado<Hecho<Unit>> {
        val e = Errores()
        val centavos = e.leer(Campos.CANTIDAD, LectorNumeros.monto(monto))
        if (e.hay) return e.falla()
        return ejecutar(Mensajes.Campo.MONTO_NO_VALIDO, operacion = { repo.guardarAviso(centavos!!) }) {
            Hecho(Unit, Mensajes.Fiado.AVISO_GUARDADO)
        }
    }
}
