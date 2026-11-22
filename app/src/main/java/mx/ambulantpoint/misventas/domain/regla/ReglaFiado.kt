package mx.ambulantpoint.misventas.domain.regla

import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.domain.venta.ReglaFiltroVenta

/** Una venta fiada pendiente con lo que ya se le abonó. */
data class SaldoVenta(
    val ventaId: String,
    val fechaHora: Long,
    val totalCentavos: Long,
    val abonadoCentavos: Long,
) {
    val saldoCentavos: Long get() = maxOf(0, totalCentavos - abonadoCentavos)
}

/** Cuánto de un abono va a una venta. */
data class Aplicacion(val ventaId: String, val montoCentavos: Long)

/** Conceptos del corte con fiado (centavos). */
data class ConceptosCorte(
    val fondoCentavos: Long,
    val ventasEfectivoCentavos: Long,
    val abonosEfectivoCentavos: Long = 0,
    val devolucionesCentavos: Long = 0,
)

/**
 * Fiado (desarrollo independiente, Fase 3, `docs/09-ESPEC-FIADO.md`). Cálculos puros con prueba
 * unitaria: saldo, reparto de abonos, pago inicial, aviso de saldo alto y efectivo esperado.
 */
object ReglaFiado {
    const val NOMBRE_MIN = 2
    const val NOMBRE_MAX = 40
    const val DESCRIPCION_MAX = 40
    const val DESCRIPCION_VACIA = "Varios"

    /** Nombre para comparar clientes: sin mayúsculas, acentos ni espacios de más ("  José  Pérez" = "jose perez"). */
    fun normalizarNombre(nombre: String): String =
        ReglaFiltroVenta.normalizar(nombre).replace(Regex("\\s+"), " ")

    /** null si es válido; si no, el mensaje del campo. */
    fun validarNombre(nombre: String): String? {
        val n = nombre.trim().replace(Regex("\\s+"), " ")
        return when {
            n.isEmpty() -> Mensajes.Campo.OBLIGATORIO
            n.length < NOMBRE_MIN || n.length > NOMBRE_MAX -> Mensajes.Fiado.NOMBRE_LONGITUD
            else -> null
        }
    }

    /** Teléfono opcional: vacío o 10 dígitos. */
    fun validarTelefono(telefono: String): String? {
        val t = telefono.trim()
        return if (t.isEmpty() || Regex("^[0-9]{10}$").matches(t)) null else Mensajes.Campo.TELEFONO_NO_VALIDO
    }

    /** Saldo del cliente = suma de lo que falta en cada venta fiada registrada. */
    fun saldo(ventas: List<SaldoVenta>): Long = ventas.sumOf { it.saldoCentavos }

    /**
     * Reparte un abono de la venta fiada más vieja a la más nueva. Falla si el monto es cero o
     * negativo, o si pasa lo que debe el cliente (no hay saldo a favor).
     */
    fun repartir(montoCentavos: Long, ventas: List<SaldoVenta>): Resultado<List<Aplicacion>> {
        if (montoCentavos <= 0) return fallo(Mensajes.Fiado.ABONO_MAYOR_A_CERO)
        val saldo = saldo(ventas)
        if (saldo <= 0) return fallo(Mensajes.Fiado.SIN_SALDO)
        if (montoCentavos > saldo) return fallo(Mensajes.Fiado.abonoMayorAlSaldo(Formato.moneda(saldo)))
        var resta = montoCentavos
        val salida = mutableListOf<Aplicacion>()
        for (v in ventas.sortedWith(compareBy({ it.fechaHora }, { it.ventaId }))) {
            if (resta == 0L) break
            val parte = minOf(resta, v.saldoCentavos)
            if (parte > 0) {
                salida += Aplicacion(v.ventaId, parte)
                resta -= parte
            }
        }
        return Resultado.Exito(salida)
    }

    /** Pago inicial al fiar: de $0 a menos que el total (si paga todo, se cobra normal). */
    fun validarPagoInicial(pagoInicialCentavos: Long, totalCentavos: Long): String? = when {
        pagoInicialCentavos < 0 -> Mensajes.Campo.MONTO_NO_VALIDO
        pagoInicialCentavos >= totalCentavos -> Mensajes.Fiado.PAGO_INICIAL_TOTAL
        else -> null
    }

    /** Lo que debería el cliente después de fiarle esta venta. */
    fun saldoDespues(saldoActual: Long, totalVenta: Long, pagoInicial: Long): Long = saldoActual + totalVenta - pagoInicial

    /** Aviso de saldo alto: solo si el saldo después de fiar PASA el monto de aviso (no bloquea). */
    fun pasaAviso(saldoDespues: Long, avisoCentavos: Long): Boolean = saldoDespues > avisoCentavos

    /** Corte: fondo + ventas en efectivo + abonos en efectivo − devoluciones. */
    fun efectivoEsperado(c: ConceptosCorte): Long =
        c.fondoCentavos + c.ventasEfectivoCentavos + c.abonosEfectivoCentavos - c.devolucionesCentavos

    /**
     * Cancelar un abono equivocado: solo un abono (no devolución) registrado, de la caja que sigue
     * abierta, y si ninguna venta a la que se aplicó está cancelada (ya se devolvió ese dinero).
     */
    fun puedeCancelarAbono(esAbono: Boolean, registrado: Boolean, suCajaAbierta: Boolean, algunaVentaCancelada: Boolean): Boolean =
        esAbono && registrado && suCajaAbierta && !algunaVentaCancelada

    /** Descripción del monto libre: vacía = "Varios"; recortada a [DESCRIPCION_MAX]. */
    fun descripcionLibre(texto: String): String =
        texto.trim().replace(Regex("\\s+"), " ").take(DESCRIPCION_MAX).ifEmpty { DESCRIPCION_VACIA }
}
