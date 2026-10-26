package mx.ambulantpoint.misventas.domain

import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.dao.VentasRecientes
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.data.local.entity.EstadoCaja
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.TipoDescuento
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.local.entity.Venta
import mx.ambulantpoint.misventas.data.repository.CambiosProducto
import mx.ambulantpoint.misventas.data.repository.CorteCaja
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.data.repository.NuevoProducto
import mx.ambulantpoint.misventas.data.repository.SolicitudCobro
import mx.ambulantpoint.misventas.data.repository.VentaCobrada
import mx.ambulantpoint.misventas.domain.puertos.PuertoCaja
import mx.ambulantpoint.misventas.domain.puertos.PuertoInventario
import mx.ambulantpoint.misventas.domain.puertos.PuertoProductos
import mx.ambulantpoint.misventas.domain.puertos.PuertoVentas
import mx.ambulantpoint.misventas.domain.puertos.PuertoVentasRecientes
import mx.ambulantpoint.misventas.domain.regla.FallaCampos
import mx.ambulantpoint.misventas.domain.regla.FallaMensaje
import mx.ambulantpoint.misventas.domain.regla.TipoDiferencia
import mx.ambulantpoint.misventas.domain.usecase.AbrirCaja
import mx.ambulantpoint.misventas.domain.usecase.AjustarExistencia
import mx.ambulantpoint.misventas.domain.usecase.CalcularCorte
import mx.ambulantpoint.misventas.domain.usecase.CancelarVenta
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.CerrarCaja
import mx.ambulantpoint.misventas.domain.usecase.CobrarVenta
import mx.ambulantpoint.misventas.domain.usecase.DescuentoCapturado
import mx.ambulantpoint.misventas.domain.usecase.EvaluarExistenciaBaja
import mx.ambulantpoint.misventas.domain.usecase.FormularioProducto
import mx.ambulantpoint.misventas.domain.usecase.FormularioRegistro
import mx.ambulantpoint.misventas.domain.usecase.PrepararCobro
import mx.ambulantpoint.misventas.domain.usecase.ProductoRef
import mx.ambulantpoint.misventas.domain.usecase.RegistrarEntrada
import mx.ambulantpoint.misventas.domain.usecase.RegistrarProducto
import mx.ambulantpoint.misventas.domain.usecase.ValidarUsuario
import mx.ambulantpoint.misventas.domain.venta.Carrito
import mx.ambulantpoint.misventas.domain.venta.ProductoEnVenta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun Resultado<*>.campos(): Map<String, String> = ((this as Resultado.Fallo).falla as FallaCampos).errores
private fun Resultado<*>.mensajeFalla(): String = ((this as Resultado.Fallo).falla as FallaMensaje).mensaje

private val EFECTIVO = FormaPago("ef", "Efectivo", "efectivo", esEfectivo = true, creadoEn = 0, actualizadoEn = 0)
private val TRANSFERENCIA = FormaPago("tr", "Transferencia", "transferencia", creadoEn = 0, actualizadoEn = 0)

private class ProductosFalsos(var falla: FallaDatos? = null) : PuertoProductos {
    var registrado: NuevoProducto? = null
    override suspend fun registrar(nuevo: NuevoProducto): Resultado<Producto> {
        falla?.let { return Resultado.Fallo(it) }
        registrado = nuevo
        return Resultado.Exito(
            Producto("p", nuevo.nombre, nuevo.nombre.lowercase(), nuevo.categoriaId, nuevo.tipoVenta,
                nuevo.precioCentavos, nuevo.existenciaInicialMilesimas, nuevo.imagenRuta, creadoEn = 0, actualizadoEn = 0),
        )
    }
    override suspend fun actualizar(id: String, cambios: CambiosProducto) = TODO()
    override suspend fun cambiarEstado(id: String, activo: Boolean) = TODO()
}

