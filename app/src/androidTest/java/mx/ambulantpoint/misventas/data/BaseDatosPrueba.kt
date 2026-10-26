package mx.ambulantpoint.misventas.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.local.sembrarSiHaceFalta
import mx.ambulantpoint.misventas.data.repository.CajaRepo
import mx.ambulantpoint.misventas.data.repository.CategoriaRepo
import mx.ambulantpoint.misventas.data.repository.FormaPagoRepo
import mx.ambulantpoint.misventas.data.repository.InventarioRepo
import mx.ambulantpoint.misventas.data.repository.NuevoProducto
import mx.ambulantpoint.misventas.data.repository.ProductoRepo
import mx.ambulantpoint.misventas.data.repository.ReporteRepo
import mx.ambulantpoint.misventas.data.repository.VentaRepo
import org.junit.Assert.fail

/** Reloj que avanza a mano, para probar fechas y orden. */
class RelojFijo(var ms: Long = 1_790_000_000_000L) : Reloj {
    override fun ahora(): Long = ms
    fun avanzar(deltaMs: Long) { ms += deltaMs }
}

/** Base de datos en memoria con los repositorios reales. */
class BaseDatosPrueba {
    val reloj = RelojFijo()
    val db: MisVentasDb = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), MisVentasDb::class.java,
    ).build()

    val categorias = CategoriaRepo(db, reloj)
    val productos = ProductoRepo(db, reloj)
    val formas = FormaPagoRepo(db, reloj)
    val cajas = CajaRepo(db, reloj)
    val ventas = VentaRepo(db, reloj)
    val inventario = InventarioRepo(db, reloj)
    val reportes = ReporteRepo(db)

    suspend fun sembrar() = db.sembrarSiHaceFalta(reloj)

    suspend fun efectivo(): FormaPago = formas.observarTodas().first().first { it.esEfectivo }
    suspend fun transferencia(): FormaPago = formas.observarTodas().first().first { it.nombre == "Transferencia" }

    suspend fun producto(
        nombre: String,
        precioCentavos: Long,
        existenciaMilesimas: Long,
        tipoVenta: TipoVenta = TipoVenta.UNIDAD,
        categoriaId: String? = null,
    ): Producto {
        val cat = categoriaId ?: categorias.crear("Categoría ${System.nanoTime()}").exito().id
        return productos.registrar(
            NuevoProducto(nombre, cat, tipoVenta, precioCentavos, existenciaMilesimas, "/img/$nombre.jpg"),
        ).exito()
    }

    fun cerrar() = db.close()
}

fun <T> Resultado<T>.exito(): T = when (this) {
    is Resultado.Exito -> valor
    is Resultado.Fallo -> { fail("Se esperaba éxito y falló con $falla"); throw IllegalStateException() }
}

fun <T> Resultado<T>.falla(): Any = when (this) {
    is Resultado.Exito -> { fail("Se esperaba falla y fue éxito: $valor"); throw IllegalStateException() }
    is Resultado.Fallo -> falla
}
