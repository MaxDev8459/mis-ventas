package mx.ambulantpoint.misventas.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.MisVentasApp
import mx.ambulantpoint.misventas.core.Dinero
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.normalizarNombre
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.local.sembrarSiHaceFalta
import mx.ambulantpoint.misventas.data.repository.CajaRepo
import mx.ambulantpoint.misventas.data.repository.InventarioRepo
import mx.ambulantpoint.misventas.data.repository.LineaVenta
import mx.ambulantpoint.misventas.data.repository.NuevoProducto
import mx.ambulantpoint.misventas.data.repository.SolicitudCobro
import mx.ambulantpoint.misventas.data.repository.VentaRepo
import java.util.Calendar
import kotlin.random.Random
import mx.ambulantpoint.misventas.domain.puertos.DatosRegistro
import mx.ambulantpoint.misventas.domain.puertos.EstadoSesion
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Carga datos de demo en la base REAL de la app (no en memoria). Solo corre si se pasa el argumento
 * `demo=1`; en una corrida normal de pruebas se salta. Es idempotente: no duplica lo que ya existe.
 * Comando en CLAUDE.md (sección Compilar).
 */
@RunWith(AndroidJUnit4::class)
class CargarDatosDemo {

    private data class Demo(val nombre: String, val categoria: String, val tipo: TipoVenta, val precio: Long, val existencia: Long, val color: Int)

    private val productos = listOf(
        Demo("Papas fritas", "Botanas", TipoVenta.UNIDAD, 1_800, 20_000, 0xFFE0A526.toInt()),
        Demo("Cacahuate japonés", "Botanas", TipoVenta.PESO, 9_000, 2_500, 0xFFB5651D.toInt()),
        Demo("Galletas", "Botanas", TipoVenta.UNIDAD, 1_500, 0, 0xFF8D6E63.toInt()),              // agotado (demo: entrada)
        Demo("Paleta de caramelo", "Dulces", TipoVenta.UNIDAD, 500, 40_000, 0xFFD84343.toInt()),
        Demo("Chicles", "Dulces", TipoVenta.UNIDAD, 300, 4_000, 0xFF6FA8DC.toInt()),              // existencia baja
        Demo("Gomitas", "Dulces", TipoVenta.PESO, 12_000, 800, 0xFF9C27B0.toInt()),               // existencia baja (kg)
        Demo("Refresco 600 ml", "Bebidas", TipoVenta.UNIDAD, 2_000, 24_000, 0xFF3D6B35.toInt()),
        Demo("Agua 600 ml", "Bebidas", TipoVenta.UNIDAD, 1_200, 15_000, 0xFF2E86C1.toInt()),
    )

    @Test
    fun cargar() = runBlocking {
        assumeTrue("Solo con -Pandroid.testInstrumentationRunnerArguments.demo=1",
            InstrumentationRegistry.getArguments().getString("demo") == "1")

        val app = ApplicationProvider.getApplicationContext<MisVentasApp>()
        val c = app.contenedor
        c.db.sembrarSiHaceFalta(c.reloj)

        if (c.auth.estado.value == EstadoSesion.SIN_CUENTA) {
            c.auth.registrar(
                DatosRegistro("Max", "Vasquez", "Arroyo", "7771234567", "Max Carlotas", "max@gmail.com", "ventas2026"),
            )
        }

        val categorias = mutableMapOf<String, String>()
        c.categoriaRepo.observarTodas().first().forEach { categorias[normalizarNombre(it.nombre)] = it.id }
        for (nombre in productos.map { it.categoria }.distinct()) {
            if (normalizarNombre(nombre) !in categorias) {
                (c.categoriaRepo.crear(nombre) as? Resultado.Exito)?.let { categorias[normalizarNombre(nombre)] = it.valor.id }
            }
        }

        for (p in productos) {
            val imagen = imagen(app.filesDir, p.nombre, p.color)
            // Si ya existe (nombre duplicado) simplemente no se vuelve a crear.
            c.productoRepo.registrar(
                NuevoProducto(p.nombre, categorias.getValue(normalizarNombre(p.categoria)), p.tipo, p.precio, p.existencia, imagen),
            )
        }

        cargarVentas(c)
    }

    /** Productos que se venden en la demo (Galletas, Chicles y Gomitas conservan su estado de agotado / bajo). */
    private val vendibles = listOf("Papas fritas", "Cacahuate japonés", "Paleta de caramelo", "Refresco 600 ml", "Agua 600 ml")

    /** Reloj que la demo mueve a mano para fechar cajas, ventas y movimientos en días pasados. */
    private class RelojDemo(var ms: Long = 0) : Reloj { override fun ahora() = ms }

