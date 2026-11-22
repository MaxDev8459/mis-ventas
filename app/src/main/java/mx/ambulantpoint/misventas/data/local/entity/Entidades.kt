package mx.ambulantpoint.misventas.data.local.entity

import androidx.room.ColumnInfo
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
    /** SC-01: datos para recibir transferencias (null = no capturados). */
    val clabe: String? = null,
    val beneficiario: String? = null,
    val banco: String? = null,
    /** Carga del día (v6): paso de + / − y de "N de cada producto" en productos por kilo (100 g por defecto). */
    @ColumnInfo(defaultValue = "100") val pasoKiloMilesimas: Long = PASO_KILO_DEFECTO,
    /** Fiado (v7): si el saldo de un cliente pasa este monto, se avisa al fiarle (no bloquea). */
    @ColumnInfo(defaultValue = "15000") val avisoSaldoCentavos: Long = AVISO_SALDO_DEFECTO,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
) {
    companion object {
        const val ID_UNICA = "configuracion"
        const val PASO_KILO_DEFECTO = 100L
        const val AVISO_SALDO_DEFECTO = 15_000L
    }
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
    /** Desarrollo independiente (v4): sale primero en Vender y en el chip "Favoritos". */
    @ColumnInfo(defaultValue = "0") val favorito: Boolean = false,
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
    /** Fiado (v7), guardado con el corte: total de las ventas fiadas de la caja. */
    val fiadoCentavos: Long? = null,
    /** Fiado (v7), guardado con el corte: abonos recibidos (todas las formas de pago). */
    val abonosCentavos: Long? = null,
    /** Fiado (v7), guardado con el corte: devoluciones en efectivo por cancelar ventas fiadas. */
    val devolucionesCentavos: Long? = null,
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

/**
 * Carga del día (desarrollo independiente, v5): cuánto de cada producto lleva el vendedor al abrir
 * caja. No mueve la existencia total (la casa + lo cargado); solo sirve para "te quedan X" y el
 * sobrante al cerrar. Una fila por producto cargado en la caja; "agregar a la carga" la incrementa.
 */
@Entity(
    tableName = "carga_caja",
    primaryKeys = ["cajaId", "productoId"],
    foreignKeys = [
        ForeignKey(entity = Caja::class, parentColumns = ["id"], childColumns = ["cajaId"]),
        ForeignKey(entity = Producto::class, parentColumns = ["id"], childColumns = ["productoId"]),
    ],
    indices = [Index(value = ["productoId"])],
)
data class CargaCaja(
    val cajaId: String,
    val productoId: String,
    val cantidadMilesimas: Long,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/**
 * Precarga (v6): carga ya definida con nombre para aplicarla al capturar la carga del día. Las
 * cantidades van codificadas ("productoId=milésimas;…", ver ReglaPrecarga). No se borra: `activa`.
 */
@Entity(tableName = "precarga", indices = [Index(value = ["nombreNormalizado"], unique = true)])
data class Precarga(
    @PrimaryKey val id: String,
    val nombre: String,
    val nombreNormalizado: String,
    val cantidades: String,
    val vecesUsada: Int = 0,
    val ultimoUso: Long? = null,
    val activa: Boolean = true,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/**
 * Fiado (v7): cliente al que se le fía. Nunca se borra; el nombre no se repite (sin distinguir
 * mayúsculas, acentos ni espacios). El saldo no se guarda: se calcula de sus ventas y abonos.
 */
@Entity(tableName = "cliente", indices = [Index(value = ["nombreNormalizado"], unique = true)])
data class Cliente(
    @PrimaryKey val id: String,
    val nombre: String,
    val nombreNormalizado: String,
    val telefono: String? = null,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/**
 * RF 3.1 – 3.4. Guarda fecha y hora, descuento, total, forma de pago y estado.
 * Fiado (v7): una venta fiada no tiene forma de pago (`formaPagoId` null) y sí `clienteId`.
 */
@Entity(
    tableName = "venta",
    foreignKeys = [
        ForeignKey(entity = Caja::class, parentColumns = ["id"], childColumns = ["cajaId"]),
        ForeignKey(entity = FormaPago::class, parentColumns = ["id"], childColumns = ["formaPagoId"]),
        ForeignKey(entity = Cliente::class, parentColumns = ["id"], childColumns = ["clienteId"]),
    ],
    indices = [
        Index(value = ["cajaId"]), Index(value = ["formaPagoId"]), Index(value = ["fechaHora"]), Index(value = ["clienteId"]),
    ],
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
    /** null = venta fiada. */
    val formaPagoId: String?,
    val montoRecibidoCentavos: Long? = null,
    val cambioCentavos: Long? = null,
    val estado: EstadoVenta = EstadoVenta.REGISTRADA,
    val canceladaEn: Long? = null,
    /** Fiado (v7): cliente al que se le fió; null = venta cobrada. */
    val clienteId: String? = null,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/**
 * Línea de venta. Conserva nombre y precio del momento (RF 2.2: el cambio de precio no la afecta).
 * Venta rápida (v7): `productoId` null = monto libre ("Varios"); el nombre guardado es su descripción.
 */
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
    val productoId: String?,
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
    /** Reportes (v9): precio de venta del producto al registrar un ajuste (valor de mermas). Null antes de la 1.6.0. */
    val precioUnitarioCentavos: Long? = null,
    val creadoEn: Long,
    val pendienteSync: Boolean = true,
)

/**
 * Fiado (v7): dinero que entra (ABONO) o sale (DEVOLUCION) por el fiado de un cliente, en una caja.
 * Entra al corte de la caja en que se registra. La devolución siempre es en efectivo y lleva la venta
 * cancelada (`ventaId`). Un abono equivocado se marca CANCELADO (no se borra).
 */
@Entity(
    tableName = "abono",
    foreignKeys = [
        ForeignKey(entity = Cliente::class, parentColumns = ["id"], childColumns = ["clienteId"]),
        ForeignKey(entity = Caja::class, parentColumns = ["id"], childColumns = ["cajaId"]),
        ForeignKey(entity = FormaPago::class, parentColumns = ["id"], childColumns = ["formaPagoId"]),
        ForeignKey(entity = Venta::class, parentColumns = ["id"], childColumns = ["ventaId"]),
    ],
    indices = [Index(value = ["clienteId"]), Index(value = ["cajaId"]), Index(value = ["formaPagoId"]), Index(value = ["ventaId"])],
)
data class Abono(
    @PrimaryKey val id: String,
    val clienteId: String,
    val cajaId: String,
    val fechaHora: Long,
    val tipo: TipoAbono,
    /** Siempre positivo; la devolución resta por su tipo. */
    val montoCentavos: Long,
    val formaPagoId: String,
    /** Solo en devoluciones: la venta fiada que se canceló. */
    val ventaId: String? = null,
    val estado: EstadoAbono = EstadoAbono.REGISTRADO,
    val canceladoEn: Long? = null,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/** Fiado (v7): cuánto de un abono se aplicó a cada venta fiada (de la más vieja a la más nueva). No cambia. */
@Entity(
    tableName = "aplicacion_abono",
    foreignKeys = [
        ForeignKey(entity = Abono::class, parentColumns = ["id"], childColumns = ["abonoId"]),
        ForeignKey(entity = Venta::class, parentColumns = ["id"], childColumns = ["ventaId"]),
    ],
    indices = [Index(value = ["abonoId"]), Index(value = ["ventaId"])],
)
data class AplicacionAbono(
    @PrimaryKey val id: String,
    val abonoId: String,
    val ventaId: String,
    val montoCentavos: Long,
    val creadoEn: Long,
    val pendienteSync: Boolean = true,
)

/**
 * Apartados (v8, `docs/10-ESPEC-APARTADOS.md`): pedido para entregar después. No mueve dinero ni la existencia
 * total; mientras está PENDIENTE su producto queda separado (no se puede vender). El precio queda fijo al apartar.
 */
@Entity(tableName = "apartado", indices = [Index(value = ["estado", "entregaEn"])])
data class Apartado(
    @PrimaryKey val id: String,
    val nombre: String,
    val telefono: String? = null,
    val lugar: String,
    /** Fecha y hora de entrega (epoch ms). */
    val entregaEn: Long,
    val totalCentavos: Long,
    val estado: EstadoApartado = EstadoApartado.PENDIENTE,
    /** Venta con la que se entregó. */
    val ventaId: String? = null,
    /** Cuándo se entregó, canceló o liberó. */
    val terminadoEn: Long? = null,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
)

/** Producto de un apartado con su precio fijo (el del día en que se apartó). No cambia. */
@Entity(
    tableName = "apartado_detalle",
    foreignKeys = [
        ForeignKey(entity = Apartado::class, parentColumns = ["id"], childColumns = ["apartadoId"]),
        ForeignKey(entity = Producto::class, parentColumns = ["id"], childColumns = ["productoId"]),
    ],
    indices = [Index(value = ["apartadoId"]), Index(value = ["productoId"])],
)
data class ApartadoDetalle(
    @PrimaryKey val id: String,
    val apartadoId: String,
    val productoId: String,
    val nombreProducto: String,
    val tipoVenta: TipoVenta,
    val cantidadMilesimas: Long,
    val precioUnitarioCentavos: Long,
    val importeCentavos: Long,
    val creadoEn: Long,
    val pendienteSync: Boolean = true,
)

/**
 * RF 7.1 + RF 2.9: una categoría eliminada que ya estaba en la nube se borra también allá en la
 * siguiente sincronización. Cuenta como registro pendiente hasta entonces.
 */
@Entity(tableName = "eliminacion_pendiente", primaryKeys = ["coleccion", "docId"])
data class EliminacionPendiente(
    val coleccion: String,
    val docId: String,
    val creadoEn: Long,
)
