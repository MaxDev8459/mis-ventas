package mx.ambulantpoint.misventas.data.sync

import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.data.local.entity.Configuracion
import mx.ambulantpoint.misventas.data.local.entity.CorteFormaPago
import mx.ambulantpoint.misventas.data.local.entity.CargaCaja
import mx.ambulantpoint.misventas.data.local.entity.Precarga
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.local.entity.MovimientoInventario
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.Venta
import mx.ambulantpoint.misventas.data.local.entity.VentaDetalle
import mx.ambulantpoint.misventas.data.local.entity.EstadoCaja
import mx.ambulantpoint.misventas.data.local.entity.EstadoVenta
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.TipoDescuento
import mx.ambulantpoint.misventas.data.local.entity.TipoMovimiento
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.local.entity.Cliente
import mx.ambulantpoint.misventas.data.local.entity.Abono
import mx.ambulantpoint.misventas.data.local.entity.AplicacionAbono
import mx.ambulantpoint.misventas.data.local.entity.TipoAbono
import mx.ambulantpoint.misventas.data.local.entity.EstadoAbono
import mx.ambulantpoint.misventas.data.local.entity.Apartado
import mx.ambulantpoint.misventas.data.local.entity.ApartadoDetalle
import mx.ambulantpoint.misventas.data.local.entity.EstadoApartado

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
    const val CARGA = "carga_caja"
    const val PRECARGA = "precarga"
    const val CLIENTE = "cliente"
    const val ABONO = "abono"
    const val APLICACION_ABONO = "aplicacion_abono"
    const val APARTADO = "apartado"
    const val APARTADO_DETALLE = "apartado_detalle"
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
    "id" to id, "descuentosHabilitados" to descuentosHabilitados,
    "clabe" to clabe, "beneficiario" to beneficiario, "banco" to banco, "pasoKiloMilesimas" to pasoKiloMilesimas,
    "avisoSaldoCentavos" to avisoSaldoCentavos, "actualizadoEn" to actualizadoEn,
)

fun Categoria.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "nombre" to nombre, "nombreNormalizado" to nombreNormalizado,
    "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

/** La imagen se respalda aparte (Storage); aquí solo los datos del producto. */
fun Producto.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "nombre" to nombre, "nombreNormalizado" to nombreNormalizado, "categoriaId" to categoriaId,
    "tipoVenta" to tipoVenta.name, "precioCentavos" to precioCentavos, "existenciaMilesimas" to existenciaMilesimas,
    "activo" to activo, "favorito" to favorito, "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
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
    "fiadoCentavos" to fiadoCentavos, "abonosCentavos" to abonosCentavos, "devolucionesCentavos" to devolucionesCentavos,
    "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

fun Precarga.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "nombre" to nombre, "nombreNormalizado" to nombreNormalizado, "cantidades" to cantidades,
    "vecesUsada" to vecesUsada, "ultimoUso" to ultimoUso, "activa" to activa, "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

fun aPrecarga(d: Map<String, Any?>) = Precarga(
    id = d.texto("id"), nombre = d.texto("nombre"), nombreNormalizado = d.texto("nombreNormalizado"),
    cantidades = d.texto("cantidades"), vecesUsada = d.entero("vecesUsada").toInt(), ultimoUso = d.enteroONulo("ultimoUso"),
    activa = d.logico("activa", true), creadoEn = d.entero("creadoEn"), actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
)

/** Carga del día: llave compuesta (cajaId, productoId) → un solo id de documento. */
fun CargaCaja.idDocumento(): String = "${cajaId}_$productoId"

