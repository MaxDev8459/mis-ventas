package mx.ambulantpoint.misventas.data.notificaciones

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.MainActivity
import mx.ambulantpoint.misventas.MisVentasApp
import mx.ambulantpoint.misventas.R
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.sync.ServicioSincronizacion
import mx.ambulantpoint.misventas.domain.puertos.EstadoSesion
import mx.ambulantpoint.misventas.domain.regla.ReglaNotificaciones
import java.util.concurrent.TimeUnit

/** A dónde lleva una notificación al tocarla. */
enum class DestinoNotificacion { CORTE_CAJA, INICIO, APARTADO }

/**
 * RF 5.4 y 7.6 — Notificaciones que llegan aun con la app cerrada y sin internet. WorkManager las
 * conserva aunque se reinicie el teléfono.
 */
object Recordatorios {
    const val EXTRA_DESTINO = "destino"

    private const val CANAL_CAJA = "caja"
    private const val CANAL_RESPALDO = "respaldo"
    private const val ID_CAJA = 1
    private const val ID_RESPALDO = 2
    private const val TRABAJO_CAJA = "caja_abierta_12h"
    private const val TRABAJO_RESPALDO = "sin_respaldo_3d"
    private const val TRABAJO_RESPALDO_AL_ABRIR = "sin_respaldo_al_abrir"
    private const val DATO_CAJA = "cajaId"

    /** Se llama al iniciar la app. */
    fun iniciar(app: MisVentasApp, alcance: CoroutineScope) {
        crearCanales(app)
        val wm = WorkManager.getInstance(app)

        // RF 7.6: referencia de "nunca sincronizado" (si se borran los datos de la app, vuelve a empezar).
        val prefsSync = app.getSharedPreferences(ServicioSincronizacion.ARCHIVO, Context.MODE_PRIVATE)
        if (!prefsSync.contains(CLAVE_PRIMER_ARRANQUE)) {
            prefsSync.edit().putLong(CLAVE_PRIMER_ARRANQUE, app.contenedor.reloj.ahora()).apply()
        }

        // RF 7.6: se revisa cada 6 h; la regla decide si toca avisar (y no repite antes de 24 h).
        wm.enqueueUniquePeriodicWork(
            TRABAJO_RESPALDO, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SinRespaldoWorker>(6, TimeUnit.HOURS).build(),
        )
        // RF 7.6: también al abrir la app (la regla evita repetir el aviso antes de 24 h).
        wm.enqueueUniqueWork(
            TRABAJO_RESPALDO_AL_ABRIR, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SinRespaldoWorker>().build(),
        )

        // RF 5.4: se programa al abrir caja y se cancela al cerrarla, sin importar desde qué pantalla.
        alcance.launch {
            app.contenedor.cajaRepo.observarAbierta()
                .map { it?.let { c -> c.id to c.abiertaEn } }
                .distinctUntilChanged()
                .collect { abierta ->
                    if (abierta == null) {
                        wm.cancelUniqueWork(TRABAJO_CAJA)
                        NotificationManagerCompat.from(app).cancel(ID_CAJA)
                    } else {
                        val (id, abiertaEn) = abierta
                        val espera = ReglaNotificaciones.esperaCajaAbierta(abiertaEn, app.contenedor.reloj.ahora())
                        wm.enqueueUniqueWork(
                            TRABAJO_CAJA, ExistingWorkPolicy.REPLACE,
                            OneTimeWorkRequestBuilder<CajaAbiertaWorker>()
                                .setInitialDelay(espera, TimeUnit.MILLISECONDS)
                                .setInputData(workDataOf(DATO_CAJA to id))
                                .build(),
                        )
                    }
                }
        }
    }

    private fun crearCanales(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = contexto.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL_CAJA, "Caja abierta", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CANAL_RESPALDO, "Respaldo de información", NotificationManager.IMPORTANCE_DEFAULT))
    }

    internal fun mostrarCajaAbierta(contexto: Context) =
        mostrar(contexto, ID_CAJA, CANAL_CAJA, Mensajes.NOTIFICACION_CAJA_ABIERTA, DestinoNotificacion.CORTE_CAJA)

    internal fun mostrarSinRespaldo(contexto: Context) =
        mostrar(contexto, ID_RESPALDO, CANAL_RESPALDO, Mensajes.NOTIFICACION_SIN_RESPALDO, DestinoNotificacion.INICIO)

    private fun mostrar(contexto: Context, id: Int, canal: String, texto: String, destino: DestinoNotificacion) {
        // Sin el permiso (Android 13+), Android no la mostraría; se omite sin error.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val abrir = Intent(contexto, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_DESTINO, destino.name)
        }
        val pendiente = PendingIntent.getActivity(
            contexto, id, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(contexto, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("Mis Ventas")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .setPriority(if (canal == CANAL_CAJA) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(contexto).notify(id, n)
    }

    /** RF 5.4: a las 12 horas, solo si esa misma caja sigue abierta. */
    class CajaAbiertaWorker(contexto: Context, params: WorkerParameters) : CoroutineWorker(contexto, params) {
        override suspend fun doWork(): Result {
            val c = (applicationContext as MisVentasApp).contenedor
            val id = inputData.getString(DATO_CAJA)
            if (id != null && c.cajaRepo.abierta()?.id == id) mostrarCajaAbierta(applicationContext)
            return Result.success()
        }
    }

    /** RF 7.6: pendientes y 3 días sin sincronizar; se repite cada 24 h mientras siga igual. */
    class SinRespaldoWorker(contexto: Context, params: WorkerParameters) : CoroutineWorker(contexto, params) {
        override suspend fun doWork(): Result {
            val app = applicationContext as MisVentasApp
            val c = app.contenedor
            val prefs = app.getSharedPreferences(ServicioSincronizacion.ARCHIVO, Context.MODE_PRIVATE)
            val ahora = c.reloj.ahora()
            // Sin sesión o con la recuperación (RF 7.4) pendiente no hay cómo sincronizar: no se avisa.
            if (c.auth.estado.value != EstadoSesion.ACTIVA || c.recuperacion.requerida.value) return Result.success()
            // "Nunca se ha sincronizado" cuenta desde el primer arranque con los datos actuales, no desde
            // la instalación: al borrar los datos de la app Android conserva la fecha de instalación.
            val primerArranque = prefs.getLong(CLAVE_PRIMER_ARRANQUE, 0L).takeIf { it > 0 }
                ?: ahora.also { prefs.edit().putLong(CLAVE_PRIMER_ARRANQUE, it).apply() }
            val avisar = ReglaNotificaciones.avisarSinRespaldo(
                pendientes = c.db.syncDao().observarPendientes().first(),
                ultimaSincronizacion = prefs.getLong(ServicioSincronizacion.CLAVE_ULTIMA, 0L).takeIf { it > 0 },
                referenciaSinSincronizar = primerArranque,
                ultimoAviso = prefs.getLong(CLAVE_ULTIMO_AVISO, 0L).takeIf { it > 0 },
                ahora = ahora,
            )
            if (avisar) {
                mostrarSinRespaldo(app)
                prefs.edit().putLong(CLAVE_ULTIMO_AVISO, ahora).apply()
            }
            return Result.success()
        }
    }

    private const val CLAVE_ULTIMO_AVISO = "ultimo_aviso_sin_respaldo"

    /** Se guarda al arrancar la app la primera vez con datos nuevos (ver [iniciar]). */
    private const val CLAVE_PRIMER_ARRANQUE = "primer_arranque"
}
