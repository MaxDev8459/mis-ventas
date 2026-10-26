package mx.ambulantpoint.misventas.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.auth.Hasheador
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.domain.puertos.DatosRegistro
import mx.ambulantpoint.misventas.domain.puertos.EstadoSesion
import mx.ambulantpoint.misventas.domain.puertos.PuertoAuth
import mx.ambulantpoint.misventas.domain.regla.FallaCampos
import mx.ambulantpoint.misventas.domain.regla.FallaMensaje
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.CerrarSesion
import mx.ambulantpoint.misventas.domain.usecase.FormularioRegistro
import mx.ambulantpoint.misventas.domain.usecase.IniciarSesion
import mx.ambulantpoint.misventas.domain.usecase.RegistrarVendedor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HasheadorTest {
    @Test fun `la contrasena no se guarda en texto plano y se verifica`() {
        val guardado = Hasheador.hashear("ventas2026", iteraciones = 1_000)
        assertFalse(guardado.contains("ventas2026"))
        assertTrue(Hasheador.verificar("ventas2026", guardado))
        assertFalse(Hasheador.verificar("ventas2025", guardado))
    }

    @Test fun `la sal cambia el hash aunque la contrasena sea igual`() {
        assertNotEquals(Hasheador.hashear("ventas2026", 1_000), Hasheador.hashear("ventas2026", 1_000))
    }

    @Test fun `un texto guardado danado no se acepta`() {
        assertFalse(Hasheador.verificar("x", "basura"))
    }
}

private class AuthFalso(private val falla: FallaDatos? = null) : PuertoAuth {
    override val estado = MutableStateFlow(EstadoSesion.SIN_CUENTA)
    override suspend fun registrar(datos: DatosRegistro): Resultado<Perfil> =
        falla?.let { Resultado.Fallo(it) }
            ?: Resultado.Exito(Perfil("p", datos.nombres, "", "", "", "", datos.correo, creadoEn = 0, actualizadoEn = 0))
    override suspend fun iniciarSesion(correo: String, contrasena: String): Resultado<Unit> =
        falla?.let { Resultado.Fallo(it) } ?: Resultado.Exito(Unit)
    override suspend fun cerrarSesion(): Resultado<Unit> = falla?.let { Resultado.Fallo(it) } ?: Resultado.Exito(Unit)
}

class AuthCasosTest {
    private val valido = FormularioRegistro("Max", "Vasquez", "Arroyo", "7771234567", "Max Carlotas", "max@gmail.com", "ventas2026", "ventas2026", true)

    @Test fun `registro exitoso muestra el mensaje del DFR`() = runTest {
        assertEquals(Mensajes.CUENTA_REGISTRADA, (RegistrarVendedor(AuthFalso())(valido) as Resultado.Exito).valor.mensaje)
    }

    @Test fun `correo ya registrado se marca en el campo correo`() = runTest {
        val r = RegistrarVendedor(AuthFalso(FallaDatos.CorreoYaRegistrado))(valido) as Resultado.Fallo
        assertEquals(Mensajes.Campo.CORREO_YA_REGISTRADO, (r.falla as FallaCampos).errores[Campos.CORREO])
    }

    @Test fun `registro que no se completa usa el mensaje general`() = runTest {
        val r = RegistrarVendedor(AuthFalso(FallaDatos.YaHayOtraCuenta))(valido) as Resultado.Fallo
        assertEquals(Mensajes.CUENTA_NO_REGISTRADA, (r.falla as FallaMensaje).mensaje)
    }

    @Test fun `credenciales incorrectas`() = runTest {
        val r = IniciarSesion(AuthFalso(FallaDatos.CredencialesIncorrectas))("max@gmail.com", "mala1234") as Resultado.Fallo
        assertEquals(Mensajes.CREDENCIALES_INCORRECTAS, (r.falla as FallaMensaje).mensaje)
    }

    @Test fun `no cierra sesion con pendientes`() = runTest {
        val r = CerrarSesion(AuthFalso(FallaDatos.PendientesDeSincronizar))() as Resultado.Fallo
        assertEquals(Mensajes.PENDIENTES_AL_CERRAR_SESION, (r.falla as FallaMensaje).mensaje)
    }
}
