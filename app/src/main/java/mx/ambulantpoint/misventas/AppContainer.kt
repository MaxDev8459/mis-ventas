package mx.ambulantpoint.misventas

import android.content.Context
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.RelojSistema
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.repository.CajaRepo
import mx.ambulantpoint.misventas.data.repository.CategoriaRepo
import mx.ambulantpoint.misventas.data.repository.ConfiguracionRepo
import mx.ambulantpoint.misventas.data.repository.FormaPagoRepo
import mx.ambulantpoint.misventas.data.repository.InventarioRepo
import mx.ambulantpoint.misventas.data.repository.PerfilRepo
import mx.ambulantpoint.misventas.data.repository.ProductoRepo
import mx.ambulantpoint.misventas.data.repository.ReporteRepo
import mx.ambulantpoint.misventas.data.repository.VentaRepo
import mx.ambulantpoint.misventas.data.auth.AuthRepoFirebase
import mx.ambulantpoint.misventas.data.red.Conectividad
import mx.ambulantpoint.misventas.data.sync.ServicioSincronizacion
import mx.ambulantpoint.misventas.data.sync.SincronizadorFirestore
import mx.ambulantpoint.misventas.data.imagen.ProcesadorImagen
import kotlinx.coroutines.flow.MutableStateFlow
import mx.ambulantpoint.misventas.domain.puertos.EstadoSesion
import mx.ambulantpoint.misventas.domain.puertos.PuertoAuth
import mx.ambulantpoint.misventas.ui.components.Notificador

/** DI manual: la base de datos y los repositorios se crean una sola vez. */
class AppContainer(private val contexto: Context, val reloj: Reloj = RelojSistema) {
    val db: MisVentasDb = MisVentasDb.crear(contexto.applicationContext)

    val perfilRepo by lazy { PerfilRepo(db, reloj) }
    val configuracionRepo by lazy { ConfiguracionRepo(db, reloj) }
    val categoriaRepo by lazy { CategoriaRepo(db, reloj) }
    val productoRepo by lazy { ProductoRepo(db, reloj) }
    val formaPagoRepo by lazy { FormaPagoRepo(db, reloj) }
    val cajaRepo by lazy { CajaRepo(db, reloj) }
    val ventaRepo by lazy { VentaRepo(db, reloj) }
    val inventarioRepo by lazy { InventarioRepo(db, reloj) }
    val reporteRepo by lazy { ReporteRepo(db) }

    val conectividad by lazy { Conectividad(contexto) }

    /** Sprint 2: Firebase Authentication detrás de la misma interfaz que usaba el MVP local. */
    val auth: PuertoAuth by lazy { AuthRepoFirebase(contexto, db, reloj, conectividad) }

    /** RF 7.1 – 7.3. Se crea al mostrar la app (ver NavegacionApp) y vive lo que vive el proceso. */
    val sincronizacion by lazy {
        ServicioSincronizacion(contexto, db, auth, conectividad, reloj, SincronizadorFirestore(db))
    }

    /** Mensajes de una sola vez (snackbar) que sobreviven al cambiar de pantalla. */
    val notificador = Notificador()

    /**
     * RF 1.2: si la app abre con una sesión activa, hay que verificar la identidad con el método de
     * seguridad del teléfono. Si abre sin sesión, el vendedor entra con correo y contraseña y no se
     * le vuelve a pedir. Vive lo que vive el proceso: al volver a abrir la app se pide de nuevo.
     */
    val identidadVerificada by lazy { MutableStateFlow(auth.estado.value != EstadoSesion.ACTIVA) }

    val procesadorImagen by lazy { ProcesadorImagen(contexto) }
}
