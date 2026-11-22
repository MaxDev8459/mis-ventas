package mx.ambulantpoint.misventas.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.sembrarSiHaceFalta
import mx.ambulantpoint.misventas.data.red.Conectividad
import mx.ambulantpoint.misventas.domain.regla.DecisionRecuperacion
import mx.ambulantpoint.misventas.domain.regla.ReglaRecuperacion
import mx.ambulantpoint.misventas.domain.regla.ReglaSesionUnica

/** RF 7.4: estado de la pantalla "Recuperando su información…". */
sealed interface EstadoRecuperacion {
    data object EnEspera : EstadoRecuperacion
    /** [progreso] de 0 a 1. */
    data class EnCurso(val progreso: Float) : EstadoRecuperacion
    data object Fallida : EstadoRecuperacion
}

/**
 * RF 7.4 — Recuperar la información en un dispositivo nuevo, y el registro del dispositivo que tiene
 * la sesión activa (RF 7.5).
 *
 * Después de cada inicio de sesión con correo y contraseña la recuperación queda **requerida**
 * (guardado en preferencias: si la app se cierra a la mitad, sigue requerida al volver a abrirla) y
 * la app no deja operar hasta terminarla. Se descarga todo a memoria y a la carpeta de imágenes, y
 * solo al final se reemplaza la base local en una sola transacción: si la conexión se pierde a la
 * mitad, el teléfono queda como estaba y se puede reintentar.
 */
