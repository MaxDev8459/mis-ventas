package mx.ambulantpoint.misventas.data.sync

import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.data.local.entity.Configuracion
import mx.ambulantpoint.misventas.data.local.entity.CorteFormaPago
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.local.entity.MovimientoInventario
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.Venta
import mx.ambulantpoint.misventas.data.local.entity.VentaDetalle

/**
 * Nombres de las colecciones en Firestore: `usuarios/{uid}/{coleccion}/{id}`. Son los mismos que
 * las tablas de Room para que la trazabilidad sea directa.
 */
object Colecciones {
    const val PERFIL = "perfil"
    const val CONFIGURACION = "configuracion"
    const val CATEGORIA = "categoria"
    const val PRODUCTO = "producto"
    const val FORMA_PAGO = "forma_pago"
    const val CAJA = "caja"
    const val CORTE_FORMA_PAGO = "corte_forma_pago"
    const val VENTA = "venta"
    const val VENTA_DETALLE = "venta_detalle"
    const val MOVIMIENTO = "movimiento_inventario"
}

/*
 * Entidad → documento de Firestore. Mapeo escrito a mano (sin reflexión) para que no lo rompa la
 * minificación del APK de entrega. Dinero en centavos y cantidades en milésimas, igual que en Room;
 * enums como texto. No se sube `pendienteSync` (es estado local del teléfono).
 */

fun Perfil.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "nombres" to nombres, "apellidoPaterno" to apellidoPaterno, "apellidoMaterno" to apellidoMaterno,
    "telefono" to telefono, "nombreNegocio" to nombreNegocio, "correo" to correo,
    "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

fun Configuracion.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "descuentosHabilitados" to descuentosHabilitados, "actualizadoEn" to actualizadoEn,
)

fun Categoria.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "nombre" to nombre, "nombreNormalizado" to nombreNormalizado,
    "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

/** La imagen se respalda aparte (Storage); aquí solo los datos del producto. */
fun Producto.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "nombre" to nombre, "nombreNormalizado" to nombreNormalizado, "categoriaId" to categoriaId,
    "tipoVenta" to tipoVenta.name, "precioCentavos" to precioCentavos, "existenciaMilesimas" to existenciaMilesimas,
    "activo" to activo, "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

fun FormaPago.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "nombre" to nombre, "nombreNormalizado" to nombreNormalizado, "activa" to activa,
    "esEfectivo" to esEfectivo, "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

fun Caja.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "estado" to estado.name, "fondoCentavos" to fondoCentavos, "abiertaEn" to abiertaEn,
    "cerradaEn" to cerradaEn, "corteCalculadoEn" to corteCalculadoEn, "corteVigente" to corteVigente,
    "totalVendidoCentavos" to totalVendidoCentavos, "numeroVentas" to numeroVentas,
    "descuentosCentavos" to descuentosCentavos, "efectivoEsperadoCentavos" to efectivoEsperadoCentavos,
    "efectivoContadoCentavos" to efectivoContadoCentavos, "diferenciaCentavos" to diferenciaCentavos,
    "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

/** Llave compuesta en Room (cajaId, formaPagoId) → un solo id de documento. */
fun CorteFormaPago.idDocumento(): String = "${cajaId}_$formaPagoId"

fun CorteFormaPago.aDocumento(): Map<String, Any?> = mapOf(
    "cajaId" to cajaId, "formaPagoId" to formaPagoId, "nombreFormaPago" to nombreFormaPago,
    "numeroVentas" to numeroVentas, "totalCentavos" to totalCentavos, "creadoEn" to creadoEn,
)

fun Venta.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "cajaId" to cajaId, "fechaHora" to fechaHora, "subtotalCentavos" to subtotalCentavos,
    "tipoDescuento" to tipoDescuento?.name, "valorDescuento" to valorDescuento, "descuentoCentavos" to descuentoCentavos,
    "totalCentavos" to totalCentavos, "formaPagoId" to formaPagoId, "montoRecibidoCentavos" to montoRecibidoCentavos,
    "cambioCentavos" to cambioCentavos, "estado" to estado.name, "canceladaEn" to canceladaEn,
    "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

fun VentaDetalle.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "ventaId" to ventaId, "productoId" to productoId, "nombreProducto" to nombreProducto,
    "tipoVenta" to tipoVenta.name, "cantidadMilesimas" to cantidadMilesimas,
    "precioUnitarioCentavos" to precioUnitarioCentavos, "importeCentavos" to importeCentavos, "creadoEn" to creadoEn,
)

fun MovimientoInventario.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "productoId" to productoId, "fechaHora" to fechaHora, "tipo" to tipo.name,
    "cantidadMilesimas" to cantidadMilesimas, "existenciaResultanteMilesimas" to existenciaResultanteMilesimas,
    "motivo" to motivo?.name, "especificacionMotivo" to especificacionMotivo, "ventaId" to ventaId, "creadoEn" to creadoEn,
)
