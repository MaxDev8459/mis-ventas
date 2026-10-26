package mx.ambulantpoint.misventas

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.data.local.sembrarSiHaceFalta

class MisVentasApp : Application() {
    /** Contenedor de dependencias manual. */
    lateinit var contenedor: AppContainer
        private set

    /** Corrutinas que viven lo que vive la app (semilla, trabajos de fondo). */
    val alcanceApp = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(this)
        alcanceApp.launch { contenedor.db.sembrarSiHaceFalta(contenedor.reloj) }
    }
}
