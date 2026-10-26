package mx.ambulantpoint.misventas.core

import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Formatos de la RNF de interfaz: montos "$0,000.00" y cantidades con unidad (pzas o kg). */
object Formato {

    /** 123456 → "$1,234.56"; -1000 → "-$10.00". */
    fun moneda(centavos: Long): String {
        val signo = if (centavos < 0) "-" else ""
        val valor = abs(centavos)
        val pesos = agruparMiles(valor / 100)
        val cent = (valor % 100).toString().padStart(2, '0')
        return "$signo\$$pesos.$cent"
    }

    /** Unidad de un tipo de venta, tal como la escribe el DFR. */
    fun unidad(tipoVenta: TipoVenta): String = when (tipoVenta) {
        TipoVenta.UNIDAD -> "pzas"
        TipoVenta.PESO -> "kg"
    }

    /** Solo el número: 3000 (UNIDAD) → "3"; 250 (PESO) → "0.250". */
    fun numeroCantidad(milesimas: Long, tipoVenta: TipoVenta): String = when (tipoVenta) {
        TipoVenta.UNIDAD -> agruparMiles(milesimas / Dinero.MILESIMAS_POR_UNIDAD)
        TipoVenta.PESO -> {
            val signo = if (milesimas < 0) "-" else ""
            val v = abs(milesimas)
            "$signo${agruparMiles(v / 1000)}.${(v % 1000).toString().padStart(3, '0')}"
        }
    }

    /** Con unidad: "3 pzas", "0.250 kg". */
    fun cantidad(milesimas: Long, tipoVenta: TipoVenta): String =
        "${numeroCantidad(milesimas, tipoVenta)} ${unidad(tipoVenta)}"

    /** RF 4.4: cantidad con signo + o −: "+5 pzas", "−0.250 kg". */
    fun cantidadConSigno(milesimas: Long, tipoVenta: TipoVenta): String {
        val signo = if (milesimas < 0) "−" else "+"
        return signo + cantidad(abs(milesimas), tipoVenta)
    }

    private val LOCALE_MX: Locale = Locale.forLanguageTag("es-MX")

    /** 1790000000000 → "26/09/2026 09:46" (hora local del teléfono). */
    fun fechaHora(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", LOCALE_MX).format(Date(ms))

    fun fecha(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", LOCALE_MX).format(Date(ms))

    fun hora(ms: Long): String = SimpleDateFormat("HH:mm", LOCALE_MX).format(Date(ms))

    private fun agruparMiles(n: Long): String {
        val s = abs(n).toString()
        val conComas = s.reversed().chunked(3).joinToString(",").reversed()
        return if (n < 0) "-$conComas" else conComas
    }
}
