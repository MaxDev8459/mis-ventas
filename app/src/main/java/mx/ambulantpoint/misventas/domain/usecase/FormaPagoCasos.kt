package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.Validadores
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.domain.puertos.PuertoFormasPago
import mx.ambulantpoint.misventas.domain.puertos.PuertoDatosTransferencia
import mx.ambulantpoint.misventas.domain.regla.DatosTransferencia
import mx.ambulantpoint.misventas.domain.regla.ReglaTransferencia

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

/**
 * Solicitud de cambio SC-01 — Datos para recibir transferencias (una sola cuenta).
 * - Los tres campos son obligatorios.
 * - La CLABE se captura con o sin espacios; debe tener 18 dígitos y un dígito verificador válido.
 * - [borrar] quita los datos (el cobro con transferencia deja de ofrecer la ventana).
 */
class GuardarDatosTransferencia(private val repo: PuertoDatosTransferencia) {
    suspend operator fun invoke(clabe: String, beneficiario: String, banco: String): Resultado<Hecho<DatosTransferencia>> {
        val digitos = ReglaTransferencia.soloDigitos(clabe)
        val e = Errores()
        e.si(
            Campos.CLABE,
            when {
                clabe.isBlank() -> Mensajes.Campo.OBLIGATORIO
                digitos.length != clabe.count { !it.isWhitespace() && it != '-' } -> Mensajes.Campo.CLABE_SOLO_NUMEROS
                digitos.length != ReglaTransferencia.LARGO_CLABE -> Mensajes.Campo.CLABE_INCOMPLETA
                !ReglaTransferencia.clabeValida(digitos) -> Mensajes.Campo.CLABE_NO_VALIDA
                else -> null
            },
        )
        e.si(Campos.BENEFICIARIO, Validadores.obligatorio(beneficiario))
        e.si(Campos.BANCO, Validadores.obligatorio(banco))
        if (e.hay) return e.falla()
        val datos = DatosTransferencia(digitos, beneficiario.trim(), banco.trim())
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { repo.guardarDatosTransferencia(datos) }) {
            Hecho(datos, Mensajes.DATOS_TRANSFERENCIA_GUARDADOS)
        }
    }

    suspend fun borrar(): Resultado<Hecho<Unit>> =
        ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { repo.guardarDatosTransferencia(null) }) {
            Hecho(Unit, Mensajes.DATOS_TRANSFERENCIA_BORRADOS)
        }
}
