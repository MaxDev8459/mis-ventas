package mx.ambulantpoint.misventas.domain

import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.domain.puertos.PuertoPerfil
import mx.ambulantpoint.misventas.domain.regla.FallaCampos
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.EditarPerfil
import mx.ambulantpoint.misventas.domain.usecase.FormularioRegistro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class PerfilesFalsos(var perfil: Perfil?) : PuertoPerfil {
    var guardado: Perfil? = null
    override suspend fun obtener() = perfil
    override suspend fun guardar(perfil: Perfil): Resultado<Perfil> { guardado = perfil; return Resultado.Exito(perfil) }
}

/** RF 1.6 — Editar perfil. */
class EditarPerfilTest {
    private val actual = Perfil("p", "Max", "Vasquez", "Arroyo", "7771234567", "Max Carlotas", "max@gmail.com", creadoEn = 0, actualizadoEn = 0)
    private val valido = FormularioRegistro(" Maximiliano ", "Vasquez", "Arroyo", "7779876543", "Max Carlotas y botana")

    @Test fun `guarda los cambios sin tocar el correo y muestra el mensaje del DFR`() = runTest {
        val puerto = PerfilesFalsos(actual)
        val r = EditarPerfil(puerto)(valido.copy(correo = "otro@gmail.com")) as Resultado.Exito
        assertEquals(Mensajes.PERFIL_ACTUALIZADO, r.valor.mensaje)
        val g = puerto.guardado!!
        assertEquals("Maximiliano", g.nombres)
        assertEquals("7779876543", g.telefono)
        assertEquals("Max Carlotas y botana", g.nombreNegocio)
        assertEquals("max@gmail.com", g.correo)
        assertEquals("p", g.id)
    }

    @Test fun `campos vacios y telefono no valido se marcan`() = runTest {
        val puerto = PerfilesFalsos(actual)
        val r = EditarPerfil(puerto)(FormularioRegistro("", "Vasquez", " ", "777", "")) as Resultado.Fallo
        val e = (r.falla as FallaCampos).errores
        assertEquals(Mensajes.Campo.OBLIGATORIO, e[Campos.NOMBRES])
        assertEquals(Mensajes.Campo.OBLIGATORIO, e[Campos.APELLIDO_MATERNO])
        assertEquals(Mensajes.Campo.OBLIGATORIO, e[Campos.NOMBRE_NEGOCIO])
        assertEquals(Mensajes.Campo.TELEFONO_NO_VALIDO, e[Campos.TELEFONO])
        assertNull(e[Campos.APELLIDO_PATERNO])
        assertNull(puerto.guardado)
    }
}