fun CargaCaja.aDocumento(): Map<String, Any?> = mapOf(
    "cajaId" to cajaId, "productoId" to productoId, "cantidadMilesimas" to cantidadMilesimas,
    "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

fun aCargaCaja(d: Map<String, Any?>) = CargaCaja(
    cajaId = d.texto("cajaId"), productoId = d.texto("productoId"), cantidadMilesimas = d.entero("cantidadMilesimas"),
    creadoEn = d.entero("creadoEn"), actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
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
    "clienteId" to clienteId, "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

fun Cliente.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "nombre" to nombre, "nombreNormalizado" to nombreNormalizado, "telefono" to telefono,
    "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

fun Abono.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "clienteId" to clienteId, "cajaId" to cajaId, "fechaHora" to fechaHora, "tipo" to tipo.name,
    "montoCentavos" to montoCentavos, "formaPagoId" to formaPagoId, "ventaId" to ventaId, "estado" to estado.name,
    "canceladoEn" to canceladoEn, "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

fun AplicacionAbono.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "abonoId" to abonoId, "ventaId" to ventaId, "montoCentavos" to montoCentavos, "creadoEn" to creadoEn,
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

/*
 * RF 7.4 — documento de Firestore → entidad de Room. Lo recuperado ya está en línea, así que llega
 * con `pendienteSync = false`. Firestore devuelve los enteros como Long; los campos que faltan
 * toman el valor por defecto de la entidad.
 */

private fun Map<String, Any?>.texto(c: String): String = this[c] as? String ?: ""
private fun Map<String, Any?>.textoONulo(c: String): String? = this[c] as? String
private fun Map<String, Any?>.entero(c: String): Long = (this[c] as? Number)?.toLong() ?: 0L
private fun Map<String, Any?>.enteroONulo(c: String): Long? = (this[c] as? Number)?.toLong()
private fun Map<String, Any?>.logico(c: String, porDefecto: Boolean): Boolean = this[c] as? Boolean ?: porDefecto
private inline fun <reified E : Enum<E>> Map<String, Any?>.enumONulo(c: String): E? =
    textoONulo(c)?.let { n -> enumValues<E>().firstOrNull { it.name == n } }
private inline fun <reified E : Enum<E>> Map<String, Any?>.enumO(c: String, porDefecto: E): E = enumONulo<E>(c) ?: porDefecto

/** Campo del documento de producto con el nombre del archivo de su imagen en Storage. */
const val CAMPO_IMAGEN = "imagen"

fun aPerfil(d: Map<String, Any?>) = Perfil(
    id = d.texto("id"), nombres = d.texto("nombres"), apellidoPaterno = d.texto("apellidoPaterno"),
    apellidoMaterno = d.texto("apellidoMaterno"), telefono = d.texto("telefono"), nombreNegocio = d.texto("nombreNegocio"),
    correo = d.texto("correo"), creadoEn = d.entero("creadoEn"), actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
)

fun aConfiguracion(d: Map<String, Any?>) = Configuracion(
    id = d.textoONulo("id") ?: Configuracion.ID_UNICA, descuentosHabilitados = d.logico("descuentosHabilitados", false),
    clabe = d.textoONulo("clabe"), beneficiario = d.textoONulo("beneficiario"), banco = d.textoONulo("banco"),
    // Respaldos anteriores a la v6 no lo tienen: 100 g.
    pasoKiloMilesimas = d.enteroONulo("pasoKiloMilesimas") ?: Configuracion.PASO_KILO_DEFECTO,
    // Respaldos anteriores a la v7 no lo tienen: $150.
    avisoSaldoCentavos = d.enteroONulo("avisoSaldoCentavos") ?: Configuracion.AVISO_SALDO_DEFECTO,
    actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
)

fun aCategoria(d: Map<String, Any?>) = Categoria(
    id = d.texto("id"), nombre = d.texto("nombre"), nombreNormalizado = d.texto("nombreNormalizado"),
    creadoEn = d.entero("creadoEn"), actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
)

/** `imagenRuta` = ruta local donde quedó la imagen descargada ("" si no tiene). */
fun aProducto(d: Map<String, Any?>, imagenRuta: String) = Producto(
    id = d.texto("id"), nombre = d.texto("nombre"), nombreNormalizado = d.texto("nombreNormalizado"),
    categoriaId = d.texto("categoriaId"), tipoVenta = d.enumO("tipoVenta", TipoVenta.UNIDAD),
    precioCentavos = d.entero("precioCentavos"), existenciaMilesimas = d.entero("existenciaMilesimas"),
    imagenRuta = imagenRuta, activo = d.logico("activo", true),
    // Respaldos anteriores a la v4 no tienen el campo: se leen como no favorito.
    favorito = d.logico("favorito", false),
    creadoEn = d.entero("creadoEn"), actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
)

fun aFormaPago(d: Map<String, Any?>) = FormaPago(
    id = d.texto("id"), nombre = d.texto("nombre"), nombreNormalizado = d.texto("nombreNormalizado"),
    activa = d.logico("activa", true), esEfectivo = d.logico("esEfectivo", false),
    creadoEn = d.entero("creadoEn"), actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
)

fun aCaja(d: Map<String, Any?>) = Caja(
    id = d.texto("id"), estado = d.enumO("estado", EstadoCaja.CERRADA), fondoCentavos = d.entero("fondoCentavos"),
    abiertaEn = d.entero("abiertaEn"), cerradaEn = d.enteroONulo("cerradaEn"), corteCalculadoEn = d.enteroONulo("corteCalculadoEn"),
    corteVigente = d.logico("corteVigente", false), totalVendidoCentavos = d.enteroONulo("totalVendidoCentavos"),
    numeroVentas = d.enteroONulo("numeroVentas")?.toInt(), descuentosCentavos = d.enteroONulo("descuentosCentavos"),
    efectivoEsperadoCentavos = d.enteroONulo("efectivoEsperadoCentavos"), efectivoContadoCentavos = d.enteroONulo("efectivoContadoCentavos"),
    diferenciaCentavos = d.enteroONulo("diferenciaCentavos"),
    fiadoCentavos = d.enteroONulo("fiadoCentavos"), abonosCentavos = d.enteroONulo("abonosCentavos"),
    devolucionesCentavos = d.enteroONulo("devolucionesCentavos"),
    creadoEn = d.entero("creadoEn"), actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
)

fun aCorteFormaPago(d: Map<String, Any?>) = CorteFormaPago(
    cajaId = d.texto("cajaId"), formaPagoId = d.texto("formaPagoId"), nombreFormaPago = d.texto("nombreFormaPago"),
    numeroVentas = d.entero("numeroVentas").toInt(), totalCentavos = d.entero("totalCentavos"),
    creadoEn = d.entero("creadoEn"), pendienteSync = false,
)

fun aVenta(d: Map<String, Any?>) = Venta(
    id = d.texto("id"), cajaId = d.texto("cajaId"), fechaHora = d.entero("fechaHora"), subtotalCentavos = d.entero("subtotalCentavos"),
    tipoDescuento = d.enumONulo<TipoDescuento>("tipoDescuento"), valorDescuento = d.enteroONulo("valorDescuento"),
    descuentoCentavos = d.entero("descuentoCentavos"), totalCentavos = d.entero("totalCentavos"),
    // Venta fiada (v7): sin forma de pago. Respaldos anteriores siempre la traen.
    formaPagoId = d.textoONulo("formaPagoId"), montoRecibidoCentavos = d.enteroONulo("montoRecibidoCentavos"),
    cambioCentavos = d.enteroONulo("cambioCentavos"), estado = d.enumO("estado", EstadoVenta.REGISTRADA),
    canceladaEn = d.enteroONulo("canceladaEn"), clienteId = d.textoONulo("clienteId"),
    creadoEn = d.entero("creadoEn"), actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
)

fun aVentaDetalle(d: Map<String, Any?>) = VentaDetalle(
    // Monto libre "Varios" (v7): sin producto.
    id = d.texto("id"), ventaId = d.texto("ventaId"), productoId = d.textoONulo("productoId"), nombreProducto = d.texto("nombreProducto"),
    tipoVenta = d.enumO("tipoVenta", TipoVenta.UNIDAD), cantidadMilesimas = d.entero("cantidadMilesimas"),
    precioUnitarioCentavos = d.entero("precioUnitarioCentavos"), importeCentavos = d.entero("importeCentavos"),
    creadoEn = d.entero("creadoEn"), pendienteSync = false,
)

fun aCliente(d: Map<String, Any?>) = Cliente(
    id = d.texto("id"), nombre = d.texto("nombre"), nombreNormalizado = d.texto("nombreNormalizado"),
    telefono = d.textoONulo("telefono"), creadoEn = d.entero("creadoEn"), actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
)

fun aAbono(d: Map<String, Any?>) = Abono(
    id = d.texto("id"), clienteId = d.texto("clienteId"), cajaId = d.texto("cajaId"), fechaHora = d.entero("fechaHora"),
    tipo = d.enumO("tipo", TipoAbono.ABONO), montoCentavos = d.entero("montoCentavos"), formaPagoId = d.texto("formaPagoId"),
    ventaId = d.textoONulo("ventaId"), estado = d.enumO("estado", EstadoAbono.REGISTRADO), canceladoEn = d.enteroONulo("canceladoEn"),
    creadoEn = d.entero("creadoEn"), actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
)

fun aAplicacionAbono(d: Map<String, Any?>) = AplicacionAbono(
    id = d.texto("id"), abonoId = d.texto("abonoId"), ventaId = d.texto("ventaId"), montoCentavos = d.entero("montoCentavos"),
    creadoEn = d.entero("creadoEn"), pendienteSync = false,
)

fun Apartado.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "nombre" to nombre, "telefono" to telefono, "lugar" to lugar, "entregaEn" to entregaEn,
    "totalCentavos" to totalCentavos, "estado" to estado.name, "ventaId" to ventaId, "terminadoEn" to terminadoEn,
    "creadoEn" to creadoEn, "actualizadoEn" to actualizadoEn,
)

fun ApartadoDetalle.aDocumento(): Map<String, Any?> = mapOf(
    "id" to id, "apartadoId" to apartadoId, "productoId" to productoId, "nombreProducto" to nombreProducto,
    "tipoVenta" to tipoVenta.name, "cantidadMilesimas" to cantidadMilesimas,
    "precioUnitarioCentavos" to precioUnitarioCentavos, "importeCentavos" to importeCentavos, "creadoEn" to creadoEn,
)

fun aApartado(d: Map<String, Any?>) = Apartado(
    id = d.texto("id"), nombre = d.texto("nombre"), telefono = d.textoONulo("telefono"), lugar = d.texto("lugar"),
    entregaEn = d.entero("entregaEn"), totalCentavos = d.entero("totalCentavos"),
    estado = d.enumO("estado", EstadoApartado.PENDIENTE), ventaId = d.textoONulo("ventaId"), terminadoEn = d.enteroONulo("terminadoEn"),
    creadoEn = d.entero("creadoEn"), actualizadoEn = d.entero("actualizadoEn"), pendienteSync = false,
)

fun aApartadoDetalle(d: Map<String, Any?>) = ApartadoDetalle(
    id = d.texto("id"), apartadoId = d.texto("apartadoId"), productoId = d.texto("productoId"), nombreProducto = d.texto("nombreProducto"),
    tipoVenta = d.enumO("tipoVenta", TipoVenta.UNIDAD), cantidadMilesimas = d.entero("cantidadMilesimas"),
    precioUnitarioCentavos = d.entero("precioUnitarioCentavos"), importeCentavos = d.entero("importeCentavos"),
    creadoEn = d.entero("creadoEn"), pendienteSync = false,
)

fun aMovimiento(d: Map<String, Any?>) = MovimientoInventario(
    id = d.texto("id"), productoId = d.texto("productoId"), fechaHora = d.entero("fechaHora"),
    tipo = d.enumO("tipo", TipoMovimiento.AJUSTE), cantidadMilesimas = d.entero("cantidadMilesimas"),
    existenciaResultanteMilesimas = d.entero("existenciaResultanteMilesimas"), motivo = d.enumONulo<MotivoAjuste>("motivo"),
    especificacionMotivo = d.textoONulo("especificacionMotivo"), ventaId = d.textoONulo("ventaId"),
    creadoEn = d.entero("creadoEn"), pendienteSync = false,
)
