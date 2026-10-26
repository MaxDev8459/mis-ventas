package mx.ambulantpoint.misventas.data.imagen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.ambulantpoint.misventas.core.nuevoId
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * RF 2.1 / 2.2 y RNF Eficiencia: cualquier foto (JPG, PNG, WEBP, HEIC) se redimensiona a máximo
 * 1024 px por lado y se comprime a máximo 300 KB, automáticamente, y se guarda en la app.
 */
class ProcesadorImagen(private val contexto: Context) {

    private val carpeta: File get() = File(contexto.filesDir, CARPETA).apply { mkdirs() }

    /** Archivo temporal para que la cámara guarde la foto (TakePicture). */
    fun uriParaCamara(): Uri {
        val dir = File(contexto.cacheDir, "camara").apply { mkdirs() }
        val archivo = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
    }

    /** Devuelve la ruta del JPEG guardado, o null si la imagen no se pudo leer. */
    suspend fun procesar(origen: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = leerReducido(origen) ?: return@runCatching null
            val girado = corregirOrientacion(bitmap, origen)
            val escalado = escalar(girado, LADO_MAXIMO)
            val bytes = comprimir(escalado, BYTES_MAXIMOS)
            val archivo = File(carpeta, "${nuevoId()}.jpg")
            archivo.writeBytes(bytes)
            archivo.absolutePath
        }.getOrNull()
    }

    /** Lee la imagen ya submuestreada para no cargar 12 MP completos en memoria. */
    private fun leerReducido(uri: Uri): Bitmap? {
        val resolver = contexto.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        var muestra = 1
        while (max(limites.outWidth, limites.outHeight) / (muestra * 2) >= LADO_MAXIMO) muestra *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
    }

    private fun corregirOrientacion(bitmap: Bitmap, uri: Uri): Bitmap {
        val orientacion = runCatching {
            contexto.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val grados = when (orientacion) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val m = Matrix().apply { postRotate(grados) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private fun escalar(bitmap: Bitmap, lado: Int): Bitmap {
        val mayor = max(bitmap.width, bitmap.height)
        if (mayor <= lado) return bitmap
        val factor = lado.toFloat() / mayor
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).roundToInt(), (bitmap.height * factor).roundToInt(), true)
    }

    /** Baja la calidad JPEG (y en último caso el tamaño) hasta quedar en el límite. */
    private fun comprimir(original: Bitmap, limite: Int): ByteArray {
        var bitmap = original
        while (true) {
            var calidad = 90
            while (calidad >= 40) {
                val salida = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
                if (salida.size() <= limite) return salida.toByteArray()
                calidad -= 10
            }
            bitmap = escalar(bitmap, (max(bitmap.width, bitmap.height) * 0.8f).roundToInt())
        }
    }

    companion object {
        const val LADO_MAXIMO = 1024
        const val BYTES_MAXIMOS = 300 * 1024
        private const val CARPETA = "productos"
    }
}
