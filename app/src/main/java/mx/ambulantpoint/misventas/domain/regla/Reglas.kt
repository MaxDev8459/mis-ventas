package mx.ambulantpoint.misventas.domain.regla

import mx.ambulantpoint.misventas.core.Dinero
import mx.ambulantpoint.misventas.core.Falla
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.entity.TipoDescuento
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta

/** Falla con el mensaje listo para mostrar. */
data class FallaMensaje(val mensaje: String) : Falla

/** Falla de captura: campo → alerta (RNF Usabilidad: indicar claramente el campo con error). */
data class FallaCampos(val errores: Map<String, String>) : Falla

/** RF 3.6: cálculo del descuento en pesos. */
object ReglaDescuento {
    /** 1 % expresado en centésimas de punto porcentual. */
    const val UN_POR_CIENTO = 100L
    const val CIEN_POR_CIENTO = 10_000L
    const val TOTAL_MINIMO_CENTAVOS = 100L

    /**
     * @param valor porcentaje en centésimas (1250 = 12.50 %) o monto fijo en centavos.
     * @return descuento en centavos, o el mensaje del DFR si no es válido.
     */
    fun calcular(subtotalCentavos: Long, tipo: TipoDescuento, valor: Long): Resultado<Long> {
        val descuento = when (tipo) {
            TipoDescuento.PORCENTAJE -> {
                if (valor < UN_POR_CIENTO || valor >= CIEN_POR_CIENTO) return fallo(Mensajes.DESCUENTO_NO_VALIDO)
                // subtotal × porcentaje ÷ 100, redondeado a centavos (mitad hacia arriba)
                (subtotalCentavos * valor + CIEN_POR_CIENTO / 2) / CIEN_POR_CIENTO
            }
            TipoDescuento.MONTO_FIJO -> {
                if (valor <= 0 || valor >= subtotalCentavos) return fallo(Mensajes.DESCUENTO_NO_VALIDO)
                valor
            }
        }
        if (subtotalCentavos - descuento < TOTAL_MINIMO_CENTAVOS) return fallo(Mensajes.DESCUENTO_TOTAL_MINIMO)
        return Resultado.Exito(descuento)
    }
}

/** RF 3.2: cambio en efectivo. */
object ReglaCobro {
    fun cambio(totalCentavos: Long, recibidoCentavos: Long): Resultado<Long> =
        if (recibidoCentavos < totalCentavos) fallo(Mensajes.MONTO_RECIBIDO_MENOR)
        else Resultado.Exito(recibidoCentavos - totalCentavos)
}

/** RF 4.5 / 6.5: existencia baja según la venta promedio diaria. */
object ReglaExistenciaBaja {
    const val DIAS = 7
    const val UMBRAL_UNIDAD_MILESIMAS = 5 * Dinero.MILESIMAS_POR_UNIDAD   // 5 pzas
    const val UMBRAL_PESO_MILESIMAS = 1 * Dinero.MILESIMAS_POR_UNIDAD     // 1 kg

    /**
     * @param diasConVentas días (máx. 7) con ventas no canceladas del producto.
     * @param vendidoMilesimas lo vendido en esos días.
     */
    fun esBaja(existenciaMilesimas: Long, tipoVenta: TipoVenta, diasConVentas: Int, vendidoMilesimas: Long): Boolean {
        if (diasConVentas < DIAS) {
            val umbral = if (tipoVenta == TipoVenta.UNIDAD) UMBRAL_UNIDAD_MILESIMAS else UMBRAL_PESO_MILESIMAS
            return existenciaMilesimas <= umbral
        }
        // existencia ≤ (vendido ÷ 7) × 2  ⇔  existencia × 7 ≤ vendido × 2 (sin perder decimales)
        return existenciaMilesimas * DIAS <= vendidoMilesimas * 2
    }
}

/** Tipo de diferencia del corte (RF 5.2). */
enum class TipoDiferencia(val etiqueta: String) {
    SOBRANTE(Mensajes.SOBRANTE),
    FALTANTE(Mensajes.FALTANTE),
    SIN_DIFERENCIA(Mensajes.SIN_DIFERENCIA),
}

/** RF 5.2: conciliación del efectivo. */
object ReglaCorte {
    fun efectivoEsperado(fondoCentavos: Long, ventasEfectivoCentavos: Long): Long = fondoCentavos + ventasEfectivoCentavos

    fun diferencia(contadoCentavos: Long, esperadoCentavos: Long): Long = contadoCentavos - esperadoCentavos

    fun tipoDiferencia(diferenciaCentavos: Long): TipoDiferencia = when {
        diferenciaCentavos > 0 -> TipoDiferencia.SOBRANTE
        diferenciaCentavos < 0 -> TipoDiferencia.FALTANTE
        else -> TipoDiferencia.SIN_DIFERENCIA
    }
}

internal fun fallo(mensaje: String): Resultado.Fallo = Resultado.Fallo(FallaMensaje(mensaje))
