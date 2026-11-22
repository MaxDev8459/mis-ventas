package mx.ambulantpoint.misventas.data.notificaciones

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.MainActivity
import mx.ambulantpoint.misventas.MisVentasApp
import mx.ambulantpoint.misventas.R
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.data.local.dao.ApartadoConDetalles
import mx.ambulantpoint.misventas.data.local.entity.EstadoApartado
import mx.ambulantpoint.misventas.domain.regla.AvisoApartado
import mx.ambulantpoint.misventas.domain.regla.ProductoApartado
import mx.ambulantpoint.misventas.domain.regla.ReglaApartado

/**
 * Apartados (Fase 4, respuesta 7): dos notificaciones por apartado pendiente, 15 minutos antes y a la hora
 * exacta, aunque la app esté cerrada. Usa alarmas exactas del sistema (WorkManager puede retrasarse).
 *
 * Se reprograman cada vez que cambian los apartados pendientes (la app observa la lista), al abrir la app y al
 * reiniciar el teléfono. Una alarma vieja no hace daño: al sonar se revisa que el apartado siga pendiente y con
 * la misma hora; si no, no se muestra nada.
 */
object AlarmasApartados {
    private const val CANAL = "apartados"
    const val EXTRA_APARTADO = "apartadoId"
    private const val EXTRA_AVISO = "aviso"
    private const val EXTRA_ENTREGA = "entregaEn"
    private const val ACCION = "mx.ambulantpoint.misventas.AVISO_APARTADO"

    /** Se llama al iniciar la app: crea el canal y programa mientras la lista de pendientes cambie. */
    fun iniciar(app: MisVentasApp, alcance: CoroutineScope) {
        crearCanal(app)
        alcance.launch {
            app.contenedor.apartadoRepo.observarPendientes().collect { programar(app, it) }
        }
    }

    private fun crearCanal(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL, "Entregas de apartados", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun codigo(apartadoId: String, aviso: AvisoApartado) = (apartadoId + aviso.name).hashCode()

    private fun intencion(contexto: Context, apartadoId: String, aviso: AvisoApartado, entregaEn: Long): PendingIntent {
        val i = Intent(contexto, AvisoApartadoReceiver::class.java).apply {
            action = ACCION
            putExtra(EXTRA_APARTADO, apartadoId)
            putExtra(EXTRA_AVISO, aviso.name)
            putExtra(EXTRA_ENTREGA, entregaEn)
        }
        return PendingIntent.getBroadcast(
            contexto, codigo(apartadoId, aviso), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Programa (o reemplaza) las alarmas de cada pendiente que aún no pasan. */
    fun programar(contexto: Context, pendientes: List<ApartadoConDetalles>) {
        val am = contexto.getSystemService(AlarmManager::class.java) ?: return
        val ahora = System.currentTimeMillis()
        val exactas = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        for (p in pendientes) {
            for ((aviso, momento) in ReglaApartado.avisos(p.apartado.entregaEn, ahora)) {
                val pi = intencion(contexto, p.apartado.id, aviso, p.apartado.entregaEn)
                if (exactas) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pi)
                } else {
                    // Sin permiso de alarmas exactas: llega cerca de la hora (Android decide el momento).
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, momento, pi)
                }
            }
        }
    }

    /** Al sonar: muestra la notificación solo si el apartado sigue pendiente y con la misma hora. */
    internal suspend fun avisar(contexto: Context, apartadoId: String, aviso: AvisoApartado, entregaEn: Long) {
        val app = contexto.applicationContext as MisVentasApp
        val dao = app.contenedor.db.apartadoDao()
        val a = dao.porId(apartadoId) ?: return
        if (a.estado != EstadoApartado.PENDIENTE || a.entregaEn != entregaEn) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        crearCanal(contexto)
        val productos = dao.detalles(apartadoId).map { ProductoApartado(it.nombreProducto, it.tipoVenta, it.cantidadMilesimas) }
        val texto = ReglaApartado.textoAviso(aviso, a.nombre, productos, a.lugar, Formato.hora(a.entregaEn), a.totalCentavos, a.telefono)
        val abrir = Intent(contexto, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(Recordatorios.EXTRA_DESTINO, DestinoNotificacion.APARTADO.name)
            putExtra(EXTRA_APARTADO, apartadoId)
        }
        val pendiente = PendingIntent.getActivity(
            contexto, codigo(apartadoId, aviso), abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val titulo = if (aviso == AvisoApartado.QUINCE_MINUTOS) "Entrega en 15 minutos" else "Hora de entregar"
        val n = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(contexto).notify(codigo(apartadoId, aviso), n)
    }

    /** Lee los extras de la alarma. */
    internal fun leer(intent: Intent): Triple<String, AvisoApartado, Long>? {
        val id = intent.getStringExtra(EXTRA_APARTADO) ?: return null
        val aviso = AvisoApartado.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_AVISO) } ?: return null
        return Triple(id, aviso, intent.getLongExtra(EXTRA_ENTREGA, 0))
    }
}

/** Suena la alarma de un apartado. */
class AvisoApartadoReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val (id, aviso, entregaEn) = AlarmasApartados.leer(intent) ?: return
        val terminar = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmasApartados.avisar(contexto, id, aviso, entregaEn)
            } finally {
                terminar.finish()
            }
        }
    }
}

/** Al reiniciar el teléfono o actualizar la app, Android borra las alarmas: se vuelven a programar. */
class ReprogramarApartadosReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        val app = contexto.applicationContext as MisVentasApp
        val terminar = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmasApartados.programar(contexto, app.contenedor.apartadoRepo.pendientes())
            } finally {
                terminar.finish()
            }
        }
    }
}
