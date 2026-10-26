package mx.ambulantpoint.misventas.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Convenciones (CLAUDE.md): id UUID, fechas en epoch ms, dinero en centavos,
 * cantidades en milésimas y pendienteSync para el Módulo 7 (Sprint 2).
 */

/** RF 1.1 / 1.5 / 1.6. Un solo vendedor por dispositivo. */
@Entity(tableName = "perfil")
data class Perfil(
    @PrimaryKey val id: String,
    val nombres: String,
    val apellidoPaterno: String,
    val apellidoMaterno: String,
    val telefono: String,
    val nombreNegocio: String,
    val correo: String,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/** RF 3.6: configuración del negocio (fila única). */
@Entity(tableName = "configuracion")
data class Configuracion(
    @PrimaryKey val id: String = ID_UNICA,
    val descuentosHabilitados: Boolean = false,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
) {
    companion object { const val ID_UNICA = "configuracion" }
}

/** RF 2.6 – 2.9. `nombreNormalizado` único: no se repite sin distinguir mayúsculas/espacios. */
@Entity(
    tableName = "categoria",
    indices = [Index(value = ["nombreNormalizado"], unique = true)],
)
data class Categoria(
    @PrimaryKey val id: String,
    val nombre: String,
    val nombreNormalizado: String,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/** RF 2.1 – 2.5. Nunca se borra: `activo` = false es "Inactivo". */
@Entity(
    tableName = "producto",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["nombreNormalizado"], unique = true),
        Index(value = ["categoriaId"]),
    ],
)
data class Producto(
    @PrimaryKey val id: String,
    val nombre: String,
    val nombreNormalizado: String,
    val categoriaId: String,
    val tipoVenta: TipoVenta,
    /** Precio por pieza o por kg, en centavos. */
    val precioCentavos: Long,
    /** Existencia en milésimas (pzas × 1000 o gramos). */
    val existenciaMilesimas: Long,
    /** Ruta local de la imagen ya comprimida. */
    val imagenRuta: String,
    val activo: Boolean = true,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/** RF 3.5. "Efectivo" es predeterminada: no se desactiva ni se elimina. */
@Entity(
    tableName = "forma_pago",
    indices = [Index(value = ["nombreNormalizado"], unique = true)],
)
data class FormaPago(
    @PrimaryKey val id: String,
    val nombre: String,
    val nombreNormalizado: String,
    val activa: Boolean = true,
    /** true solo para "Efectivo": captura monto recibido y calcula cambio. */
    val esEfectivo: Boolean = false,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/**
 * RF 5.1 – 5.5. El corte se guarda en la misma caja. `corteVigente` pasa a false cuando se
 * registra o cancela una venta después del corte (RF 5.3: hay que recalcularlo antes de cerrar).
 */
@Entity(
    tableName = "caja",
    indices = [Index(value = ["estado"]), Index(value = ["cerradaEn"])],
)
data class Caja(
    @PrimaryKey val id: String,
    val estado: EstadoCaja,
    val fondoCentavos: Long,
    val abiertaEn: Long,
    val cerradaEn: Long? = null,
    val corteCalculadoEn: Long? = null,
    val corteVigente: Boolean = false,
    val totalVendidoCentavos: Long? = null,
    val numeroVentas: Int? = null,
    val descuentosCentavos: Long? = null,
    val efectivoEsperadoCentavos: Long? = null,
    val efectivoContadoCentavos: Long? = null,
    /** contado − esperado: positivo = sobrante, negativo = faltante. */
    val diferenciaCentavos: Long? = null,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/** RF 5.2 / 5.5: total por forma de pago guardado con el corte. */
@Entity(
    tableName = "corte_forma_pago",
    primaryKeys = ["cajaId", "formaPagoId"],
    foreignKeys = [
        ForeignKey(entity = Caja::class, parentColumns = ["id"], childColumns = ["cajaId"]),
        ForeignKey(entity = FormaPago::class, parentColumns = ["id"], childColumns = ["formaPagoId"]),
    ],
    indices = [Index(value = ["formaPagoId"])],
)
data class CorteFormaPago(
    val cajaId: String,
    val formaPagoId: String,
    val nombreFormaPago: String,
    val numeroVentas: Int,
    val totalCentavos: Long,
    val creadoEn: Long,
    val pendienteSync: Boolean = true,
)

/** RF 3.1 – 3.4. Guarda fecha y hora, descuento, total, forma de pago y estado. */
@Entity(
    tableName = "venta",
    foreignKeys = [
        ForeignKey(entity = Caja::class, parentColumns = ["id"], childColumns = ["cajaId"]),
        ForeignKey(entity = FormaPago::class, parentColumns = ["id"], childColumns = ["formaPagoId"]),
    ],
    indices = [Index(value = ["cajaId"]), Index(value = ["formaPagoId"]), Index(value = ["fechaHora"])],
)
data class Venta(
    @PrimaryKey val id: String,
    val cajaId: String,
    val fechaHora: Long,
    val subtotalCentavos: Long,
    val tipoDescuento: TipoDescuento? = null,
    /** Valor capturado: porcentaje (en centésimas, 1250 = 12.50 %) o monto fijo en centavos. */
    val valorDescuento: Long? = null,
    val descuentoCentavos: Long = 0,
    val totalCentavos: Long,
    val formaPagoId: String,
    val montoRecibidoCentavos: Long? = null,
    val cambioCentavos: Long? = null,
    val estado: EstadoVenta = EstadoVenta.REGISTRADA,
    val canceladaEn: Long? = null,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/** Línea de venta. Conserva nombre y precio del momento (RF 2.2: el cambio de precio no la afecta). */
@Entity(
    tableName = "venta_detalle",
    foreignKeys = [
        ForeignKey(entity = Venta::class, parentColumns = ["id"], childColumns = ["ventaId"]),
        ForeignKey(entity = Producto::class, parentColumns = ["id"], childColumns = ["productoId"]),
    ],
    indices = [Index(value = ["ventaId"]), Index(value = ["productoId"])],
)
data class VentaDetalle(
    @PrimaryKey val id: String,
    val ventaId: String,
    val productoId: String,
    val nombreProducto: String,
    val tipoVenta: TipoVenta,
    val cantidadMilesimas: Long,
    val precioUnitarioCentavos: Long,
    val importeCentavos: Long,
    val creadoEn: Long,
    val pendienteSync: Boolean = true,
)

/** RF 4.1 – 4.4. No se edita ni se elimina. Cantidad con signo (+ entra, − sale). */
@Entity(
    tableName = "movimiento_inventario",
    foreignKeys = [
        ForeignKey(entity = Producto::class, parentColumns = ["id"], childColumns = ["productoId"]),
    ],
    indices = [Index(value = ["productoId", "fechaHora"]), Index(value = ["ventaId"])],
)
data class MovimientoInventario(
    @PrimaryKey val id: String,
    val productoId: String,
    val fechaHora: Long,
    val tipo: TipoMovimiento,
    val cantidadMilesimas: Long,
    val existenciaResultanteMilesimas: Long,
    val motivo: MotivoAjuste? = null,
    val especificacionMotivo: String? = null,
    val ventaId: String? = null,
    val creadoEn: Long,
    val pendienteSync: Boolean = true,
)
