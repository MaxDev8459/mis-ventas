package mx.ambulantpoint.misventas.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash de contraseña con PBKDF2-HMAC-SHA256 y sal aleatoria. Temporal del MVP local: el DFR indica
 * que la contraseña la resguarda Firebase Authentication; esto se elimina al integrarlo (Sprint 2).
 */
object Hasheador {
    private const val ALGORITMO = "PBKDF2WithHmacSHA256"
    const val ITERACIONES = 120_000
    private const val BITS = 256
    private const val BYTES_SAL = 16

    /** Texto guardado: "iteraciones:salBase64:hashBase64". */
    fun hashear(contrasena: String, iteraciones: Int = ITERACIONES): String {
        val sal = ByteArray(BYTES_SAL).also { SecureRandom().nextBytes(it) }
        val hash = derivar(contrasena, sal, iteraciones)
        val b64 = Base64.getEncoder()
        return "$iteraciones:${b64.encodeToString(sal)}:${b64.encodeToString(hash)}"
    }

    /** Comparación en tiempo constante. */
    fun verificar(contrasena: String, guardado: String): Boolean {
        val partes = guardado.split(":")
        if (partes.size != 3) return false
        val iteraciones = partes[0].toIntOrNull() ?: return false
        val b64 = Base64.getDecoder()
        val sal = runCatching { b64.decode(partes[1]) }.getOrNull() ?: return false
        val esperado = runCatching { b64.decode(partes[2]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(esperado, derivar(contrasena, sal, iteraciones))
    }

    private fun derivar(contrasena: String, sal: ByteArray, iteraciones: Int): ByteArray {
        val spec = PBEKeySpec(contrasena.toCharArray(), sal, iteraciones, BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