private class VentasFalsas(var falla: FallaDatos? = null) : PuertoVentas {
    var solicitud: SolicitudCobro? = null
    override suspend fun cobrar(solicitud: SolicitudCobro): Resultado<VentaCobrada> {
        falla?.let { return Resultado.Fallo(it) }
        this.solicitud = solicitud
        val venta = Venta("v", "c", 0, 0, totalCentavos = 0, formaPagoId = solicitud.formaPagoId, creadoEn = 0, actualizadoEn = 0)
        // Simula que al refresco le quedan 3 pzas
        return Resultado.Exito(VentaCobrada(venta, emptyList(), mapOf("r" to 3_000L)))
    }
    override suspend fun cancelar(ventaId: String) = TODO()
}

/** Siempre menos de 7 días con ventas → umbral fijo (5 pzas / 1 kg). */
private class SinHistorial : PuertoVentasRecientes {
    override suspend fun ventasRecientes(productoId: String) = VentasRecientes(0, 0)
}

private class CajaFalsa(var falla: FallaDatos? = null) : PuertoCaja {
    var fondo: Long? = null
    private val caja = Caja("c", EstadoCaja.ABIERTA, 10_000, 0, creadoEn = 0, actualizadoEn = 0)
    override suspend fun abrir(fondoCentavos: Long): Resultado<Caja> {
        falla?.let { return Resultado.Fallo(it) }
        fondo = fondoCentavos
        return Resultado.Exito(caja)
    }
    override suspend fun calcularCorte(efectivoContadoCentavos: Long) =
        Resultado.Exito(CorteCaja(caja.copy(efectivoContadoCentavos = efectivoContadoCentavos, diferenciaCentavos = efectivoContadoCentavos - 16_000), emptyList()))
    override suspend fun cerrar(): Resultado<Caja> = falla?.let { Resultado.Fallo(it) } ?: Resultado.Exito(caja)
}

private class InventarioFalso : PuertoInventario {
    override suspend fun registrarEntrada(productoId: String, cantidadMilesimas: Long) = Resultado.Exito(cantidadMilesimas)
    override suspend fun ajustar(productoId: String, aumentar: Boolean, cantidadMilesimas: Long, motivo: MotivoAjuste, especificacion: String?) =
        Resultado.Fallo(FallaDatos.DisminucionMayorAExistencia)
}

class RegistrarProductoTest {
    @Test fun `formulario vacio marca cada campo obligatorio incluida la imagen`() = runTest {
        val r = RegistrarProducto(ProductosFalsos())(FormularioProducto())
        val c = r.campos()
        assertEquals(Mensajes.Campo.OBLIGATORIO, c[Campos.NOMBRE])
        assertEquals(Mensajes.Campo.OBLIGATORIO, c[Campos.CATEGORIA])
        assertEquals(Mensajes.Campo.OBLIGATORIO, c[Campos.TIPO_VENTA])
        assertEquals(Mensajes.Campo.OBLIGATORIO, c[Campos.PRECIO])
        assertEquals(Mensajes.Campo.IMAGEN_OBLIGATORIA, c[Campos.IMAGEN])
    }

    @Test fun `existencia invalida para el tipo de venta`() = runTest {
        val f = FormularioProducto("Refresco", "cat", TipoVenta.UNIDAD, "20", "1.5", "/img.jpg")
        assertEquals(Mensajes.Campo.CANTIDAD_ENTERA, RegistrarProducto(ProductosFalsos())(f).campos()[Campos.EXISTENCIA])
    }

    @Test fun `nombre duplicado se marca en el campo nombre`() = runTest {
        val f = FormularioProducto("Refresco", "cat", TipoVenta.UNIDAD, "20", "10", "/img.jpg")
        val r = RegistrarProducto(ProductosFalsos(FallaDatos.NombreDuplicado))(f)
        assertEquals(Mensajes.Campo.NOMBRE_YA_REGISTRADO, r.campos()[Campos.NOMBRE])
    }

    @Test fun `registro exitoso convierte a centavos y milesimas`() = runTest {
        val repo = ProductosFalsos()
        val f = FormularioProducto("Cacahuate", "cat", TipoVenta.PESO, "86.50", "2.5", "/img.jpg")
        val r = RegistrarProducto(repo)(f)
        assertEquals(Mensajes.PRODUCTO_REGISTRADO, r.ok().mensaje)
        assertEquals(8650L, repo.registrado!!.precioCentavos)
        assertEquals(2500L, repo.registrado!!.existenciaInicialMilesimas)
    }

