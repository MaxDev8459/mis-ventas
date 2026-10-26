package mx.ambulantpoint.misventas.data.repository

import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.CorteFormaPago
import mx.ambulantpoint.misventas.data.local.entity.EstadoCaja

/** Resumen del corte (RF 5.2 / 5.5). */
data class CorteCaja(
    val caja: Caja,
    val porFormaPago: List<CorteFormaPago>,
) {
    val fondoCentavos get() = caja.fondoCentavos
    val totalVendidoCentavos get() = caja.totalVendidoCentavos ?: 0L
    val numeroVentas get() = caja.numeroVentas ?: 0
    val descuentosCentavos get() = caja.descuentosCentavos ?: 0L
    val efectivoEsperadoCentavos get() = caja.efectivoEsperadoCentavos ?: 0L
    val efectivoContadoCentavos get() = caja.efectivoContadoCentavos ?: 0L
    /** Positivo = sobrante, negativo = faltante, cero = sin diferencia. */
    val diferenciaCentavos get() = caja.diferenciaCentavos ?: 0L
}

/** RF 5.1 – 5.5. */
class CajaRepo(private val db: MisVentasDb, private val reloj: Reloj) {
    private val dao = db.cajaDao()

    fun observarAbierta(): Flow<Caja?> = dao.observarAbierta()
    suspend fun abierta(): Caja? = dao.abierta()

    fun observarCortes(desde: Long = 0, hasta: Long = Long.MAX_VALUE): Flow<List<Caja>> =
        dao.observarCerradas(desde, hasta)

    fun observarCorteFormaPago(cajaId: String): Flow<List<CorteFormaPago>> = dao.observarCorteFormaPago(cajaId)

    /** RF 5.1: fondo ≥ 0 y solo una caja abierta a la vez. */
    suspend fun abrir(fondoCentavos: Long): Resultado<Caja> = db.transaccion {
        if (fondoCentavos < 0) fallar(FallaDatos.CantidadInvalida)
        if (dao.abierta() != null) fallar(FallaDatos.CajaYaAbierta)
        val ahora = reloj.ahora()
        val caja = Caja(
            id = nuevoId(), estado = EstadoCaja.ABIERTA, fondoCentavos = fondoCentavos,
            abiertaEn = ahora, creadoEn = ahora, actualizadoEn = ahora,
        )
        dao.insertar(caja)
        caja
    }

    /**
     * RF 5.2: calcula y guarda el corte de la caja abierta (no la cierra).
     * Efectivo esperado = fondo + ventas en efectivo; diferencia = contado − esperado.
     */
    suspend fun calcularCorte(efectivoContadoCentavos: Long): Resultado<CorteCaja> = db.transaccion {
        if (efectivoContadoCentavos < 0) fallar(FallaDatos.CantidadInvalida)
        val caja = dao.abierta() ?: fallar(FallaDatos.SinCajaAbierta)
        val ahora = reloj.ahora()
        val resumen = dao.resumenVentas(caja.id)
        val porForma = dao.totalesPorFormaPago(caja.id)
        val ventasEfectivo = porForma.filter { it.esEfectivo }.sumOf { it.totalCentavos }
        val esperado = caja.fondoCentavos + ventasEfectivo

        val conCorte = caja.copy(
            corteCalculadoEn = ahora,
            corteVigente = true,
            totalVendidoCentavos = resumen.totalVendidoCentavos,
            numeroVentas = resumen.numeroVentas,
            descuentosCentavos = resumen.descuentosCentavos,
            efectivoEsperadoCentavos = esperado,
            efectivoContadoCentavos = efectivoContadoCentavos,
            diferenciaCentavos = efectivoContadoCentavos - esperado,
            actualizadoEn = ahora,
            pendienteSync = true,
        )
        dao.actualizar(conCorte)

        val filas = porForma.map {
            CorteFormaPago(
                cajaId = caja.id, formaPagoId = it.formaPagoId, nombreFormaPago = it.nombreFormaPago,
                numeroVentas = it.numeroVentas, totalCentavos = it.totalCentavos, creadoEn = ahora,
            )
        }
        dao.borrarCorteFormaPago(caja.id)
        if (filas.isNotEmpty()) dao.insertarCorteFormaPago(filas)
        CorteCaja(conCorte, filas)
    }

    /** RF 5.3: requiere corte vigente (sin ventas ni cancelaciones posteriores). */
    suspend fun cerrar(): Resultado<Caja> = db.transaccion {
        val caja = dao.abierta() ?: fallar(FallaDatos.SinCajaAbierta)
        if (!caja.corteVigente) fallar(FallaDatos.CorteNoVigente)
        val ahora = reloj.ahora()
        val cerrada = caja.copy(estado = EstadoCaja.CERRADA, cerradaEn = ahora, actualizadoEn = ahora, pendienteSync = true)
        dao.actualizar(cerrada)
        cerrada
    }
}
