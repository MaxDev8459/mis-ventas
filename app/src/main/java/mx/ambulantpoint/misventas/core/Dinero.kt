package mx.ambulantpoint.misventas.core

import java.util.Locale

/**
 * Unidades internas:
 * - Dinero en centavos (Long): $12.50 = 1250.
 * - Cantidades en milésimas (Long): 1 pza = 1000; 0.250 kg = 250.
 */
object Dinero {
    const val MILESIMAS_POR_UNIDAD = 1000L

    /**
     * Importe de una línea de venta: cantidad × precio, redondeado a centavos (mitad hacia arriba).
     * Ej.: 0.250 kg × $86.00/kg = 250 × 8600 / 1000 = 2150 centavos = $21.50.
     */
    fun importe(cantidadMilesimas: Long, precioCentavos: Long): Long {
        require(cantidadMilesimas >= 0 && precioCentavos >= 0) { "Cantidad y precio no pueden ser negativos" }
        return Math.addExact(Math.multiplyExact(cantidadMilesimas, precioCentavos), 500L) / MILESIMAS_POR_UNIDAD
    }
}

/** Nombre normalizado para comparar duplicados: sin espacios al inicio/fin y sin distinguir mayúsculas. */
fun normalizarNombre(nombre: String): String = nombre.trim().lowercase(Locale.ROOT)