    @Test fun `falla inesperada muestra el mensaje general del DFR`() = runTest {
        val f = FormularioProducto("Refresco", "cat", TipoVenta.UNIDAD, "20", "10", "/img.jpg")
        val r = RegistrarProducto(ProductosFalsos(FallaDatos.NoEncontrado))(f)
        assertEquals(Mensajes.PRODUCTO_NO_REGISTRADO, r.mensajeFalla())
    }
}

class CobrarVentaTest {
    private val refresco = ProductoEnVenta("r", "Refresco", TipoVenta.UNIDAD, 2000, 10_000)
    private val carrito = Carrito().fijar(refresco, 3000).ok()  // $60.00
    private val existenciaBaja = EvaluarExistenciaBaja(SinHistorial())

    @Test fun `sin productos pide agregar al menos uno`() = runTest {
        val r = CobrarVenta(VentasFalsas(), existenciaBaja)(Carrito(), EFECTIVO, "100")
        assertEquals(Mensajes.AGREGUE_UN_PRODUCTO, r.mensajeFalla())
    }

    @Test fun `efectivo menor al total marca el monto recibido`() = runTest {
        val r = CobrarVenta(VentasFalsas(), existenciaBaja)(carrito, EFECTIVO, "50")
        assertEquals(Mensajes.MONTO_RECIBIDO_MENOR, r.campos()[Campos.MONTO_RECIBIDO])
    }

    @Test fun `muestra el cambio antes de confirmar`() {
        val resumen = PrepararCobro.calcular(carrito, EFECTIVO, "100", null).ok()
        assertEquals(6000L, resumen.totalCentavos)
        assertEquals(4000L, resumen.cambioCentavos)
        assertNull(PrepararCobro.calcular(carrito, TRANSFERENCIA, null, null).ok().cambioCentavos)
    }

    @Test fun `descuento en porcentaje se refleja en subtotal descuento y total`() {
        val resumen = PrepararCobro.calcular(carrito, TRANSFERENCIA, null, DescuentoCapturado(TipoDescuento.PORCENTAJE, "10")).ok()
        assertEquals(6000L, resumen.subtotalCentavos)
        assertEquals(600L, resumen.descuentoCentavos)
        assertEquals(5400L, resumen.totalCentavos)
        val invalido = PrepararCobro.calcular(carrito, TRANSFERENCIA, null, DescuentoCapturado(TipoDescuento.PORCENTAJE, "100"))
        assertEquals(Mensajes.DESCUENTO_NO_VALIDO, invalido.campos()[Campos.DESCUENTO])
    }

    @Test fun `venta registrada con aviso de existencia baja`() = runTest {
        val ventas = VentasFalsas()
        val r = CobrarVenta(ventas, existenciaBaja)(carrito, EFECTIVO, "100").ok()
        assertEquals(Mensajes.VENTA_REGISTRADA, r.mensaje)
        assertEquals(listOf("Quedan 3 pzas de Refresco. Considere surtirlo"), r.avisos)
        assertEquals(10_000L, ventas.solicitud!!.montoRecibidoCentavos)
    }

    @Test fun `existencia insuficiente al guardar usa el mensaje del DFR`() = runTest {
        val ventas = VentasFalsas(FallaDatos.ExistenciaInsuficiente("r", "Refresco", TipoVenta.UNIDAD, 2_000))
        val r = CobrarVenta(ventas, existenciaBaja)(carrito, TRANSFERENCIA)
        assertEquals("Solo hay 2 pzas disponibles de Refresco", r.mensajeFalla())
    }

    @Test fun `sin caja abierta pide abrir caja`() = runTest {
        val r = CobrarVenta(VentasFalsas(FallaDatos.SinCajaAbierta), existenciaBaja)(carrito, TRANSFERENCIA)
        assertEquals(Mensajes.ABRA_CAJA, r.mensajeFalla())
    }

    @Test fun `confirmacion de cancelar con el total formateado`() {
        val v = Venta("v", "c", 0, 6150, totalCentavos = 6150, formaPagoId = "ef", creadoEn = 0, actualizadoEn = 0)
        assertEquals(
            "¿Desea cancelar la venta de \$61.50? Las existencias se regresarán al inventario",
            CancelarVenta(VentasFalsas()).confirmacion(v),
        )
    }
}

