package mx.ambulantpoint.misventas.domain

import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.regla.AvisoApartado
import mx.ambulantpoint.misventas.domain.regla.MomentoApartado
import mx.ambulantpoint.misventas.domain.regla.ProductoApartado
import mx.ambulantpoint.misventas.domain.regla.ReglaApartado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Fase 4: apartados (docs/10-ESPEC-APARTADOS.md). */
class ApartadoTest {
    private val mx = ZoneId.of("America/Mexico_City")
    private fun ms(texto: String) = LocalDateTime.parse(texto).atZone(mx).toInstant().toEpochMilli()

    // ── Existencia disponible ──

    @Test fun `disponible es total menos apartado`() = assertEquals(2_000L, ReglaApartado.disponible(5_000, 3_000))
    @Test fun `sin apartados todo esta disponible`() = assertEquals(5_000L, ReglaApartado.disponible(5_000, 0))
    @Test fun `disponible nunca es negativo`() = assertEquals(0L, ReglaApartado.disponible(2_000, 3_000))

    // ── Merma o ajuste (respuesta 8: no se permite por debajo de lo apartado) ──

    @Test fun `ajuste que deja justo lo apartado se permite`() =
        assertNull(ReglaApartado.validarDisminucion(5_000, 2_000, 3_000, "Carlota", TipoVenta.UNIDAD))

    @Test fun `ajuste por debajo de lo apartado se rechaza`() = assertEquals(
        "Hay 3 pzas de Carlota en apartados. Cancele o cambie el apartado primero",
        ReglaApartado.validarDisminucion(5_000, 2_001, 3_000, "Carlota", TipoVenta.UNIDAD),
    )

    @Test fun `sin apartados el ajuste no se limita aqui`() =
        assertNull(ReglaApartado.validarDisminucion(5_000, 5_000, 0, "Carlota", TipoVenta.UNIDAD))

    // ── Validaciones ──

    @Test fun `lugar obligatorio y hasta 60`() {
        assertEquals("Este campo es obligatorio", ReglaApartado.validarLugar("   "))
        assertEquals("El lugar debe tener hasta 60 caracteres", ReglaApartado.validarLugar("x".repeat(61)))
        assertNull(ReglaApartado.validarLugar("x".repeat(60)))
    }

    @Test fun `entrega obligatoria y no en el pasado`() {
        val ahora = ms("2026-11-24T10:00")
        assertEquals("Este campo es obligatorio", ReglaApartado.validarEntrega(null, ahora))
        assertEquals("La fecha y hora de entrega ya pasaron", ReglaApartado.validarEntrega(ahora, ahora))
        assertNull(ReglaApartado.validarEntrega(ms("2026-11-24T10:01"), ahora))
    }

    // ── Liberar al cerrar la caja (respuesta 5) ──

    private val abiertaMartes = ms("2026-11-24T08:30")

    @Test fun `se libera el de ese dia aunque la hora sea despues del cierre`() =
        assertTrue(ReglaApartado.seLibera(ms("2026-11-24T23:59"), abiertaMartes, mx))

    @Test fun `se libera el de dias anteriores`() =
        assertTrue(ReglaApartado.seLibera(ms("2026-11-20T11:00"), abiertaMartes, mx))

    @Test fun `no se libera el del dia siguiente`() =
        assertFalse(ReglaApartado.seLibera(ms("2026-11-25T00:00"), abiertaMartes, mx))

    @Test fun `el dia es el de la apertura aunque la caja se cierre despues de medianoche`() {
        // Abierta el martes 23:00; a la 01:00 del miércoles se cierra: solo se liberan los del martes y antes.
        val abierta = ms("2026-11-24T23:00")
        assertTrue(ReglaApartado.seLibera(ms("2026-11-24T23:30"), abierta, mx))
        assertFalse(ReglaApartado.seLibera(ms("2026-11-25T09:00"), abierta, mx))
    }

    // ── Notificaciones (respuesta 7) ──

    @Test fun `dos avisos 15 minutos antes y a la hora exacta`() {
        val entrega = ms("2026-11-24T11:00")
        val avisos = ReglaApartado.avisos(entrega, ms("2026-11-23T20:00"))
        assertEquals(
            listOf(AvisoApartado.QUINCE_MINUTOS to ms("2026-11-24T10:45"), AvisoApartado.HORA_EXACTA to entrega),
            avisos,
        )
    }

    @Test fun `con menos de 15 minutos solo queda el de la hora exacta`() {
        val entrega = ms("2026-11-24T11:00")
        assertEquals(listOf(AvisoApartado.HORA_EXACTA to entrega), ReglaApartado.avisos(entrega, ms("2026-11-24T10:50")))
    }

    @Test fun `si ya paso la hora no hay avisos`() =
        assertTrue(ReglaApartado.avisos(ms("2026-11-24T11:00"), ms("2026-11-24T11:00")).isEmpty())

    private val productos = listOf(
        ProductoApartado("Carlota", TipoVenta.UNIDAD, 3_000),
        ProductoApartado("Queso", TipoVenta.PESO, 500),
    )

    @Test fun `texto del aviso de 15 minutos con todos los datos`() = assertEquals(
        "En 15 minutos tienes que entregar a Juan (tel. 7341234567): 3 pzas Carlota, 0.500 kg Queso en Parque central a las 11:00. Total \$160.00",
        ReglaApartado.textoAviso(AvisoApartado.QUINCE_MINUTOS, "Juan", productos, "Parque central", "11:00", 16_000, "7341234567"),
    )

    @Test fun `texto del aviso a la hora exacta sin telefono`() = assertEquals(
        "Es hora de entregar a Juan: 3 pzas Carlota, 0.500 kg Queso en Parque central a las 11:00. Total \$160.00",
        ReglaApartado.textoAviso(AvisoApartado.HORA_EXACTA, "Juan", productos, "Parque central", "11:00", 16_000),
    )

    // ── Cómo se ve en la lista ──

    @Test fun `atrasado hoy o despues`() {
        val ahora = ms("2026-11-24T12:00")
        assertEquals(MomentoApartado.ATRASADO, ReglaApartado.momento(ms("2026-11-24T11:00"), ahora, mx))
        assertEquals(MomentoApartado.HOY, ReglaApartado.momento(ms("2026-11-24T18:00"), ahora, mx))
        assertEquals(MomentoApartado.DESPUES, ReglaApartado.momento(ms("2026-11-25T09:00"), ahora, mx))
    }
}
