package mx.ambulantpoint.misventas.domain.regla

/**
 * Calculadora de billetes y monedas para el corte de caja: el vendedor captura cuántas piezas
 * tiene de cada denominación y la suma llena el "Efectivo contado". No se guarda el desglose.
 */
object ReglaConteoEfectivo {
    /** Denominaciones en centavos, de mayor a menor (el $20 cuenta billete y moneda juntos). */
    val BILLETES = listOf(100_000L, 50_000L, 20_000L, 10_000L, 5_000L, 2_000L)
    val MONEDAS = listOf(1_000L, 500L, 200L, 100L, 50L)
    val DENOMINACIONES = BILLETES + MONEDAS

    /** Piezas capturadas como texto; vacío = 0. Null si alguna no es un número entero de piezas. */
    fun piezas(texto: String): Long? {
        val t = texto.trim()
        if (t.isEmpty()) return 0
        if (t.length > 6 || !t.all { it.isDigit() }) return null
        return t.toLong()
    }

    /** Total en centavos; las denominaciones sin captura cuentan como 0. Null si alguna captura no es válida. */
    fun total(capturas: Map<Long, String>): Long? {
        var suma = 0L
        for (d in DENOMINACIONES) {
            val n = piezas(capturas[d].orEmpty()) ?: return null
            suma += d * n
        }
        return suma
    }

    /** Monto para el campo "Efectivo contado" (sin separador de miles): 123450 → "1234.50". */
    fun comoTexto(centavos: Long): String = "${centavos / 100}.${(centavos % 100).toString().padStart(2, '0')}"
}
