package mx.ambulantpoint.misventas.domain.regla

import java.util.Calendar
import java.util.TimeZone

/** Módulo 6: periodos disponibles (Hoy, Esta semana, Este mes y Rango personalizado). */
enum class TipoPeriodo(val etiqueta: String) {
    HOY("Hoy"),
    SEMANA("Esta semana"),
    MES("Este mes"),
    RANGO("Rango personalizado"),
}

/** Periodo en hora local: [desde, hasta] en epoch ms, ambos incluidos. */
data class Periodo(val tipo: TipoPeriodo, val desde: Long, val hasta: Long)

/**
 * Límites de los periodos del Módulo 6.
 * - La semana empieza en lunes. El DFR no lo especifica: es decisión del equipo (costumbre en México).
 * - "Esta semana" y "Este mes" van del primer día a las 23:59:59.999 de hoy (no hay ventas futuras).
 */
object ReglaPeriodos {
    private fun cal(ms: Long, zona: TimeZone) = Calendar.getInstance(zona).apply { timeInMillis = ms }

    fun inicioDelDia(ms: Long, zona: TimeZone = TimeZone.getDefault()): Long = cal(ms, zona).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Último milisegundo del día (respeta días de 23 o 25 h por cambio de horario). */
    fun finDelDia(ms: Long, zona: TimeZone = TimeZone.getDefault()): Long =
        cal(inicioDelDia(ms, zona), zona).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis - 1

    fun calcular(tipo: TipoPeriodo, ahora: Long, zona: TimeZone = TimeZone.getDefault()): Periodo {
        val fin = finDelDia(ahora, zona)
        val inicio = when (tipo) {
            TipoPeriodo.HOY, TipoPeriodo.RANGO -> inicioDelDia(ahora, zona)
            TipoPeriodo.SEMANA -> cal(inicioDelDia(ahora, zona), zona).apply {
                // Calendar: domingo = 1, lunes = 2 … sábado = 7. Días a retroceder hasta el lunes:
                val retroceso = (get(Calendar.DAY_OF_WEEK) + 5) % 7
                add(Calendar.DAY_OF_YEAR, -retroceso)
            }.timeInMillis
            TipoPeriodo.MES -> cal(inicioDelDia(ahora, zona), zona).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        }
        return Periodo(tipo, inicio, fin)
    }

    /** Rango personalizado: del inicio de [diaInicial] al final de [diaFinal] (se ordenan si vienen al revés). */
    fun rango(diaInicial: Long, diaFinal: Long, zona: TimeZone = TimeZone.getDefault()): Periodo {
        val (a, b) = if (diaInicial <= diaFinal) diaInicial to diaFinal else diaFinal to diaInicial
        return Periodo(TipoPeriodo.RANGO, inicioDelDia(a, zona), finDelDia(b, zona))
    }
}

/** RF 6.1: ticket promedio = total vendido ÷ número de ventas, en centavos (mitad hacia arriba). */
object ReglaTicketPromedio {
    fun calcular(totalVendidoCentavos: Long, numeroVentas: Int): Long {
        if (numeroVentas <= 0) return 0
        require(totalVendidoCentavos >= 0) { "El total vendido no puede ser negativo" }
        return (totalVendidoCentavos + numeroVentas / 2) / numeroVentas
    }
}
