package mx.ambulantpoint.misventas.domain

import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.CorteFormaPago
import mx.ambulantpoint.misventas.data.local.entity.EstadoCaja
import mx.ambulantpoint.misventas.data.local.entity.EstadoVenta
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.MovimientoInventario
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.TipoDescuento
import mx.ambulantpoint.misventas.data.local.entity.TipoMovimiento
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.local.entity.Venta
import mx.ambulantpoint.misventas.data.sync.CAMPO_IMAGEN
import mx.ambulantpoint.misventas.data.sync.aCaja
import mx.ambulantpoint.misventas.data.sync.aCorteFormaPago
import mx.ambulantpoint.misventas.data.sync.aDocumento
import mx.ambulantpoint.misventas.data.sync.aMovimiento
import mx.ambulantpoint.misventas.data.sync.aProducto
import mx.ambulantpoint.misventas.data.sync.aVenta
import mx.ambulantpoint.misventas.domain.regla.AccionSesion
import mx.ambulantpoint.misventas.domain.regla.DecisionRecuperacion
import mx.ambulantpoint.misventas.domain.regla.ReglaRecuperacion
import mx.ambulantpoint.misventas.domain.regla.ReglaSesionUnica
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RF 7.4 — cuándo se reemplaza la información del teléfono por la de la cuenta. */
class DecisionRecuperacionTest {
    @Test fun `telefono nuevo sin datos recupera`() =
        assertEquals(DecisionRecuperacion.RECUPERAR, ReglaRecuperacion.decidir(0, true, null, "max@correo.com"))

    @Test fun `mismo telefono despues de usar otro recupera porque lo local puede estar atrasado`() =
        assertEquals(DecisionRecuperacion.RECUPERAR, ReglaRecuperacion.decidir(0, true, "max@correo.com", "max@correo.com"))

    @Test fun `otra cuenta en el telefono recupera la de la sesion`() =
        assertEquals(DecisionRecuperacion.RECUPERAR, ReglaRecuperacion.decidir(0, true, "otra@correo.com", "max@correo.com"))

    @Test fun `con ventas sin enviar no se reemplaza nada`() =
        assertEquals(DecisionRecuperacion.CONSERVAR_LOCAL, ReglaRecuperacion.decidir(3, true, "max@correo.com", "max@correo.com"))

    @Test fun `cuenta sin respaldo en linea y telefono de la misma cuenta conserva lo local`() =
        assertEquals(DecisionRecuperacion.CONSERVAR_LOCAL, ReglaRecuperacion.decidir(0, false, "MAX@correo.com", "max@correo.com"))

    @Test fun `cuenta sin respaldo en linea en telefono de otra cuenta deja el telefono vacio`() =
        assertEquals(DecisionRecuperacion.RECUPERAR, ReglaRecuperacion.decidir(0, false, "otra@correo.com", "max@correo.com"))
}

/** RF 7.5 — sesión en un solo dispositivo. */
class SesionUnicaTest {
    private val yo = "telefono-a"

    @Test fun `con datos de la copia local no se actua`() =
        assertEquals(AccionSesion.NADA, ReglaSesionUnica.accion("telefono-b", yo, desdeServidor = false, pendientes = 0))

    @Test fun `sin dispositivo registrado se reclama`() =
        assertEquals(AccionSesion.RECLAMAR, ReglaSesionUnica.accion(null, yo, true, 0))

    @Test fun `si la sesion es de este telefono no pasa nada`() =
        assertEquals(AccionSesion.NADA, ReglaSesionUnica.accion(yo, yo, true, 5))

    @Test fun `en otro telefono primero se envian los pendientes`() =
        assertEquals(AccionSesion.ENVIAR_PENDIENTES, ReglaSesionUnica.accion("telefono-b", yo, true, 2))

    @Test fun `en otro telefono y sin pendientes se cierra`() =
        assertEquals(AccionSesion.CERRAR_SESION, ReglaSesionUnica.accion("telefono-b", yo, true, 0))

