package mx.ambulantpoint.misventas

import android.app.Application

class MisVentasApp : Application() {
    /** Contenedor de dependencias manual (se llena en Fase 1: Room y repositorios). */
    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(this)
    }
}
