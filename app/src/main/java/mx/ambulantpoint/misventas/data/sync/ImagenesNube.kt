package mx.ambulantpoint.misventas.data.sync

import android.content.Context
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mx.ambulantpoint.misventas.data.imagen.ProcesadorImagen
import java.io.File

/**
 * RF 7.1 / 7.4 — Imágenes de productos en Firebase Storage: `usuarios/{uid}/productos/{archivo}`.
 *
 * El archivo local ya tiene un nombre único (UUID, lo pone [ProcesadorImagen]) que cambia cuando el
 * vendedor cambia la foto. Por eso se sube con ese mismo nombre y se recuerda cuáles ya están en
 * línea: cuando un producto se vuelve a enviar solo porque cambió su existencia, la imagen no se
 * sube otra vez.
 */
class ImagenesNube(
    private val contexto: Context,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) {
    private val prefs = contexto.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    private fun clave(uid: String) = "subidas_$uid"
    private fun subidas(uid: String): Set<String> = prefs.getStringSet(clave(uid), emptySet()).orEmpty()

    private fun recordar(uid: String, nombres: Collection<String>) {
        if (nombres.isEmpty()) return
        prefs.edit().putStringSet(clave(uid), subidas(uid) + nombres).apply()
    }

    private fun referencia(uid: String, nombre: String) =
        storage.reference.child("usuarios").child(uid).child("productos").child(nombre)

    /**
     * Sube la imagen si hace falta. Devuelve el nombre del archivo en línea, o null si el producto
     * no tiene imagen en el teléfono. Si la subida falla lanza la excepción (el producto sigue pendiente).
     */
    suspend fun asegurarSubida(uid: String, rutaLocal: String): String? = withContext(Dispatchers.IO) {
        if (rutaLocal.isBlank()) return@withContext null
        val archivo = File(rutaLocal)
        if (!archivo.isFile) return@withContext null
        val nombre = archivo.name
        if (nombre in subidas(uid)) return@withContext nombre
        val metadatos = StorageMetadata.Builder().setContentType("image/jpeg").build()
        withTimeout(ESPERA_MS) { referencia(uid, nombre).putBytes(archivo.readBytes(), metadatos).await() }
        recordar(uid, listOf(nombre))
        nombre
    }

    /** RF 7.4: descarga la imagen a la carpeta de la app y devuelve su ruta local. */
    suspend fun descargar(uid: String, nombre: String): String = withContext(Dispatchers.IO) {
        val bytes = withTimeout(ESPERA_MS) { referencia(uid, nombre).getBytes(MAX_BYTES).await() }
        val destino = File(carpeta(), nombre)
        destino.writeBytes(bytes)
        destino.absolutePath
    }

    /** RF 7.4: las imágenes recuperadas ya están en línea; no se vuelven a subir. */
    fun recordarRecuperadas(uid: String, nombres: Collection<String>) = recordar(uid, nombres)

    /** RF 7.4: borra del teléfono las imágenes que ya no usa ningún producto (de la información reemplazada). */
    fun borrarSobrantes(enUso: Set<String>) {
        carpeta().listFiles()?.forEach { if (it.absolutePath !in enUso) it.delete() }
    }

    /**
     * Los productos que se sincronizaron antes de respaldar imágenes (18-nov-2026) están en línea sin
     * su imagen. La primera vez se vuelven a marcar como pendientes para que la suban.
     */
    fun requiereReenvioInicial(): Boolean = !prefs.getBoolean(CLAVE_REENVIO, false)
    fun reenvioInicialHecho() = prefs.edit().putBoolean(CLAVE_REENVIO, true).apply()

    private fun carpeta() = File(contexto.filesDir, ProcesadorImagen.CARPETA).apply { mkdirs() }

    private companion object {
        const val ARCHIVO = "mis_ventas_imagenes"
        const val CLAVE_REENVIO = "reenvio_inicial_hecho"
        const val ESPERA_MS = 60_000L
        /** Las imágenes pesan máximo 300 KB (RF 2.1); se deja margen. */
        const val MAX_BYTES = 1024L * 1024L
    }
}