class CajaInventarioCasosTest {
    @Test fun `abrir caja admite fondo cero`() = runTest {
        val caja = CajaFalsa()
        assertEquals(Mensajes.CAJA_ABIERTA, AbrirCaja(caja)("0").ok().mensaje)
        assertEquals(0L, caja.fondo)
        assertEquals(Mensajes.Campo.OBLIGATORIO, AbrirCaja(caja)("").campos()[Campos.FONDO])
    }

    @Test fun `corte indica faltante`() = runTest {
        val r = CalcularCorte(CajaFalsa())("150").ok().valor
        assertEquals(TipoDiferencia.FALTANTE, r.tipoDiferencia)
    }

    @Test fun `cerrar sin corte vigente se rechaza`() = runTest {
        val r = CerrarCaja(CajaFalsa(FallaDatos.CorteNoVigente))()
        assertEquals(Mensajes.Campo.CORTE_PENDIENTE, r.mensajeFalla())
    }

    @Test fun `entrada muestra la nueva existencia y avisa si sigue baja`() = runTest {
        val p = ProductoRef("r", "Refresco", TipoVenta.UNIDAD)
        val r = RegistrarEntrada(InventarioFalso(), EvaluarExistenciaBaja(SinHistorial()))(p, "4").ok()
        assertEquals(Mensajes.ENTRADA_REGISTRADA, r.mensaje)
        assertEquals("4 pzas", r.valor.existenciaTexto)
        assertEquals(listOf("Quedan 4 pzas de Refresco. Considere surtirlo"), r.avisos)
    }

    @Test fun `ajuste con motivo otro exige especificacion`() = runTest {
        val p = ProductoRef("r", "Refresco", TipoVenta.UNIDAD)
        val caso = AjustarExistencia(InventarioFalso(), EvaluarExistenciaBaja(SinHistorial()))
        assertEquals(Mensajes.Campo.ESPECIFIQUE_MOTIVO, caso(p, false, "1", MotivoAjuste.OTRO, " ").campos()[Campos.ESPECIFICACION])
        assertEquals(Mensajes.DISMINUCION_MAYOR, caso(p, false, "99", MotivoAjuste.MERMA, null).campos()[Campos.CANTIDAD])
    }
}

class ValidarUsuarioTest {
    @Test fun `registro sin aviso de privacidad no procede`() {
        val f = FormularioRegistro("Max", "Vasquez", "Arroyo", "7771234567", "Max Carlotas", "max@gmail.com", "ventas2026", "ventas2026", false)
        assertEquals(mapOf(Campos.AVISO_PRIVACIDAD to Mensajes.Campo.ACEPTAR_AVISO), ValidarUsuario.registro(f)!!.errores)
        assertNull(ValidarUsuario.registro(f.copy(aceptaAvisoPrivacidad = true)))
    }

    @Test fun `registro marca telefono contrasena y confirmacion`() {
        val f = FormularioRegistro("Max", "V", "A", "123", "Negocio", "max@gmail.com", "corta1", "otra", true)
        val e = ValidarUsuario.registro(f)!!.errores
        assertEquals(Mensajes.Campo.TELEFONO_NO_VALIDO, e[Campos.TELEFONO])
        assertEquals(Mensajes.Campo.CONTRASENA_NO_VALIDA, e[Campos.CONTRASENA])
        assertEquals(Mensajes.Campo.CONTRASENAS_NO_COINCIDEN, e[Campos.CONFIRMAR_CONTRASENA])
    }

    @Test fun `inicio de sesion valida campos vacios y correo`() {
        val e = ValidarUsuario.inicioSesion("max@", "")!!.errores
        assertEquals(Mensajes.Campo.CORREO_NO_VALIDO, e[Campos.CORREO])
        assertEquals(Mensajes.Campo.OBLIGATORIO, e[Campos.CONTRASENA])
        assertTrue(ValidarUsuario.inicioSesion("max@gmail.com", "x") == null)
    }
}
