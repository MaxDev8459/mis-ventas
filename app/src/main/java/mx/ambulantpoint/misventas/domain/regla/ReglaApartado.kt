package mx.ambulantpoint.misventas.domain.regla

import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import java.time.Instant
import java.time.ZoneId

/** Cuándo llega cada notificación de un apartado. */
enum class AvisoApartado { QUINCE_MINUTOS, HORA_EXACTA }

/** Cómo se ve un apartado pendiente en la lista. */
enum class MomentoApartado { ATRASADO, HOY, DESPUES }

/** Un producto de un apartado, para los textos. */
data class ProductoApartado(val nombre: String, val tipoVenta: TipoVenta, val cantidadMilesimas: Long)

/**
 * Apartados (Fase 4, `docs/10-ESPEC-APARTADOS.md`). Cálculos puros con prueba unitaria: lo disponible,
 * validaciones, cuáles se liberan al cerrar la caja, horas y textos de las notificaciones.
 */
object ReglaApartado {
    const val LUGAR_MAX = 60
    const val MINUTOS_ANTES = 15L
    private const val MS_MINUTO = 60_000L

    /** Lo que se puede vender: existencia total − lo apartado (nunca negativo). */
    fun disponible(existenciaMilesimas: Long, apartadoMilesimas: Long): Long = maxOf(0, existenciaMilesimas - apartadoMilesimas)

    fun validarLugar(lugar: String): String? {
        val l = lugar.trim()
        return when {
            l.isEmpty() -> Mensajes.Campo.OBLIGATORIO
            l.length > LUGAR_MAX -> Mensajes.Apartado.LUGAR_LARGO
            else -> null
        }
    }

    /** La entrega no puede ser en el pasado. */
    fun validarEntrega(entregaEn: Long?, ahora: Long): String? = when {
        entregaEn == null -> Mensajes.Campo.OBLIGATORIO
        entregaEn <= ahora -> Mensajes.Apartado.ENTREGA_PASADA
        else -> null
    }

    /**
     * Al cerrar la caja se liberan los pendientes con entrega ese día o antes. "Ese día" = el día (hora local)
     * en que se abrió la caja; devuelve el inicio del día siguiente: se liberan los que tengan entrega ANTES de él.
     */
    fun limiteLiberacion(abiertaEn: Long, zona: ZoneId): Long =
        Instant.ofEpochMilli(abiertaEn).atZone(zona).toLocalDate().plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()

    fun seLibera(entregaEn: Long, abiertaEn: Long, zona: ZoneId): Boolean = entregaEn < limiteLiberacion(abiertaEn, zona)

    /** Momentos de las notificaciones que aún no pasan: 15 min antes y a la hora exacta. */
    fun avisos(entregaEn: Long, ahora: Long): List<Pair<AvisoApartado, Long>> = listOf(
        AvisoApartado.QUINCE_MINUTOS to entregaEn - MINUTOS_ANTES * MS_MINUTO,
        AvisoApartado.HORA_EXACTA to entregaEn,
    ).filter { it.second > ahora }

    fun momento(entregaEn: Long, ahora: Long, zona: ZoneId): MomentoApartado {
        if (entregaEn < ahora) return MomentoApartado.ATRASADO
        val dia = { ms: Long -> Instant.ofEpochMilli(ms).atZone(zona).toLocalDate() }
        return if (dia(entregaEn) == dia(ahora)) MomentoApartado.HOY else MomentoApartado.DESPUES
    }

    /** "3 pzas Carlota, 0.500 kg Queso". */
    fun productos(lista: List<ProductoApartado>): String =
        lista.joinToString(", ") { "${Formato.cantidad(it.cantidadMilesimas, it.tipoVenta)} ${it.nombre}" }

    /** Texto de la notificación, con todos los datos de la entrega. */
    fun textoAviso(
        aviso: AvisoApartado,
        nombre: String,
        productos: List<ProductoApartado>,
        lugar: String,
        horaTexto: String,
        totalCentavos: Long,
        telefono: String? = null,
    ): String {
        val inicio = when (aviso) {
            AvisoApartado.QUINCE_MINUTOS -> "En 15 minutos tienes que entregar"
            AvisoApartado.HORA_EXACTA -> "Es hora de entregar"
        }
        val tel = telefono?.takeIf { it.isNotBlank() }?.let { " (tel. $it)" }.orEmpty()
        return "$inicio a $nombre$tel: ${productos(productos)} en $lugar a las $horaTexto. Total ${Formato.moneda(totalCentavos)}"
    }

    /** Merma o ajuste: no puede dejar la existencia por debajo de lo apartado (respuesta 8). */
    fun validarDisminucion(
        existenciaMilesimas: Long,
        disminuirMilesimas: Long,
        apartadoMilesimas: Long,
        nombre: String,
        tipoVenta: TipoVenta,
    ): String? =
        if (apartadoMilesimas > 0 && existenciaMilesimas - disminuirMilesimas < apartadoMilesimas) {
            Mensajes.Apartado.ajusteDebajoDeApartado(Formato.cantidad(apartadoMilesimas, tipoVenta), nombre)
        } else {
            null
        }
}
