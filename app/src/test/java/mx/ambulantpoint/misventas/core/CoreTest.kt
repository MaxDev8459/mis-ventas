package mx.ambulantpoint.misventas.core

import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidadoresTest {
    @Test fun `correo con formato valido`() {
        assertNull(Validadores.correo("max.carlotas@gmail.com"))
        assertEquals(Mensajes.Campo.CORREO_NO_VALIDO, Validadores.correo("max@"))
        assertEquals(Mensajes.Campo.CORREO_NO_VALIDO, Validadores.correo("max carlotas@gmail.com"))
        assertEquals(Mensajes.Campo.OBLIGATORIO, Validadores.correo("  "))
    }

    @Test fun `telefono exactamente 10 digitos`() {
        assertNull(Validadores.telefono("7771234567"))
        assertEquals(Mensajes.Campo.TELEFONO_NO_VALIDO, Validadores.telefono("777123456"))
        assertEquals(Mensajes.Campo.TELEFONO_NO_VALIDO, Validadores.telefono("77712345678"))
        assertEquals(Mensajes.Campo.TELEFONO_NO_VALIDO, Validadores.telefono("777-123-456"))
    }

    @Test fun `contrasena minimo 8 con letra y numero`() {
        assertNull(Validadores.contrasena("ventas2026"))
        assertEquals(Mensajes.Campo.CONTRASENA_NO_VALIDA, Validadores.contrasena("venta26"))
        assertEquals(Mensajes.Campo.CONTRASENA_NO_VALIDA, Validadores.contrasena("soloLetras"))
        assertEquals(Mensajes.Campo.CONTRASENA_NO_VALIDA, Validadores.contrasena("12345678"))
    }

    @Test fun `contrasenas deben coincidir`() {
        assertNull(Validadores.confirmarContrasena("ventas2026", "ventas2026"))
        assertEquals(Mensajes.Campo.CONTRASENAS_NO_COINCIDEN, Validadores.confirmarContrasena("ventas2026", "ventas2025"))
    }
}

class LectorNumerosTest {
    private fun valor(l: Lectura) = (l as Lectura.Valor).valor
    private fun error(l: Lectura) = (l as Lectura.Error).mensaje

    @Test fun `montos con hasta dos decimales a centavos`() {
        assertEquals(1250L, valor(LectorNumeros.monto("12.5")))
        assertEquals(1250L, valor(LectorNumeros.monto("12,5")))
        assertEquals(123450L, valor(LectorNumeros.monto("$1,234.50")))
        assertEquals(123400L, valor(LectorNumeros.monto("1,234")))
        assertEquals(Mensajes.Campo.MONTO_NO_VALIDO, error(LectorNumeros.monto("12.505")))
        assertEquals(Mensajes.Campo.MONTO_NO_VALIDO, error(LectorNumeros.monto("abc")))
        assertEquals(Mensajes.Campo.MONTO_NO_VALIDO, error(LectorNumeros.monto("-5")))
    }

    @Test fun `fondo de caja admite cero pero precio no`() {
        assertEquals(0L, valor(LectorNumeros.monto("0", permitirCero = true)))
        assertEquals(Mensajes.Campo.PRECIO_NO_VALIDO, error(LectorNumeros.precio("0")))
        assertEquals(Mensajes.Campo.OBLIGATORIO, error(LectorNumeros.precio("")))
    }

    @Test fun `cantidad por unidad debe ser entera`() {
        assertEquals(3000L, valor(LectorNumeros.cantidad("3", TipoVenta.UNIDAD)))
        assertEquals(Mensajes.Campo.CANTIDAD_ENTERA, error(LectorNumeros.cantidad("1.5", TipoVenta.UNIDAD)))
        assertEquals(Mensajes.Campo.CANTIDAD_MAYOR_CERO, error(LectorNumeros.cantidad("0", TipoVenta.UNIDAD)))
        assertEquals(0L, valor(LectorNumeros.cantidad("0", TipoVenta.UNIDAD, permitirCero = true)))
    }

    @Test fun `cantidad por peso admite hasta tres decimales`() {
        assertEquals(250L, valor(LectorNumeros.cantidad("0.25", TipoVenta.PESO)))
        assertEquals(1505L, valor(LectorNumeros.cantidad("1.505", TipoVenta.PESO)))
        assertEquals(Mensajes.Campo.CANTIDAD_KG, error(LectorNumeros.cantidad("0.2505", TipoVenta.PESO)))
    }
}

class FormatoTest {
    @Test fun `moneda con formato 0,000_00`() {
        assertEquals("$1,234.56", Formato.moneda(123456))
        assertEquals("$0.05", Formato.moneda(5))
        assertEquals("$1,000,000.00", Formato.moneda(100_000_000))
        assertEquals("-$10.00", Formato.moneda(-1000))
    }

    @Test fun `cantidades con su unidad`() {
        assertEquals("3 pzas", Formato.cantidad(3000, TipoVenta.UNIDAD))
        assertEquals("0.250 kg", Formato.cantidad(250, TipoVenta.PESO))
        assertEquals("1,200 pzas", Formato.cantidad(1_200_000, TipoVenta.UNIDAD))
        assertEquals("−0.250 kg", Formato.cantidadConSigno(-250, TipoVenta.PESO))
        assertEquals("+5 pzas", Formato.cantidadConSigno(5000, TipoVenta.UNIDAD))
    }
}
