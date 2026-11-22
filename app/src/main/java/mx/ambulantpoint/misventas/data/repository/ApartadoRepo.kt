package mx.ambulantpoint.misventas.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.ambulantpoint.misventas.core.Dinero
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.dao.ApartadoConDetalles
import mx.ambulantpoint.misventas.data.local.entity.Apartado
import mx.ambulantpoint.misventas.data.local.entity.ApartadoDetalle
import mx.ambulantpoint.misventas.data.local.entity.EstadoApartado
import mx.ambulantpoint.misventas.domain.regla.ReglaApartado
import mx.ambulantpoint.misventas.domain.regla.ReglaFiado
import java.time.ZoneId

/** Datos de entrega de un apartado (nuevo o al editar). */
data class DatosApartado(
    val nombre: String,
    val telefono: String,
    val lugar: String,
    val entregaEn: Long,
)

/** Apartados (Fase 4, `docs/10-ESPEC-APARTADOS.md`). La entrega se cobra con VentaRepo (SolicitudCobro.apartadoId). */
class ApartadoRepo(
    private val db: MisVentasDb,
    private val reloj: Reloj,
    private val zona: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val dao = db.apartadoDao()

    fun observarPendientes(): Flow<List<ApartadoConDetalles>> = dao.observarPendientes()
    fun observarHistorial(): Flow<List<ApartadoConDetalles>> = dao.observarHistorial()
    fun observar(id: String): Flow<ApartadoConDetalles?> = dao.observar(id)
    suspend fun pendientes(): List<ApartadoConDetalles> = dao.pendientes()

    /** productoId → lo apartado (pendiente). */
    fun observarApartadoPorProducto(): Flow<Map<String, Long>> =
        dao.observarApartadoPorProducto().map { l -> l.associate { it.productoId to it.cantidadMilesimas } }

    private fun validar(d: DatosApartado): DatosApartado {
        val nombre = d.nombre.trim().replace(Regex("\\s+"), " ")
        ReglaFiado.validarNombre(nombre)?.let { fallar(FallaDatos.Regla(it)) }
        ReglaFiado.validarTelefono(d.telefono)?.let { fallar(FallaDatos.Regla(it)) }
        ReglaApartado.validarLugar(d.lugar)?.let { fallar(FallaDatos.Regla(it)) }
        ReglaApartado.validarEntrega(d.entregaEn, reloj.ahora())?.let { fallar(FallaDatos.Regla(it)) }
        return d.copy(nombre = nombre, telefono = d.telefono.trim(), lugar = d.lugar.trim().replace(Regex("\\s+"), " "))
    }

    /**
     * Separa los productos con su precio de hoy (queda fijo). No mueve la existencia total ni dinero;
     * no requiere caja. Solo se puede apartar lo disponible (total − lo ya apartado).
     */
    suspend fun apartar(datos: DatosApartado, lineas: List<LineaVenta>): Resultado<Apartado> = db.transaccion {
        if (lineas.isEmpty()) fallar(FallaDatos.VentaSinProductos)
        val d = validar(datos)
        val ahora = reloj.ahora()
        val id = nuevoId()
        val cantidades = lineas.groupBy { it.productoId }.mapValues { (_, l) -> l.sumOf { it.cantidadMilesimas } }
        val detalles = cantidades.map { (productoId, cantidad) ->
            val p = db.productoDao().porId(productoId) ?: fallar(FallaDatos.NoEncontrado)
            if (!p.activo) fallar(FallaDatos.ProductoNoDisponible(productoId))
            if (cantidad <= 0 || !cantidadValida(p.tipoVenta, cantidad)) fallar(FallaDatos.CantidadInvalida)
            val disponible = ReglaApartado.disponible(p.existenciaMilesimas, dao.apartadoDe(productoId))
            if (cantidad > disponible) fallar(FallaDatos.ExistenciaInsuficiente(productoId, p.nombre, p.tipoVenta, disponible))
            ApartadoDetalle(
                id = nuevoId(), apartadoId = id, productoId = productoId, nombreProducto = p.nombre, tipoVenta = p.tipoVenta,
                cantidadMilesimas = cantidad, precioUnitarioCentavos = p.precioCentavos,
                importeCentavos = Dinero.importe(cantidad, p.precioCentavos), creadoEn = ahora,
            )
        }
        val apartado = Apartado(
            id = id, nombre = d.nombre, telefono = d.telefono.ifEmpty { null }, lugar = d.lugar, entregaEn = d.entregaEn,
            totalCentavos = detalles.sumOf { it.importeCentavos }, creadoEn = ahora, actualizadoEn = ahora,
        )
        dao.insertar(apartado)
        dao.insertarDetalles(detalles)
        apartado
    }

    /** Cambia nombre, teléfono, lugar, fecha y hora (solo pendientes). */
    suspend fun editar(id: String, datos: DatosApartado): Resultado<Apartado> = db.transaccion {
        val actual = dao.porId(id) ?: fallar(FallaDatos.NoEncontrado)
        if (actual.estado != EstadoApartado.PENDIENTE) fallar(FallaDatos.Regla(Mensajes.Apartado.NO_PENDIENTE))
        val d = validar(datos)
        actual.copy(
            nombre = d.nombre, telefono = d.telefono.ifEmpty { null }, lugar = d.lugar, entregaEn = d.entregaEn,
            actualizadoEn = reloj.ahora(), pendienteSync = true,
        ).also { dao.actualizar(it) }
    }

    /** Cancela a mano (no requiere caja): el producto vuelve a estar disponible. */
    suspend fun cancelar(id: String): Resultado<Unit> = db.transaccion {
        if (dao.terminar(id, EstadoApartado.CANCELADO.name, reloj.ahora()) == 0) {
            fallar(FallaDatos.Regla(Mensajes.Apartado.NO_PENDIENTE))
        }
    }

    /** Los que se liberarían al cerrar la caja abierta (para avisar antes de confirmar). */
    suspend fun porLiberar(): List<Apartado> {
        val caja = db.cajaDao().abierta() ?: return emptyList()
        return dao.pendientesAntesDe(ReglaApartado.limiteLiberacion(caja.abiertaEn, zona()))
    }
}

/**
 * Al cerrar la caja (dentro de su transacción): pendientes con entrega ese día o antes pasan a LIBERADO.
 * "Ese día" = el día en que se abrió la caja (respuesta 5 del cliente).
 */
internal suspend fun liberarApartados(db: MisVentasDb, abiertaEn: Long, ahora: Long, zona: ZoneId): Int {
    val dao = db.apartadoDao()
    return dao.pendientesAntesDe(ReglaApartado.limiteLiberacion(abiertaEn, zona))
        .sumOf { dao.terminar(it.id, EstadoApartado.LIBERADO.name, ahora) }
}
