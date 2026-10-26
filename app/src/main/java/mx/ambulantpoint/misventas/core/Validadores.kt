package mx.ambulantpoint.misventas.core

import mx.ambulantpoint.misventas.data.local.entity.TipoVenta

/**
 * Validaciones comunes reutilizables (RNF Reusabilidad). Devuelven null si el valor es válido o
 * el texto de la alerta del campo.
 */
object Validadores {
    private val REGEX_CORREO = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val REGEX_TELEFONO = Regex("^[0-9]{10}$")

    fun obligatorio(valor: String?): String? =
        if (valor.isNullOrBlank()) Mensajes.Campo.OBLIGATORIO else null

    /** RF 1.1 / 1.2 / 1.4. */
    fun correo(valor: String?): String? = obligatorio(valor)
        ?: if (!REGEX_CORREO.matches(valor!!.trim())) Mensajes.Campo.CORREO_NO_VALIDO else null

    /** RF 1.1 / 1.6: exactamente 10 dígitos numéricos. */
    fun telefono(valor: String?): String? = obligatorio(valor)
        ?: if (!REGEX_TELEFONO.matches(valor!!.trim())) Mensajes.Campo.TELEFONO_NO_VALIDO else null

    /** RF 1.1: mínimo 8 caracteres con al menos una letra y un número. */
    fun contrasena(valor: String?): String? {
        obligatorio(valor)?.let { return it }
        val v = valor!!
        val ok = v.length >= 8 && v.any { it.isLetter() } && v.any { it.isDigit() }
        return if (ok) null else Mensajes.Campo.CONTRASENA_NO_VALIDA
    }

    fun confirmarContrasena(contrasena: String?, confirmacion: String?): String? = obligatorio(confirmacion)
        ?: if (contrasena != confirmacion) Mensajes.Campo.CONTRASENAS_NO_COINCIDEN else null
}

/** Resultado de leer un número capturado por el vendedor. */
sealed interface Lectura {
    data class Valor(val valor: Long) : Lectura
    data class Error(val mensaje: String) : Lectura
}

/**
 * Convierte texto capturado a centavos / milésimas sin usar Double.
 * Acepta "12", "12.5", "12.50", "$1,234.50" y coma decimal sola ("12,5").
 */
object LectorNumeros {

    /** Monto en pesos con hasta 2 decimales → centavos. [permitirCero] para fondo de caja (RF 5.1). */
    fun monto(texto: String?, permitirCero: Boolean = false): Lectura {
        if (texto.isNullOrBlank()) return Lectura.Error(Mensajes.Campo.OBLIGATORIO)
        val valor = aEntero(texto, decimales = 2) ?: return Lectura.Error(Mensajes.Campo.MONTO_NO_VALIDO)
        if (valor < 0 || (valor == 0L && !permitirCero)) return Lectura.Error(Mensajes.Campo.MONTO_NO_VALIDO)
        return Lectura.Valor(valor)
    }

    /** RF 2.1 / 2.2: precio mayor a cero con hasta dos decimales. */
    fun precio(texto: String?): Lectura = when (val l = monto(texto)) {
        is Lectura.Error -> if (texto.isNullOrBlank()) l else Lectura.Error(Mensajes.Campo.PRECIO_NO_VALIDO)
        is Lectura.Valor -> l
    }

    /**
     * Cantidad según el tipo de venta → milésimas. Por unidad: entero; por peso: hasta 3 decimales.
     * [permitirCero] para la existencia inicial (RF 2.1).
     */
    fun cantidad(texto: String?, tipoVenta: TipoVenta, permitirCero: Boolean = false): Lectura {
        if (texto.isNullOrBlank()) return Lectura.Error(Mensajes.Campo.OBLIGATORIO)
        val milesimas = when (tipoVenta) {
            TipoVenta.UNIDAD -> aEntero(texto, decimales = 0)?.let { Math.multiplyExact(it, Dinero.MILESIMAS_POR_UNIDAD) }
                ?: return Lectura.Error(Mensajes.Campo.CANTIDAD_ENTERA)
            TipoVenta.PESO -> aEntero(texto, decimales = 3)
                ?: return Lectura.Error(Mensajes.Campo.CANTIDAD_KG)
        }
        if (milesimas < 0 || (milesimas == 0L && !permitirCero)) return Lectura.Error(Mensajes.Campo.CANTIDAD_MAYOR_CERO)
        return Lectura.Valor(milesimas)
    }

    /** "12.5" con 2 decimales → 1250. Null si no es un número válido o trae más decimales. */
    internal fun aEntero(texto: String, decimales: Int): Long? {
        var t = texto.trim().replace(" ", "").removePrefix("$")
        t = if (t.contains(',') && !t.contains('.')) {
            // Coma decimal solo si no parece separador de miles ("1,234")
            if (Regex("^-?\\d{1,3}(,\\d{3})+$").matches(t)) t.replace(",", "") else t.replace(',', '.')
        } else {
            t.replace(",", "")
        }
        val m = Regex("^(-?)(\\d*)(?:\\.(\\d*))?$").matchEntire(t) ?: return null
        val (signo, enteros, fraccion) = m.destructured
        if (enteros.isEmpty() && fraccion.isEmpty()) return null
        if (fraccion.length > decimales) return null
        if (enteros.length > 12) return null
        var factor = 1L
        repeat(decimales) { factor *= 10 }
        val parteEntera = (enteros.ifEmpty { "0" }).toLong() * factor
        val parteFraccion = if (decimales == 0) 0L else fraccion.padEnd(decimales, '0').toLong()
        val v = parteEntera + parteFraccion
        return if (signo == "-") -v else v
    }
}
