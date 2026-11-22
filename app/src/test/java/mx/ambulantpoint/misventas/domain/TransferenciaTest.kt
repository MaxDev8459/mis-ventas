package mx.ambulantpoint.misventas.domain

import kotlinx.coroutines.runBlocking
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.entity.Configuracion
import mx.ambulantpoint.misventas.data.repository.datosTransferencia
import mx.ambulantpoint.misventas.data.sync.aConfiguracion
import mx.ambulantpoint.misventas.data.sync.aDocumento
import mx.ambulantpoint.misventas.domain.puertos.PuertoDatosTransferencia
import mx.ambulantpoint.misventas.domain.regla.DatosTransferencia
import mx.ambulantpoint.misventas.domain.regla.FallaCampos
import mx.ambulantpoint.misventas.domain.regla.ReglaTransferencia
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.GuardarDatosTransferencia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SC-01 — CLABE: dígito verificador de Banxico (pesos 3, 7, 1). */
class ReglaTransferenciaTest {
    /** Ejemplo público de CLABE válida (clave 032 = IXE, que no está en la lista de sugerencias). */
    private val valida = "032180000118359719"

    @Test fun `clabe valida`() = assertTrue(ReglaTransferencia.clabeValida(valida))

    @Test fun `un digito cambiado la invalida`() = assertFalse(ReglaTransferencia.clabeValida("032180000118359718"))

    @Test fun `digitos transpuestos la invalidan`() = assertFalse(ReglaTransferencia.clabeValida("302180000118359719"))

    @Test fun `largo distinto de 18 no es valida`() {
        assertFalse(ReglaTransferencia.clabeValida(valida.dropLast(1)))
        assertFalse(ReglaTransferencia.clabeValida(valida + "0"))
    }

    @Test fun `digito verificador calculado`() = assertEquals(9, ReglaTransferencia.digitoVerificador(valida.take(17)))

    @Test fun `banco sugerido por la clave`() {
        assertEquals("BanBajío", ReglaTransferencia.bancoSugerido("030180000118359719"))
        assertEquals("BBVA", ReglaTransferencia.bancoSugerido("012"))
        assertEquals("Nu México", ReglaTransferencia.bancoSugerido("638180"))
        assertNull(ReglaTransferencia.bancoSugerido(valida))
        assertNull(ReglaTransferencia.bancoSugerido("01"))
    }

    @Test fun `clabe en grupos de cuatro`() = assertEquals("0321 8000 0118 3597 19", ReglaTransferencia.clabeEnGrupos(valida))

    @Test fun `formas de pago de transferencia por nombre`() {
        assertTrue(ReglaTransferencia.esTransferencia("Transferencia"))
        assertTrue(ReglaTransferencia.esTransferencia(" transferencia SPEI "))
        assertTrue(ReglaTransferencia.esTransferencia("SPEI"))
        assertFalse(ReglaTransferencia.esTransferencia("Tarjeta"))
        assertFalse(ReglaTransferencia.esTransferencia("Efectivo"))
    }

    @Test fun `texto para compartir con el total y la clabe sin espacios`() {
        val texto = ReglaTransferencia.textoParaCompartir(DatosTransferencia(valida, "Max Carlotas", "BanBajío"), 8_650)
        assertEquals(
            "Datos para transferencia\nBanco: BanBajío\nBeneficiario: Max Carlotas\nCLABE: $valida\nTotal: \$86.50",
            texto,
        )
    }
}

/** SC-01 — Guardar los datos: validaciones y lo que llega al repositorio. */
class GuardarDatosTransferenciaTest {
    private class RepoFalso : PuertoDatosTransferencia {
        var guardado: DatosTransferencia? = DatosTransferencia("x", "x", "x")
        override suspend fun guardarDatosTransferencia(datos: DatosTransferencia?): Resultado<Unit> {
            guardado = datos
            return Resultado.Exito(Unit)
        }
    }

    private fun errores(r: Resultado<*>): Map<String, String> = ((r as Resultado.Fallo).falla as FallaCampos).errores

    @Test fun `guarda la clabe solo con digitos y los textos sin espacios de sobra`() = runBlocking {
        val repo = RepoFalso()
        val r = GuardarDatosTransferencia(repo)("0321 8000 0118 3597 19", "  Max Carlotas ", " BanBajío ")
        assertTrue(r is Resultado.Exito)
        assertEquals(Mensajes.DATOS_TRANSFERENCIA_GUARDADOS, (r as Resultado.Exito).valor.mensaje)
        assertEquals(DatosTransferencia("032180000118359719", "Max Carlotas", "BanBajío"), repo.guardado)
    }

    @Test fun `campos vacios marcan los tres`() = runBlocking {
        val r = GuardarDatosTransferencia(RepoFalso())("", " ", "")
        assertEquals(
            mapOf(Campos.CLABE to Mensajes.Campo.OBLIGATORIO, Campos.BENEFICIARIO to Mensajes.Campo.OBLIGATORIO, Campos.BANCO to Mensajes.Campo.OBLIGATORIO),
            errores(r),
        )
    }

    @Test fun `clabe incompleta o con letras`() = runBlocking {
        assertEquals(Mensajes.Campo.CLABE_INCOMPLETA, errores(GuardarDatosTransferencia(RepoFalso())("03218000011835971", "A", "B"))[Campos.CLABE])
        assertEquals(Mensajes.Campo.CLABE_SOLO_NUMEROS, errores(GuardarDatosTransferencia(RepoFalso())("03218000011835971A", "A", "B"))[Campos.CLABE])
    }

    @Test fun `clabe con digito verificador incorrecto`() = runBlocking {
        assertEquals(Mensajes.Campo.CLABE_NO_VALIDA, errores(GuardarDatosTransferencia(RepoFalso())("032180000118359718", "A", "B"))[Campos.CLABE])
    }

    @Test fun `borrar deja los datos en null`() = runBlocking {
        val repo = RepoFalso()
        val r = GuardarDatosTransferencia(repo).borrar()
        assertTrue(r is Resultado.Exito)
        assertNull(repo.guardado)
    }
}

/** SC-01 — Los datos viajan con la configuración (RF 7.1 / 7.4). */
class ConfiguracionTransferenciaTest {
    @Test fun `ida y vuelta a Firestore`() {
        val c = Configuracion(descuentosHabilitados = true, clabe = "032180000118359719", beneficiario = "Max", banco = "BanBajío", actualizadoEn = 5)
        assertEquals(c.copy(pendienteSync = false), aConfiguracion(c.aDocumento()))
    }

    @Test fun `configuracion de antes del cambio no tiene datos`() {
        assertNull(aConfiguracion(mapOf("id" to Configuracion.ID_UNICA, "descuentosHabilitados" to false, "actualizadoEn" to 1L)).datosTransferencia())
        assertNull((null as Configuracion?).datosTransferencia())
    }

    @Test fun `datos incompletos no se ofrecen`() =
        assertNull(Configuracion(clabe = "032180000118359719", beneficiario = "Max", banco = " ", actualizadoEn = 1).datosTransferencia())
}
