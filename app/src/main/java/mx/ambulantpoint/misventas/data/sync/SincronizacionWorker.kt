package mx.ambulantpoint.misventas.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import mx.ambulantpoint.misventas.MisVentasApp
import java.util.concurrent.TimeUnit

/**
 * RF 7.1 — Sincronización automática con la app cerrada.
 *
 * Solo con Wi-Fi (red sin costo): con datos móviles el DFR pide preguntar al vendedor, y con la
 * app cerrada no se puede preguntar; eso queda para cuando la abra. Android la ejecuta cada 15 min
 * como mínimo (puede retrasarla en ahorro de batería) y la conserva tras reiniciar el teléfono.
 */
class SincronizacionWorker(contexto: Context, params: WorkerParameters) : CoroutineWorker(contexto, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as MisVentasApp).contenedor
        return if (c.sincronizacion.sincronizarEnSegundoPlano()) Result.success() else Result.retry()
    }

    companion object {
        private const val TRABAJO = "sincronizacion_wifi"

        /** Se llama al iniciar la app. KEEP: no reinicia el periodo si ya estaba programada. */
        fun programar(contexto: Context) {
            val solicitud = PeriodicWorkRequestBuilder<SincronizacionWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(TRABAJO, ExistingPeriodicWorkPolicy.KEEP, solicitud)
        }
    }
}
