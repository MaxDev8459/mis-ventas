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
import mx.ambulantpoint.misventas.MisVentasApp
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.normalizarNombre
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.local.sembrarSiHaceFalta
import mx.ambulantpoint.misventas.data.repository.NuevoProducto
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
