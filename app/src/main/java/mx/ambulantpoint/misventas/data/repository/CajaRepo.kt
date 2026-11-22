package mx.ambulantpoint.misventas.data.repository

import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.CorteFormaPago
import mx.ambulantpoint.misventas.data.local.entity.EstadoCaja
import mx.ambulantpoint.misventas.domain.puertos.PuertoCaja
import mx.ambulantpoint.misventas.domain.regla.ReglaCorte
import mx.ambulantpoint.misventas.domain.regla.ReglaFiado
import mx.ambulantpoint.misventas.domain.regla.ConceptosCorte
import mx.ambulantpoint.misventas.data.local.dao.TotalFormaPago

/** Resumen del corte (RF 5.2 / 5.5). */
data class CorteCaja(
    val caja: Caja,
    val porFormaPago: List<CorteFormaPago>,
    /** Fiado (v7): abonos recibidos en la caja por forma de pago (`numeroVentas` = número de abonos). */
    val abonosPorForma: List<TotalFormaPago> = emptyList(),
) {
    val fiadoCentavos get() = caja.fiadoCentavos ?: 0L
    val abonosCentavos get() = caja.abonosCentavos ?: 0L
    val devolucionesCentavos get() = caja.devolucionesCentavos ?: 0L
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
class CajaRepo(private val db: MisVentasDb, private val reloj: Reloj) : PuertoCaja {
    private val dao = db.cajaDao()
    private val fiadoDao = db.fiadoDao()

    /** Fiado (v7): abonos de una caja por forma de pago (no cambian después de cerrar la caja). */
    fun observarAbonosPorForma(cajaId: String): Flow<List<TotalFormaPago>> = fiadoDao.observarAbonosPorFormaDeCaja(cajaId)

    fun observarAbierta(): Flow<Caja?> = dao.observarAbierta()
    suspend fun abierta(): Caja? = dao.abierta()

    fun observarCortes(desde: Long = 0, hasta: Long = Long.MAX_VALUE): Flow<List<Caja>> =
        dao.observarCerradas(desde, hasta)

    fun observarCorteFormaPago(cajaId: String): Flow<List<CorteFormaPago>> = dao.observarCorteFormaPago(cajaId)

    /** RF 5.1: fondo ≥ 0 y solo una caja abierta a la vez. */
    override suspend fun abrir(fondoCentavos: Long): Resultado<Caja> = db.transaccion {
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
     * Efectivo esperado = fondo + ventas en efectivo + abonos en efectivo − devoluciones; diferencia = contado − esperado.
     */
    override suspend fun calcularCorte(efectivoContadoCentavos: Long): Resultado<CorteCaja> = db.transaccion {
        if (efectivoContadoCentavos < 0) fallar(FallaDatos.CantidadInvalida)
        val caja = dao.abierta() ?: fallar(FallaDatos.SinCajaAbierta)
        val ahora = reloj.ahora()
        val resumen = dao.resumenVentas(caja.id)
        val porForma = dao.totalesPorFormaPago(caja.id)
        val ventasEfectivo = porForma.filter { it.esEfectivo }.sumOf { it.totalCentavos }
        // Fiado (v7): las ventas fiadas no entran por forma de pago; sí los abonos y las devoluciones.
        val fiado = fiadoDao.fiadoDeCaja(caja.id)
        val abonos = fiadoDao.abonosPorFormaDeCaja(caja.id)
        val devoluciones = fiadoDao.devolucionesDeCaja(caja.id)
        val esperado = ReglaFiado.efectivoEsperado(
            ConceptosCorte(
                fondoCentavos = caja.fondoCentavos,
                ventasEfectivoCentavos = ventasEfectivo,
                abonosEfectivoCentavos = abonos.filter { it.esEfectivo }.sumOf { it.totalCentavos },
                devolucionesCentavos = devoluciones,
            ),
        )

        val conCorte = caja.copy(
            corteCalculadoEn = ahora,
            corteVigente = true,
            totalVendidoCentavos = resumen.totalVendidoCentavos,
            numeroVentas = resumen.numeroVentas,
            descuentosCentavos = resumen.descuentosCentavos,
            efectivoEsperadoCentavos = esperado,
            efectivoContadoCentavos = efectivoContadoCentavos,
            diferenciaCentavos = ReglaCorte.diferencia(efectivoContadoCentavos, esperado),
            fiadoCentavos = fiado,
            abonosCentavos = abonos.sumOf { it.totalCentavos },
            devolucionesCentavos = devoluciones,
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
        CorteCaja(conCorte, filas, abonos)
    }

    /** RF 5.3: requiere corte vigente (sin ventas ni cancelaciones posteriores). */
    override suspend fun cerrar(): Resultado<Caja> = db.transaccion {
        val caja = dao.abierta() ?: fallar(FallaDatos.SinCajaAbierta)
        if (!caja.corteVigente) fallar(FallaDatos.CorteNoVigente)
        val ahora = reloj.ahora()
        val cerrada = caja.copy(estado = EstadoCaja.CERRADA, cerradaEn = ahora, actualizadoEn = ahora, pendienteSync = true)
        dao.actualizar(cerrada)
        cerrada
    }
}