class ServicioRecuperacion(
    contexto: Context,
    private val db: MisVentasDb,
    private val red: Conectividad,
    private val reloj: Reloj,
    private val imagenes: ImagenesNube,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val prefs = contexto.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
    private val prefsSync = contexto.applicationContext.getSharedPreferences(ServicioSincronizacion.ARCHIVO, Context.MODE_PRIVATE)
    private val candado = Mutex()

    /**
     * RF 7.5: identificador de esta instalación de la app. Borrar los datos de la app o instalarla en
     * otro teléfono crea uno nuevo (es, para la cuenta, un dispositivo nuevo).
     */
    val miDispositivo: String = prefs.getString(CLAVE_DISPOSITIVO, null) ?: nuevoId().also {
        prefs.edit().putString(CLAVE_DISPOSITIVO, it).apply()
    }

    private val _requerida = MutableStateFlow(prefs.getBoolean(CLAVE_REQUERIDA, false))
    /** true mientras la app no debe dejar operar (RF 7.4). También detiene la sincronización. */
    val requerida: StateFlow<Boolean> = _requerida.asStateFlow()

    private val _estado = MutableStateFlow<EstadoRecuperacion>(EstadoRecuperacion.EnEspera)
    val estado: StateFlow<EstadoRecuperacion> = _estado.asStateFlow()

    private val _avisoOtroDispositivo = MutableStateFlow(false)
    /** RF 7.5: mostrar "Su cuenta se cerrará en el otro dispositivo…". */
    val avisoOtroDispositivo: StateFlow<Boolean> = _avisoOtroDispositivo.asStateFlow()
    fun avisoMostrado() { _avisoOtroDispositivo.value = false }

    /** Lo llama la autenticación al iniciar sesión, antes de marcar la sesión como activa. */
    fun marcarRequerida() {
        prefs.edit().putBoolean(CLAVE_REQUERIDA, true).commit()
        _requerida.value = true
        _estado.value = EstadoRecuperacion.EnEspera
    }

    /** Documento con el dispositivo que tiene la sesión activa: `usuarios/{uid}/sesion/dispositivo`. */
    fun documentoSesion(uid: String): DocumentReference =
        firestore.collection("usuarios").document(uid).collection(COLECCION_SESION).document(DOC_DISPOSITIVO)

    /** Registra este teléfono como el de la sesión activa (RF 7.5). */
    suspend fun reclamarSesion(uid: String) {
        withTimeout(ESPERA_MS) {
            documentoSesion(uid).set(mapOf(CAMPO_DISPOSITIVO to miDispositivo, "desde" to reloj.ahora())).await()
        }
    }

    /** RF 7.4. Si ya hay una en curso, no hace nada. */
    suspend fun recuperar() = withContext(Dispatchers.IO) {
        if (!candado.tryLock()) return@withContext
        try {
            if (!_requerida.value) return@withContext
            _estado.value = EstadoRecuperacion.EnCurso(0f)
            val exito = try {
                if (!red.hayInternet()) false else { ejecutar(); true }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (exito) {
                prefs.edit().putBoolean(CLAVE_REQUERIDA, false).commit()
                _requerida.value = false
                _estado.value = EstadoRecuperacion.EnEspera
            } else {
                _estado.value = EstadoRecuperacion.Fallida
            }
        } finally {
            candado.unlock()
        }
    }

    private suspend fun ejecutar() {
        val usuario = auth.currentUser ?: error("Sin sesión de Firebase")
        val uid = usuario.uid
        val raiz = firestore.collection("usuarios").document(uid)

        // RF 7.5: este teléfono pasa a ser el de la sesión activa; el anterior se cerrará.
        val anterior = withTimeout(ESPERA_MS) { documentoSesion(uid).get(Source.SERVER).await() }.getString(CAMPO_DISPOSITIVO)
        reclamarSesion(uid)
        if (ReglaSesionUnica.avisarAlIniciar(anterior, miDispositivo)) _avisoOtroDispositivo.value = true

        // RF 7.4: solo la información de esta cuenta, solo lo que ya estaba en línea.
        val perfiles = bajar(raiz.collection(Colecciones.PERFIL))
        val decision = ReglaRecuperacion.decidir(
            pendientesNegocio = db.syncDao().contarPendientesNegocio(),
            nubeTienePerfil = perfiles.isNotEmpty(),
            correoLocal = db.perfilDao().obtener()?.correo,
            correoSesion = usuario.email.orEmpty(),
        )
        if (decision == DecisionRecuperacion.CONSERVAR_LOCAL) return

        val colecciones = listOf(
            Colecciones.CONFIGURACION, Colecciones.CATEGORIA, Colecciones.FORMA_PAGO, Colecciones.PRODUCTO,
            Colecciones.CAJA, Colecciones.CORTE_FORMA_PAGO, Colecciones.VENTA, Colecciones.VENTA_DETALLE,
            Colecciones.MOVIMIENTO, Colecciones.CARGA,
        )
        val datos = mutableMapOf(Colecciones.PERFIL to perfiles)
        colecciones.forEachIndexed { i, c ->
            datos[c] = bajar(raiz.collection(c))
            _estado.value = EstadoRecuperacion.EnCurso(PARTE_DATOS * (i + 1) / colecciones.size)
        }

        // Imágenes de los productos (RF 7.4: "productos con sus imágenes").
        val docsProductos = datos.getValue(Colecciones.PRODUCTO)
        val conImagen = docsProductos.mapNotNull { it[CAMPO_IMAGEN] as? String }.distinct()
        val rutas = mutableMapOf<String, String>()
        conImagen.forEachIndexed { i, nombre ->
            rutas[nombre] = imagenes.descargar(uid, nombre)
            _estado.value = EstadoRecuperacion.EnCurso(PARTE_DATOS + (1f - PARTE_DATOS - 0.05f) * (i + 1) / conImagen.size)
        }

        val productos = docsProductos.map { d -> aProducto(d, (d[CAMPO_IMAGEN] as? String)?.let { rutas[it] }.orEmpty()) }
        val dao = db.syncDao()
        db.withTransaction {
            // Hijos antes que padres (llaves foráneas).
            dao.borrarMovimientos(); dao.borrarVentaDetalles(); dao.borrarVentas(); dao.borrarCortesFormaPago(); dao.borrarCargas()
            dao.borrarCajas(); dao.borrarProductos(); dao.borrarFormasPago(); dao.borrarCategorias()
            dao.borrarConfiguracion(); dao.borrarPerfil(); dao.borrarEliminaciones()

            dao.insertarPerfiles(perfiles.map(::aPerfil).take(1))
            dao.insertarConfiguraciones(datos.getValue(Colecciones.CONFIGURACION).map(::aConfiguracion))
            dao.insertarCategorias(datos.getValue(Colecciones.CATEGORIA).map(::aCategoria))
            dao.insertarFormasPago(datos.getValue(Colecciones.FORMA_PAGO).map(::aFormaPago))
            dao.insertarProductos(productos)
            dao.insertarCajas(datos.getValue(Colecciones.CAJA).map(::aCaja))
            dao.insertarCortesFormaPago(datos.getValue(Colecciones.CORTE_FORMA_PAGO).map(::aCorteFormaPago))
            dao.insertarVentas(datos.getValue(Colecciones.VENTA).map(::aVenta))
            dao.insertarVentaDetalles(datos.getValue(Colecciones.VENTA_DETALLE).map(::aVentaDetalle))
            dao.insertarMovimientos(datos.getValue(Colecciones.MOVIMIENTO).map(::aMovimiento))
            // Respaldos anteriores a la v5 no tienen carga del día: la colección llega vacía.
            dao.insertarCargas(datos.getValue(Colecciones.CARGA).map(::aCargaCaja))
        }
        // Una cuenta que nunca subió formas de pago recibe las iniciales (Efectivo y Transferencia).
        db.sembrarSiHaceFalta(reloj)

        imagenes.recordarRecuperadas(uid, conImagen)
        imagenes.borrarSobrantes(productos.map { it.imagenRuta }.filter { it.isNotBlank() }.toSet())
        // Lo del teléfono es igual al respaldo en línea: cuenta como sincronizado (RF 7.2 / 7.6).
        prefsSync.edit().putLong(ServicioSincronizacion.CLAVE_ULTIMA, reloj.ahora()).apply()
        _estado.value = EstadoRecuperacion.EnCurso(1f)
    }

    /** Toda la colección, por páginas, directo del servidor (sin copia local: falla sin conexión). */
    private suspend fun bajar(coleccion: CollectionReference): List<Map<String, Any?>> {
        val salida = mutableListOf<Map<String, Any?>>()
        var ultimo: DocumentSnapshot? = null
        while (true) {
            var consulta = coleccion.orderBy(FieldPath.documentId()).limit(PAGINA.toLong())
            ultimo?.let { consulta = consulta.startAfter(it) }
            val pagina = withTimeout(ESPERA_MS) { consulta.get(Source.SERVER).await() }
            pagina.documents.forEach { d -> d.data?.let { salida += it } }
            if (pagina.size() < PAGINA) break
            ultimo = pagina.documents.last()
        }
        return salida
    }

    companion object {
        private const val ARCHIVO = "mis_ventas_recuperacion"
        private const val CLAVE_REQUERIDA = "requerida"
        private const val CLAVE_DISPOSITIVO = "dispositivo"
        const val COLECCION_SESION = "sesion"
        const val DOC_DISPOSITIVO = "dispositivo"
        const val CAMPO_DISPOSITIVO = "dispositivo"
        private const val PAGINA = 500
        private const val ESPERA_MS = 60_000L
        /** Parte de la barra de progreso que corresponde a los datos (el resto, imágenes y guardado). */
        private const val PARTE_DATOS = 0.6f
    }
}
