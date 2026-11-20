package mx.ambulantpoint.misventas.domain.regla

import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.normalizarNombre

/**
 * Solicitud de cambio SC-01 (20-nov-2026, aprobada por el cliente): datos para recibir
 * transferencias. Una sola cuenta: CLABE interbancaria, beneficiario y banco.
 */
data class DatosTransferencia(val clabe: String, val beneficiario: String, val banco: String)

object ReglaTransferencia {
    const val LARGO_CLABE = 18

    /** Solo los dígitos (el vendedor puede capturarla con espacios o guiones). */
    fun soloDigitos(texto: String): String = texto.filter { it.isDigit() }

    /**
     * Dígito verificador de la CLABE (estándar de Banxico): los primeros 17 dígitos se multiplican
     * por 3, 7, 1, 3, 7, 1…; se suman las unidades de cada producto; el verificador es
     * (10 − suma mod 10) mod 10.
     */
    fun digitoVerificador(primeros17: String): Int {
        require(primeros17.length == LARGO_CLABE - 1 && primeros17.all { it.isDigit() })
        val pesos = intArrayOf(3, 7, 1)
        val suma = primeros17.withIndex().sumOf { (i, c) -> (c.digitToInt() * pesos[i % 3]) % 10 }
        return (10 - suma % 10) % 10
    }

    fun clabeValida(clabe: String): Boolean =
        clabe.length == LARGO_CLABE && clabe.all { it.isDigit() } &&
            digitoVerificador(clabe.take(LARGO_CLABE - 1)) == clabe.last().digitToInt()

    /** Banco sugerido por los 3 primeros dígitos (clave de Banxico). El vendedor puede cambiarlo. */
    fun bancoSugerido(clabe: String): String? = if (clabe.length >= 3) BANCOS[clabe.take(3)] else null

    /** "032180000118359719" → "0321 8000 0118 3597 19", para leerla en voz alta o de la pantalla. */
    fun clabeEnGrupos(clabe: String): String = clabe.chunked(4).joinToString(" ")

    /**
     * La ventana de datos se ofrece con las formas de pago de transferencia (por nombre: el vendedor
     * define las suyas, RF 3.5), p. ej. "Transferencia" o "Transferencia SPEI".
     */
    fun esTransferencia(nombreFormaPago: String): Boolean {
        val n = normalizarNombre(nombreFormaPago)
        return "transfer" in n || "spei" in n
    }

    /** Texto para compartir por WhatsApp, SMS, etc. La CLABE va sin espacios para copiarla directo. */
    fun textoParaCompartir(datos: DatosTransferencia, totalCentavos: Long): String =
        "Datos para transferencia\n" +
            "Banco: ${datos.banco}\n" +
            "Beneficiario: ${datos.beneficiario}\n" +
            "CLABE: ${datos.clabe}\n" +
            "Total: ${Formato.moneda(totalCentavos)}"

    /** Claves de Banxico de los bancos más comunes. */
    private val BANCOS = mapOf(
        "002" to "Banamex", "012" to "BBVA", "014" to "Santander", "021" to "HSBC", "030" to "BanBajío",
        "036" to "Inbursa", "042" to "Mifel", "044" to "Scotiabank", "058" to "Banregio", "062" to "Afirme",
        "072" to "Banorte", "127" to "Banco Azteca", "130" to "Compartamos", "137" to "BanCoppel",
        "638" to "Nu México", "646" to "STP", "722" to "Mercado Pago",
    )
}