    /**
     * Módulo 6 (demo de reportes): dos semanas de trabajo, de lunes a viernes, terminando ayer. Usa los
     * mismos repositorios que la app, así que se respetan existencias, cobro y corte. Solo se carga si
     * no hay ninguna caja (idempotente). La caja del último día queda cerrada.
     */
    private suspend fun cargarVentas(c: AppContainer) {
        if (c.db.query("SELECT COUNT(*) FROM caja", null).use { it.moveToFirst(); it.getInt(0) } > 0) return

        val reloj = RelojDemo()
        val cajas = CajaRepo(c.db, reloj)
        val ventas = VentaRepo(c.db, reloj)
        val inventario = InventarioRepo(c.db, reloj)
        val formas = c.formaPagoRepo.observarActivas().first()
        val efectivo = formas.first { it.esEfectivo }
        val transferencia = formas.firstOrNull { normalizarNombre(it.nombre) == "transferencia" } ?: efectivo
        val azar = Random(2026)

        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dias = (14 downTo 1).map { atras -> (hoy.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -atras) } }
            .filter { it.get(Calendar.DAY_OF_WEEK) !in listOf(Calendar.SATURDAY, Calendar.SUNDAY) }

        fun a(dia: Calendar, hora: Int, minuto: Int = 0) = (dia.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hora); set(Calendar.MINUTE, minuto)
        }.timeInMillis

        dias.forEachIndexed { i, dia ->
            val productos = c.productoRepo.observar(activo = true).first().map { it.producto }
                .filter { it.nombre in vendibles }

            // 8:30 — entrada de mercancía si algo quedó corto (RF 4.1).
            reloj.ms = a(dia, 8, 30)
            for (p in productos) {
                val minimo = if (p.tipoVenta == TipoVenta.PESO) 1_500L else 12_000L
                if (p.existenciaMilesimas < minimo) {
                    inventario.registrarEntrada(p.id, if (p.tipoVenta == TipoVenta.PESO) 2_000L else 24_000L)
                }
            }

            // 9:00 — abrir caja con fondo de $200 (RF 5.1).
            reloj.ms = a(dia, 9)
            cajas.abrir(20_000)

            // 10:00 a 16:00 — ventas (RF 3.1 / 3.2).
            val numVentas = 4 + azar.nextInt(4)
            val cobradas = mutableListOf<String>()
            repeat(numVentas) { n ->
                reloj.ms = a(dia, 10 + n * 6 / numVentas, azar.nextInt(50))
                val disponibles = c.productoRepo.observar(activo = true).first().map { it.producto }
                    .filter { it.nombre in vendibles }
                val lineas = disponibles.shuffled(azar).take(1 + azar.nextInt(2)).map { p ->
                    val cantidad = if (p.tipoVenta == TipoVenta.PESO) 100L + 50L * azar.nextInt(5) else 1_000L * (1 + azar.nextInt(3))
                    p to minOf(cantidad, p.existenciaMilesimas)
                }.filter { it.second > 0 }
                if (lineas.isEmpty()) return@repeat

                val total = lineas.sumOf { (p, cant) -> Dinero.importe(cant, p.precioCentavos) }
                val enEfectivo = azar.nextInt(10) < 7
                val solicitud = SolicitudCobro(
                    lineas = lineas.map { (p, cant) -> LineaVenta(p.id, cant) },
                    formaPagoId = if (enEfectivo) efectivo.id else transferencia.id,
                    // Paga exacto o con el siguiente múltiplo de $50.
                    montoRecibidoCentavos = if (enEfectivo) {
                        if (azar.nextBoolean()) total else (total / 5_000 + 1) * 5_000
                    } else null,
                )
                (ventas.cobrar(solicitud) as? Resultado.Exito)?.let { cobradas += it.valor.venta.id }
            }

            // En dos días se cancela una venta (RF 3.3).
            if (i % 4 == 1 && cobradas.size > 2) {
                reloj.ms = a(dia, 16, 30)
                ventas.cancelar(cobradas[1])
            }

            // 17:00 — corte (RF 5.2): algunos días sobra o falta efectivo. 17:05 — cierre (RF 5.3).
            reloj.ms = a(dia, 17)
            val esperado = (cajas.calcularCorte(0) as Resultado.Exito).valor.efectivoEsperadoCentavos
            val diferencia = when (i % 5) { 2 -> 1_000L; 4 -> -500L; else -> 0L }
            cajas.calcularCorte(esperado + diferencia)
            reloj.ms = a(dia, 17, 5)
            cajas.cerrar()
        }
    }

    /** Imagen sencilla (color + nombre) para que cada producto tenga su referencia. */
    private fun imagen(filesDir: File, nombre: String, color: Int): String {
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(color)
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val palabras = nombre.split(" ")
        val alto = pincel.fontSpacing
        var y = 240f - (palabras.size - 1) * alto / 2 + 22f
        for (palabra in palabras) { canvas.drawText(palabra, 320f, y, pincel); y += alto }
        val dir = File(filesDir, "productos").apply { mkdirs() }
        val archivo = File(dir, "${nuevoId()}.jpg")
        archivo.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return archivo.absolutePath
    }
}
