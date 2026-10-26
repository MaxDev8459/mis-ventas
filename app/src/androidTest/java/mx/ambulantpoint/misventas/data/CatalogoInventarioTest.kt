package mx.ambulantpoint.misventas.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.TipoMovimiento
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.repository.CambiosProducto
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.data.repository.LineaVenta
import mx.ambulantpoint.misventas.data.repository.NuevoProducto
import mx.ambulantpoint.misventas.data.repository.SolicitudCobro
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Catálogo, categorías, formas de pago, inventario y consultas base de reportes. */
@RunWith(AndroidJUnit4::class)
class CatalogoInventarioTest {
    private lateinit var p: BaseDatosPrueba

    @Before fun preparar() = runTest {
        p = BaseDatosPrueba()
        p.sembrar()
    }

    @After fun limpiar() = p.cerrar()

    @Test fun categoria_duplicada_sin_distinguir_mayusculas_ni_espacios() = runTest {
        p.categorias.crear("Bebidas").exito()
        assertEquals(FallaDatos.NombreDuplicado, p.categorias.crear("  bebidas ").falla())
    }

    @Test fun categoria_con_productos_no_se_elimina() = runTest {
        val cat = p.categorias.crear("Dulces").exito()
        p.producto("Paleta", 500, 5_000, categoriaId = cat.id)
        assertEquals(FallaDatos.CategoriaConProductos, p.categorias.eliminar(cat.id).falla())

        val vacia = p.categorias.crear("Vacía").exito()
        p.categorias.eliminar(vacia.id).exito()
        assertEquals(listOf("Dulces"), p.categorias.observarTodas().first().map { it.nombre })
    }

    @Test fun producto_duplicado_incluye_desactivados() = runTest {
        val cat = p.categorias.crear("Botanas").exito()
        val papas = p.producto("Papas", 1800, 1_000, categoriaId = cat.id)
        p.productos.cambiarEstado(papas.id, activo = false).exito()
        val falla = p.productos.registrar(NuevoProducto(" PAPAS", cat.id, TipoVenta.UNIDAD, 1800, 0, "/x.jpg")).falla()
        assertEquals(FallaDatos.NombreDuplicado, falla)
    }

    @Test fun existencia_inicial_queda_como_entrada() = runTest {
        val papas = p.producto("Papas", 1800, 12_000)
        val movs = p.inventario.observarMovimientos(papas.id).first()
        assertEquals(1, movs.size)
        assertEquals(TipoMovimiento.ENTRADA, movs[0].tipo)
        assertEquals(12_000L, movs[0].existenciaResultanteMilesimas)
    }

    @Test fun tipo_de_venta_solo_cambia_con_existencia_cero() = runTest {
        val cat = p.categorias.crear("Semillas").exito()
        val pepitas = p.producto("Pepitas", 9000, 1_000, categoriaId = cat.id)
        val cambios = CambiosProducto("Pepitas", cat.id, TipoVenta.PESO, 9000, "/x.jpg")
        assertEquals(FallaDatos.TipoVentaNoModificable, p.productos.actualizar(pepitas.id, cambios).falla())

        p.inventario.ajustar(pepitas.id, aumentar = false, cantidadMilesimas = 1_000, motivo = MotivoAjuste.MERMA).exito()
        assertEquals(TipoVenta.PESO, p.productos.actualizar(pepitas.id, cambios).exito().tipoVenta)
    }

    @Test fun cambio_de_precio_no_afecta_ventas_anteriores() = runTest {
        val cat = p.categorias.crear("Bebidas").exito()
        val agua = p.producto("Agua", 1500, 5_000, categoriaId = cat.id)
        p.cajas.abrir(0).exito()
        val venta = p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(agua.id, 1000)), p.transferencia().id)).exito().venta
        p.productos.actualizar(agua.id, CambiosProducto("Agua", cat.id, TipoVenta.UNIDAD, 1800, "/x.jpg")).exito()

        val detalle = p.ventas.observarDetalles(venta.id).first().single()
        assertEquals(1500L, detalle.precioUnitarioCentavos)
    }

    @Test fun entrada_y_ajuste_registran_movimientos_y_no_bajan_de_cero() = runTest {
        val azucar = p.producto("Azúcar", 3000, 0, TipoVenta.PESO)
        assertEquals(1_500L, p.inventario.registrarEntrada(azucar.id, 1_500).exito())
        assertEquals(
            FallaDatos.DisminucionMayorAExistencia,
            p.inventario.ajustar(azucar.id, false, 2_000, MotivoAjuste.CORRECCION_CONTEO).falla(),
        )
        assertEquals(1_250L, p.inventario.ajustar(azucar.id, false, 250, MotivoAjuste.OTRO, "Se derramó").exito())

        val movs = p.inventario.observarMovimientos(azucar.id).first()
        val ajuste = movs.first { it.tipo == TipoMovimiento.AJUSTE }
        assertEquals(-250L, ajuste.cantidadMilesimas)
        assertEquals("Se derramó", ajuste.especificacionMotivo)
        assertEquals(1_250L, p.productos.porId(azucar.id)!!.existenciaMilesimas)
    }

    @Test fun efectivo_no_se_desactiva_y_formas_no_se_repiten() = runTest {
        val efectivo = p.efectivo()
        assertEquals(FallaDatos.EfectivoNoModificable, p.formas.actualizar(efectivo.id, "Efectivo", activa = false).falla())
        assertEquals(FallaDatos.NombreDuplicado, p.formas.crear("transferencia").falla())

        val transferencia = p.transferencia()
        p.formas.actualizar(transferencia.id, "Transferencia", activa = false).exito()
        assertEquals(listOf("Efectivo"), p.formas.observarActivas().first().map { it.nombre })
    }

    @Test fun ventas_recientes_toma_los_ultimos_7_dias_con_ventas() = runTest {
        val dia = 24L * 60 * 60 * 1000
        val refresco = p.producto("Refresco", 2000, 100_000)
        p.cajas.abrir(0).exito()
        // 8 días distintos, 1 pza el primero y 2 pzas los siguientes 7
        repeat(8) { i ->
            val cantidad = if (i == 0) 1000L else 2000L
            p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, cantidad)), p.transferencia().id)).exito()
            p.reloj.avanzar(dia)
        }
        val recientes = p.reportes.ventasRecientes(refresco.id)
        assertEquals(7, recientes.dias)
        assertEquals(14_000L, recientes.cantidadMilesimas)
    }

    @Test fun resumen_de_periodo_separa_canceladas() = runTest {
        val refresco = p.producto("Refresco", 2000, 10_000)
        p.cajas.abrir(0).exito()
        val v = p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 1000)), p.transferencia().id)).exito().venta
        p.ventas.cobrar(SolicitudCobro(listOf(LineaVenta(refresco.id, 2000)), p.transferencia().id)).exito()
        p.ventas.cancelar(v.id).exito()

        val r = p.reportes.resumenPeriodo(0, Long.MAX_VALUE)
        assertEquals(1, r.numeroVentas)
        assertEquals(4_000L, r.totalVendidoCentavos)
        assertEquals(1, r.numeroCanceladas)
        assertEquals(2_000L, r.importeCanceladasCentavos)
    }
}