    @Test fun `aviso al iniciar solo si habia otro dispositivo`() {
        assertTrue(ReglaSesionUnica.avisarAlIniciar("telefono-b", yo))
        assertFalse(ReglaSesionUnica.avisarAlIniciar(null, yo))
        assertFalse(ReglaSesionUnica.avisarAlIniciar(yo, yo))
    }
}

/** RF 7.4 — lo que se sube es lo que se recupera (Firestore devuelve los enteros como Long). */
class MapeoRecuperacionTest {
    /** Simula Firestore: Int → Long. */
    private fun comoFirestore(d: Map<String, Any?>) = d.mapValues { (_, v) -> if (v is Int) v.toLong() else v }

    @Test fun `venta con descuento y cancelada`() {
        val v = Venta(
            id = "v1", cajaId = "c1", fechaHora = 10, subtotalCentavos = 10_000, tipoDescuento = TipoDescuento.PORCENTAJE,
            valorDescuento = 1_250, descuentoCentavos = 1_250, totalCentavos = 8_750, formaPagoId = "f1",
            montoRecibidoCentavos = 10_000, cambioCentavos = 1_250, estado = EstadoVenta.CANCELADA, canceladaEn = 20,
            creadoEn = 10, actualizadoEn = 20, pendienteSync = true,
        )
        assertEquals(v.copy(pendienteSync = false), aVenta(comoFirestore(v.aDocumento())))
    }

    @Test fun `venta sin descuento conserva los nulos`() {
        val v = Venta(id = "v2", cajaId = "c1", fechaHora = 1, subtotalCentavos = 500, totalCentavos = 500, formaPagoId = "f2", creadoEn = 1, actualizadoEn = 1)
        assertEquals(v.copy(pendienteSync = false), aVenta(comoFirestore(v.aDocumento())))
    }

    @Test fun `caja abierta y caja cerrada con corte`() {
        val abierta = Caja(id = "c1", estado = EstadoCaja.ABIERTA, fondoCentavos = 10_000, abiertaEn = 5, creadoEn = 5, actualizadoEn = 5)
        val cerrada = abierta.copy(
            id = "c2", estado = EstadoCaja.CERRADA, cerradaEn = 9, corteCalculadoEn = 8, corteVigente = true,
            totalVendidoCentavos = 4_000, numeroVentas = 3, descuentosCentavos = 0, efectivoEsperadoCentavos = 14_000,
            efectivoContadoCentavos = 13_900, diferenciaCentavos = -100,
        )
        assertEquals(abierta.copy(pendienteSync = false), aCaja(comoFirestore(abierta.aDocumento())))
        assertEquals(cerrada.copy(pendienteSync = false), aCaja(comoFirestore(cerrada.aDocumento())))
    }

    @Test fun `corte por forma de pago`() {
        val c = CorteFormaPago("c1", "f1", "Efectivo", numeroVentas = 4, totalCentavos = 12_000, creadoEn = 3)
        assertEquals(c.copy(pendienteSync = false), aCorteFormaPago(comoFirestore(c.aDocumento())))
    }

    @Test fun `movimiento de ajuste con motivo`() {
        val m = MovimientoInventario(
            id = "m1", productoId = "p1", fechaHora = 4, tipo = TipoMovimiento.AJUSTE, cantidadMilesimas = -250,
            existenciaResultanteMilesimas = 750, motivo = MotivoAjuste.OTRO, especificacionMotivo = "Se cayó", creadoEn = 4,
        )
        assertEquals(m.copy(pendienteSync = false), aMovimiento(comoFirestore(m.aDocumento())))
    }

    @Test fun `producto toma la ruta local de la imagen descargada`() {
        val p = Producto(
            id = "p1", nombre = "Carlota", nombreNormalizado = "carlota", categoriaId = "k1", tipoVenta = TipoVenta.PESO,
            precioCentavos = 8_650, existenciaMilesimas = 1_500, imagenRuta = "/viejo/telefono/a.jpg", activo = false,
            creadoEn = 1, actualizadoEn = 2,
        )
        val doc = comoFirestore(p.aDocumento() + (CAMPO_IMAGEN to "a.jpg"))
        assertEquals(p.copy(imagenRuta = "/nuevo/a.jpg", pendienteSync = false), aProducto(doc, "/nuevo/a.jpg"))
    }
}
