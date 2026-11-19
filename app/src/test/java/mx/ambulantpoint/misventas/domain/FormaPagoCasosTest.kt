package mx.ambulantpoint.misventas.domain

import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.domain.puertos.PuertoFormasPago
import mx.ambulantpoint.misventas.domain.regla.FallaCampos
import mx.ambulantpoint.misventas.domain.regla.FallaMensaje
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.GuardarFormaPago
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Imita la capa de datos: nombre único (sin distinguir mayúsculas) y "Efectivo" protegido. */
private class FormasFalsas : PuertoFormasPago {
    val formas = mutableListOf(
        FormaPago("ef", "Efectivo", "efectivo", esEfectivo = true, creadoEn = 0, actualizadoEn = 0),
        FormaPago("tr", "Transferencia", "transferencia", creadoEn = 0, actualizadoEn = 0),
    )
    var llamadas = 0

    private fun repetido(nombre: String, excepto: String?) =
        formas.any { it.id != excepto && it.nombreNormalizado == nombre.trim().lowercase() }

    override suspend fun crear(nombre: String, activa: Boolean): Resultado<FormaPago> {
        llamadas++
        if (repetido(nombre, null)) return Resultado.Fallo(FallaDatos.NombreDuplicado)
        val nueva = FormaPago("n${formas.size}", nombre.trim(), nombre.trim().lowercase(), activa, creadoEn = 0, actualizadoEn = 0)
        formas += nueva
        return Resultado.Exito(nueva)
    }

    override suspend fun actualizar(id: String, nombre: String, activa: Boolean): Resultado<FormaPago> {
        llamadas++
        val i = formas.indexOfFirst { it.id == id }
        if (i < 0) return Resultado.Fallo(FallaDatos.NoEncontrado)
        val actual = formas[i]
        if (actual.esEfectivo && (!activa || nombre.trim().lowercase() != actual.nombreNormalizado)) {
            return Resultado.Fallo(FallaDatos.EfectivoNoModificable)
        }
        if (repetido(nombre, id)) return Resultado.Fallo(FallaDatos.NombreDuplicado)
        val nueva = actual.copy(nombre = nombre.trim(), nombreNormalizado = nombre.trim().lowercase(), activa = activa)
        formas[i] = nueva
        return Resultado.Exito(nueva)
    }
}

private fun Resultado<*>.campos(): Map<String, String> = ((this as Resultado.Fallo).falla as FallaCampos).errores
private fun Resultado<*>.mensajeFalla(): String = ((this as Resultado.Fallo).falla as FallaMensaje).mensaje

/** RF 3.5 — Configurar formas de pago. */
class GuardarFormaPagoTest {
    private val repo = FormasFalsas()
    private val guardar = GuardarFormaPago(repo)

    @Test fun `nombre vacio marca el campo sin llamar a la capa de datos`() = runTest {
        val r = guardar(null, "   ", activa = true)
        assertEquals(Mensajes.Campo.OBLIGATORIO, r.campos()[Campos.NOMBRE])
        assertEquals(0, repo.llamadas)
    }

    @Test fun `agregar muestra el mensaje del DFR`() = runTest {
        val r = guardar(null, " Tarjeta ", activa = true) as Resultado.Exito
        assertEquals(Mensajes.FORMA_PAGO_GUARDADA, r.valor.mensaje)
        assertEquals("Tarjeta", r.valor.valor.nombre)
    }

    @Test fun `nombre ya registrado se marca en el campo nombre`() = runTest {
        val r = guardar(null, "TRANSFERENCIA", activa = true)
        assertEquals(Mensajes.Campo.NOMBRE_YA_REGISTRADO, r.campos()[Campos.NOMBRE])
    }

    @Test fun `renombrar a un nombre existente tambien se rechaza`() = runTest {
        val r = guardar("tr", "Efectivo", activa = true)
        assertEquals(Mensajes.Campo.NOMBRE_YA_REGISTRADO, r.campos()[Campos.NOMBRE])
    }

    @Test fun `desactivar una forma adicional`() = runTest {
        val r = guardar("tr", "Transferencia", activa = false) as Resultado.Exito
        assertFalse(r.valor.valor.activa)
        assertEquals(Mensajes.FORMA_PAGO_GUARDADA, r.valor.mensaje)
    }

    @Test fun `efectivo no se desactiva`() = runTest {
        val r = guardar("ef", "Efectivo", activa = false)
        assertEquals(Mensajes.Campo.EFECTIVO_NO_MODIFICABLE, r.mensajeFalla())
    }

    @Test fun `efectivo no se renombra`() = runTest {
        val r = guardar("ef", "Contado", activa = true)
        assertEquals(Mensajes.Campo.EFECTIVO_NO_MODIFICABLE, r.mensajeFalla())
        assertNull(repo.formas.firstOrNull { it.nombre == "Contado" })
    }
}
